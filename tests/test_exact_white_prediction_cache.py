# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original tensors exercise cache ownership and custom-model compatibility."""
import unittest
import numpy as np
from sheet_music_recognition import reader


class FakeRuntime:
    def __init__(self, stateful=False):
        self.calls = 0
        self.stateful = stateful
        self.output = np.zeros((1, 320, 320), np.int64)
        yy, xx = np.mgrid[:320, :320]
        self.pattern = xx // 7 + yy // 11

    def set_tensor(self, index, values):
        self.input = values.copy()

    def invoke(self):
        self.calls += 1
        marker = self.calls if self.stateful else int(self.input.sum(dtype=np.float64)) + 1
        self.output[0] = (self.pattern + marker) % 6

    def get_tensor(self, index):
        # Deliberately return a reused buffer rather than a defensive copy.
        return self.output


def make_reader(known=True, stateful=False):
    instance = object.__new__(reader.Interpreter)
    instance.runtime = FakeRuntime(stateful)
    instance.input = {"index": 0}
    instance.output = {"index": 1}
    instance.model_sha256 = reader.MODEL_SHA256 if known else "custom-model"
    yy, xx = np.mgrid[:320, :320]
    instance.edge = np.minimum.reduce([xx, yy, 319 - xx, 319 - yy]) + 1
    return instance


def original_prediction(owner, gray):
    height, width = gray.shape
    labels = np.zeros_like(gray)
    confidence = np.zeros_like(gray)
    for top in reader.tile_starts(height):
        for left in reader.tile_starts(width):
            ch, cw = min(320, height - top), min(320, width - left)
            tile = np.full((320, 320), 255, np.float32)
            tile[:ch, :cw] = gray[top:top + ch, left:left + cw]
            owner.runtime.set_tensor(owner.input["index"], np.repeat(tile[None, None], 3, axis=1))
            owner.runtime.invoke()
            prediction = owner.runtime.get_tensor(owner.output["index"])[0]
            wins = owner.edge[:ch, :cw] >= confidence[top:top + ch, left:left + cw]
            labels[top:top + ch, left:left + cw][wins] = prediction[:ch, :cw][wins]
            confidence[top:top + ch, left:left + cw][wins] = owner.edge[:ch, :cw][wins]
    return labels


class ExactWhitePredictionCacheTest(unittest.TestCase):
    def assert_original(self, gray, known=True, stateful=False):
        before = gray.copy()
        baseline, candidate = make_reader(known, stateful), make_reader(known, stateful)
        expected = original_prediction(baseline, gray)
        actual = candidate.predict(gray)
        np.testing.assert_array_equal(expected, actual)
        np.testing.assert_array_equal(before, gray)
        self.assertGreater(np.count_nonzero(actual), 0, "White must retain actual nonzero model output")
        return baseline, candidate

    def test_partial_and_overlapping_exact_white_tensors_keep_actual_predictions(self):
        for width, height in [(1, 1), (321, 319), (17, 321), (705, 705)]:
            with self.subTest(width=width, height=height):
                gray = np.full((height, width), 255, np.uint8)
                gray.setflags(write=False)
                baseline, candidate = self.assert_original(gray)
                self.assertEqual(1, candidate.runtime.calls)
                self.assertEqual(len(reader.tile_starts(width)) * len(reader.tile_starts(height)), baseline.runtime.calls)

    def test_cached_output_owns_values_across_interleaved_nonwhite_tiles(self):
        gray = np.full((705, 705), 255, np.uint8)
        gray[0, 0] = 0
        gray[512, 512] = 18
        baseline, candidate = self.assert_original(gray)
        self.assertLess(candidate.runtime.calls, baseline.runtime.calls)
        expected = original_prediction(make_reader(), gray)
        first_calls = candidate.runtime.calls
        np.testing.assert_array_equal(expected, candidate.predict(gray))
        self.assertEqual(first_calls * 2, candidate.runtime.calls, "Prediction cache must be local to one page call")

    def test_nearly_white_pixels_do_not_reuse_a_different_tensor(self):
        gray = np.full((705, 705), 254, np.uint8)
        baseline, candidate = self.assert_original(gray)
        self.assertEqual(baseline.runtime.calls, candidate.runtime.calls)

    def test_custom_stateful_model_keeps_one_invocation_per_tile(self):
        gray = np.full((705, 705), 255, np.uint8)
        baseline, candidate = self.assert_original(gray, known=False, stateful=True)
        self.assertEqual(16, baseline.runtime.calls)
        self.assertEqual(baseline.runtime.calls, candidate.runtime.calls)


if __name__ == "__main__":
    unittest.main()
