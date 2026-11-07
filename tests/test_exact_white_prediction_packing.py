# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original arrays verify that only invoked tensors allocate RGB copies."""
import unittest
from unittest.mock import patch

import numpy as np
from sheet_music_recognition import reader
from test_exact_white_prediction_cache import make_reader, original_prediction


class ExactWhitePredictionPackingTest(unittest.TestCase):
    def test_reused_predictions_do_not_allocate_repeated_rgb_tensors(self):
        for pattern in ('white', 'mixed', 'almost-white'):
            with self.subTest(pattern=pattern):
                gray = np.full((705, 705), 255, np.uint8)
                if pattern == 'mixed':
                    gray[0, 0] = 0
                    gray[512, 512] = 18
                elif pattern == 'almost-white':
                    gray.fill(254)
                baseline, candidate = make_reader(), make_reader()
                expected = original_prediction(baseline, gray)
                original_repeat = np.repeat
                with patch.object(reader.np, 'repeat', wraps=original_repeat) as repeat:
                    actual = candidate.predict(gray)
                    self.assertEqual(candidate.runtime.calls, repeat.call_count)
                    for call in repeat.call_args_list:
                        self.assertEqual((1, 1, 320, 320), call.args[0].shape)
                        self.assertEqual(np.float32, call.args[0].dtype)
                        self.assertEqual(3, call.args[1])
                        self.assertEqual(1, call.kwargs['axis'])
                np.testing.assert_array_equal(expected, actual)
                if pattern == 'white':
                    self.assertEqual(1, candidate.runtime.calls)
                elif pattern == 'almost-white':
                    self.assertEqual(16, candidate.runtime.calls)

    def test_unknown_model_fingerprint_preserves_stateful_invocations(self):
        gray = np.full((705, 705), 255, np.uint8)
        baseline, candidate = make_reader(False, True), make_reader(False, True)
        del candidate.model_sha256
        expected = original_prediction(baseline, gray)
        original_repeat = np.repeat
        with patch.object(reader.np, 'repeat', wraps=original_repeat) as repeat:
            np.testing.assert_array_equal(expected, candidate.predict(gray))
            self.assertEqual(16, repeat.call_count)
        self.assertEqual(16, candidate.runtime.calls)
