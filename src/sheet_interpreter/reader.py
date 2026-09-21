# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Model inference and a file bridge to the platform-independent Java decoder."""
import gzip
import hashlib
import json
import os
import shutil
import struct
import subprocess
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

MODEL_SHA256 = "92ab7c34c27cb704d95f0cde247b3611d1563fdf1c114b9f9817abb18a7b6a93"
MODEL_NAME = "music_sheets_v4_float16.tflite"


def tile_starts(size):
    """Tail tiles retain the app's exact 320/192 ownership order."""
    if size < 1:
        raise ValueError("Image dimensions must be positive")
    last = max(0, size - 320)
    values = []
    for offset in range(0, size, 192):
        value = min(offset, last)
        if not values or values[-1] != value:
            values.append(value)
        if value == last:
            break
    return values


def grayscale(image, width=2048):
    if not 64 <= width <= 4096:
        raise ValueError("Analysis width must be 64..4096")
    image = ImageOps.exif_transpose(image).convert("RGBA")
    white = Image.new("RGBA", image.size, "white")
    white.alpha_composite(image)
    gray = white.convert("L")
    height = max(1, round(gray.height * width / gray.width))
    if width * height > 20_000_000:
        raise ValueError("Analysis image exceeds 20 million pixels")
    return np.asarray(gray.resize((width, height), Image.Resampling.BILINEAR), dtype=np.uint8)


def write_page(path, labels, gray, annotations=None):
    height, width = gray.shape
    if labels.shape != gray.shape or labels.dtype != np.uint8 or gray.dtype != np.uint8:
        raise ValueError("Expected same-size uint8 mask and grayscale arrays")
    if annotations is not None and not isinstance(annotations, dict):
        raise ValueError("Each page's annotations must be an object")
    with gzip.open(path, "wb") as stream:
        stream.write(struct.pack(">III", 0x52535031, width, height))
        stream.write(labels.tobytes())
        stream.write(gray.tobytes())
        if annotations is None:
            return
        stream.write(struct.pack(">I", 0x4F435231))
        for field in ("measureNumbers", "tempoNumbers", "restCounts"):
            rows = annotations.get(field, [])
            if len(rows) > 10000:
                raise ValueError("Too many OCR tokens")
            stream.write(struct.pack(">i", len(rows)))
            for row in rows:
                stream.write(struct.pack(">i5f", row["value"], row["left"], row["top"], row["right"],
                                         row["bottom"], row.get("annotationLeft", row["left"])))
        words = annotations.get("words", [])
        if len(words) > 10000:
            raise ValueError("Too many OCR words")
        stream.write(struct.pack(">i", len(words)))
        for word in words:
            text = word["text"].encode("utf-8")
            if len(text) > 10000:
                raise ValueError("OCR word too long")
            stream.write(struct.pack(">i", len(text)) + text)
            stream.write(struct.pack(">4f", word["left"], word["top"], word["right"], word["bottom"]))
        meters = annotations.get("meters", [])
        if len(meters) > 10000:
            raise ValueError("Too many meter changes")
        stream.write(struct.pack(">i", len(meters)))
        for meter in meters:
            stream.write(struct.pack(">iii", meter["measureIndex"], meter["numerator"], meter["denominator"]))


def java_executable():
    if os.environ.get("JAVA_HOME"):
        path = Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if path.is_file():
            return str(path)
    found = shutil.which("java")
    if not found:
        raise RuntimeError("Install Java 17 or newer and put java on PATH or set JAVA_HOME")
    return found


