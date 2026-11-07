# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Synthetic scanned-tab regression for the installed OCR and Java decoder."""

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from sheet_music_recognition.ocr import LocalOcr, tempo_numbers, rest_count_numbers
from sheet_music_recognition.reader import grayscale, java_executable, write_page


ROOT = Path(__file__).resolve().parents[1]


class BuiltinOcrTests(unittest.TestCase):
    def test_isolated_rest_count_is_offered_without_reading_a_verse_heading(self):
        # Original synthetic boxes; Java still requires a note-free heavy rest bar.
        words = [dict(text="2", left=.30, top=.20, right=.32, bottom=.22),
                 dict(text="Verse 2", left=.36, top=.19, right=.43, bottom=.21),
                 dict(text="34", left=.25, top=.20, right=.27, bottom=.22)]
        tokens = rest_count_numbers(words)
        self.assertEqual([2], [token["value"] for token in tokens])
        self.assertAlmostEqual(.30, tokens[0]["left"])

    def test_printed_tempo_line_bounds_the_digits_and_keeps_direction_anchor(self):
        # Original synthetic OCR geometry, with title and measure numbers nearby.
        words = [dict(text="J = 125", left=.15, top=.125, right=.21, bottom=.145),
                 dict(text="13", left=.06, top=.55, right=.08, bottom=.57),
                 dict(text="Op. 2023", left=.4, top=.04, right=.5, bottom=.06)]
        tokens = tempo_numbers(words)
        self.assertEqual(1, len(tokens))
        self.assertEqual(125, tokens[0]["value"])
        self.assertAlmostEqual(.15, tokens[0]["annotationLeft"])
        self.assertGreater(tokens[0]["left"], .18)
        self.assertAlmostEqual(.21, tokens[0]["right"])

    def test_scanned_pdf_staff_strokes_do_not_hide_a_twelve_fret(self):
        import pypdfium2 as pdfium

        document = pdfium.PdfDocument(ROOT / "tests/fixtures/synthetic-scanned-tab.pdf")
        page = document[0]
        bitmap = page.render(scale=2400 / page.get_width())
        try:
            gray = grayscale(bitmap.to_pil())
        finally:
            bitmap.close()
            page.close()
            document.close()
        words = LocalOcr().words(gray)
        self.assertEqual({"0", "12", "7", "3"}, {word["text"] for word in words})

    def test_scanned_frets_reach_the_decoder_without_supplied_annotations(self):
        # Newly drawn tab rules and frets, saved as pixels so rendering does not
        # vary across operating systems and font rasterizers.
        with Image.open(ROOT / "tests/fixtures/synthetic-scanned-tab.png") as image:
            gray = np.asarray(image.convert("L"), dtype=np.uint8)
        words = LocalOcr().words(gray)
        self.assertEqual({"0", "12", "7", "3"}, {word["text"] for word in words})
        for fret, x in (("0", 240), ("12", 420), ("7", 590), ("3", 760)):
            word = next(word for word in words if word["text"] == fret)
            self.assertLess(abs((word["left"] + word["right"]) * 600 - x), 20)

        with tempfile.TemporaryDirectory() as folder:
            page = Path(folder) / "scan.page.gz"
            output = Path(folder) / "score.json"
            write_page(page, np.zeros_like(gray), gray, {"words": words})
            run = subprocess.run([java_executable(), "-jar", str(ROOT / "src/sheet_music_recognition/interpreter.jar"),
                                  str(page), str(output), "4", "4", "0"],
                                 capture_output=True, text=True)
            self.assertEqual(0, run.returncode, run.stderr)
            score = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual([64, 67, 52, 62], [event["midi"] for event in score["events"]])


if __name__ == "__main__":
    unittest.main()
