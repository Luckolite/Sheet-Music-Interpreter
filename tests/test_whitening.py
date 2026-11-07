# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
import numpy as np
from sheet_music_recognition.whitening import PaperBackgroundWhitening, raster_sha256


class WhiteningTest(unittest.TestCase):
    def test_clean_bypasses_missing_model(self):
        gray = np.full((96, 128), 255, np.uint8)
        gray[20:80:12, 10:118] = 0
        before = gray.copy()
        result = PaperBackgroundWhitening('missing-model.onnx').correct(gray)
        self.assertIs(result.decoder_gray, gray)
        self.assertFalse(result.changed)
        self.assertEqual(result.raw_sha256, result.decoder_sha256)
        np.testing.assert_array_equal(gray, before)

    def test_known_paper_keeps_source(self):
        owner = PaperBackgroundWhitening()
        owner._inference = lambda features: (np.full(features.shape[1:], .8, np.float32),
                                             np.full(features.shape[1:], .5, np.float32))
        gray = np.full((64, 96), 204, np.uint8)
        gray.flat[3000] = 102
        before = gray.copy()
        result = owner.correct(gray)
        self.assertTrue(result.changed)
        self.assertEqual(int(result.decoder_gray.flat[0]), 255)
        self.assertEqual(int(result.decoder_gray.flat[3000]), 0)
        np.testing.assert_array_equal(gray, before)

    def test_invalid_input(self):
        for gray in (np.zeros((0, 1), np.uint8), np.zeros((2, 2), np.float32),
                     np.zeros((2, 2, 3), np.uint8)):
            with self.assertRaises(ValueError):
                PaperBackgroundWhitening().correct(gray)

    def test_model_hash_is_checked(self):
        from pathlib import Path
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'model.onnx'
            path.write_bytes(b'not the qualified model')
            with self.assertRaises(ValueError):
                PaperBackgroundWhitening(path).verify_model()

    def test_hash_has_dimensions(self):
        gray = np.zeros((8, 8), np.uint8)
        self.assertNotEqual(raster_sha256(gray), raster_sha256(gray.reshape(4, 16)))
