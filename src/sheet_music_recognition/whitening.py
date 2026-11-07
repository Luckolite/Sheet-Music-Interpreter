# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Pinned original-training paper correction for decoder grayscale."""
from dataclasses import dataclass
import hashlib
from pathlib import Path
import struct

import numpy as np

MODEL_NAME = "paper_field_v1.onnx"
MODEL_SHA256 = "dc27aec9f1d29cc423510277992523991fbbb568770a3acf73875107476ab460"
RASTER_POLICY = "original-segmentation-paper-field-decoder-v1"
MAX_PIXELS = 20_000_000


@dataclass(frozen=True)
class WhiteningResult:
    decoder_gray: np.ndarray
    changed: bool
    raw_sha256: str
    decoder_sha256: str


def raster_sha256(gray):
    height, width = gray.shape
    return hashlib.sha256(struct.pack(">II", width, height) + gray.tobytes()).hexdigest()


class PaperBackgroundWhitening:
    def __init__(self, model_path=None, threads=2):
        if type(threads) is not int or not 1 <= threads <= 32:
            raise ValueError("threads must be 1..32")
        self.model_path = Path(model_path) if model_path else Path(__file__).parent / "data" / MODEL_NAME
        self.threads = threads
        self._session = None

    def verify_model(self):
        if hashlib.sha256(self.model_path.read_bytes()).hexdigest() != MODEL_SHA256:
            raise ValueError("Unrecognized paper-field model checksum")

    def _inference(self, features):
        if self._session is None:
            import onnxruntime as ort
            self.verify_model()
            options = ort.SessionOptions()
            options.intra_op_num_threads = self.threads
            options.inter_op_num_threads = 1
            options.add_session_config_entry("session.force_spinning_stop", "1")
            self._session = ort.InferenceSession(str(self.model_path), options, providers=["CPUExecutionProvider"])
        paper, contrast = self._session.run(["paper", "contrast"], {"features": features[None]})
        expected = (1, 1, features.shape[1], features.shape[2])
        for field, minimum in ((paper, .23), (contrast, .35)):
            if (field.shape != expected or not np.isfinite(field).all()
                    or float(field.min()) < minimum - .00001 or float(field.max()) > 1.00001):
                raise ValueError("Invalid paper-field output")
        return paper[0, 0], contrast[0, 0]

    def correct(self, gray):
        if not isinstance(gray, np.ndarray) or gray.dtype != np.uint8 or gray.ndim != 2:
            raise ValueError("Expected uint8 grayscale image")
        height, width = gray.shape
        if not width or not height or gray.size > MAX_PIXELS:
            raise ValueError("Invalid grayscale dimensions")
        import cv2
        raw_hash = raster_sha256(gray)

        def bright(pixels):
            normalized = pixels.astype(np.float32) / 255
            return bool(np.quantile(normalized, .1) > .94 and np.median(normalized) > .98)

        kernel = np.ones((25, 25), np.uint8)
        if bright(gray) or bright(cv2.morphologyEx(gray, cv2.MORPH_CLOSE, kernel)):
            return WhiteningResult(gray, False, raw_hash, raw_hash)
        scale = min(1., 960 / max(height, width))
        shape = (max(8, round(width * scale)), max(8, round(height * scale)))
        small = cv2.resize(gray.astype(np.float32), shape, interpolation=cv2.INTER_AREA) / 255
        estimate = cv2.morphologyEx(small, cv2.MORPH_CLOSE, kernel)
        features = np.stack((small, estimate)).astype(np.float32, copy=False)
        paper, contrast = self._inference(features)
        paper = cv2.resize(paper, (width, height), interpolation=cv2.INTER_LINEAR)
        contrast = cv2.resize(contrast, (width, height), interpolation=cv2.INTER_LINEAR)
        ink = np.maximum(0, 1 - gray / (255 * paper))
        output = np.rint(255 * (1 - ink / contrast)).clip(0, 255).astype(np.uint8)
        changed = not np.array_equal(output, gray)
        if not changed:
            return WhiteningResult(gray, False, raw_hash, raw_hash)
        return WhiteningResult(output, True, raw_hash, raster_sha256(output))