class Interpreter:
    """One reusable CPU interpreter. Use one instance per worker; calls are not thread-safe."""
    def __init__(self, model_path=None, threads=2):
        if not 1 <= threads <= 32:
            raise ValueError("threads must be 1..32")
        self.jar = Path(__file__).with_name("interpreter.jar")
        if not self.jar.is_file():
            raise RuntimeError("Core JAR missing: run python scripts/build_java.py before installing from source")
        path = Path(model_path) if model_path else Path(__file__).parent / "data" / MODEL_NAME
        if not path.is_file():
            raise FileNotFoundError("Model missing: run python scripts/build_java.py or use the release wheel")
        self.model_sha256 = hashlib.sha256(path.read_bytes()).hexdigest()
        if model_path is None and self.model_sha256 != MODEL_SHA256:
            raise ValueError("Bundled model checksum mismatch")
        try:
            import tensorflow as tf
        except ImportError as error:
            raise RuntimeError("Install the inference extra: pip install 'music-sheets-interpreter[inference]'") from error
        self.runtime = tf.lite.Interpreter(model_path=str(path), num_threads=threads)
        self.runtime.allocate_tensors()
        self.input = self.runtime.get_input_details()[0]
        self.output = self.runtime.get_output_details()[0]
        if self.input["shape"].tolist() != [1, 3, 320, 320] or self.input["dtype"] != np.float32:
            raise ValueError("Expected float32 input [1,3,320,320]")
        if self.output["shape"].tolist() != [1, 320, 320] or self.output["dtype"] != np.int64:
            raise ValueError("Expected int64 class IDs [1,320,320]")
        yy, xx = np.mgrid[:320, :320]
        self.edge = np.minimum.reduce([xx, yy, 319 - xx, 319 - yy]) + 1

    def predict(self, gray):
        if not isinstance(gray, np.ndarray) or gray.ndim != 2 or gray.dtype != np.uint8:
            raise ValueError("Expected a 2D uint8 grayscale array")
        height, width = gray.shape
        if not width or not height or gray.size > 20_000_000:
            raise ValueError("Invalid image dimensions")
        labels = np.zeros_like(gray)
        confidence = np.zeros_like(gray)
        for top in tile_starts(height):
            for left in tile_starts(width):
                ch, cw = min(320, height - top), min(320, width - left)
                tile = np.full((320, 320), 255, np.float32)
                tile[:ch, :cw] = gray[top:top + ch, left:left + cw]
                self.runtime.set_tensor(self.input["index"], np.repeat(tile[None, None], 3, axis=1))
                self.runtime.invoke()
                prediction = self.runtime.get_tensor(self.output["index"])[0]
                if prediction.min() < 0 or prediction.max() > 5:
                    raise ValueError("Model returned classes outside 0..5")
                wins = self.edge[:ch, :cw] >= confidence[top:top + ch, left:left + cw]
                labels[top:top + ch, left:left + cw][wins] = prediction[:ch, :cw][wins]
                confidence[top:top + ch, left:left + cw][wins] = self.edge[:ch, :cw][wins]
        return labels

    def interpret(self, image, *, meter=(4, 4), key_fifths=0, width=2048, annotations=None):
        """Recognize a PIL image, returning geometry, musical events and optional OCR interpretations."""
        if len(meter) != 2 or not all(isinstance(n, int) for n in meter):
            raise ValueError("meter must be a numerator/denominator integer pair")
        numerator, denominator = meter
        if not 1 <= numerator <= 32 or denominator not in (1, 2, 4, 8, 16, 32):
            raise ValueError("Invalid meter")
        if not isinstance(key_fifths, int) or not -7 <= key_fifths <= 7:
            raise ValueError("key_fifths must be -7..7")
        gray = grayscale(image, width)
        labels = self.predict(gray)
        with tempfile.TemporaryDirectory(prefix="sheet-interpreter-") as folder:
            page = Path(folder) / "page.page.gz"
            output = Path(folder) / "score.json"
            write_page(page, labels, gray, annotations)
            run = subprocess.run([java_executable(), "-Xmx1g", "-jar", str(self.jar), str(page), str(output),
                                  str(numerator), str(denominator), str(key_fifths)], capture_output=True, text=True)
            if run.returncode:
                raise RuntimeError("Decoder failed: " + run.stderr.strip())
            result = json.loads(output.read_text(encoding="utf-8"))
        result["modelSha256"] = self.model_sha256
        result["initialMeter"] = list(meter)
        result["initialKeyFifths"] = key_fifths
        result["warnings"] = ["Experimental recognition: review pitches, accidentals, ties and timing.",
                              "OCR is caller-supplied; the initial meter is an argument, not an automatic reading."]
        result["warnings"].extend(result.get("tablatureWarnings", []))
        if not result["events"]:
            result["warnings"].append("No playable notes detected on this page.")
        return result
