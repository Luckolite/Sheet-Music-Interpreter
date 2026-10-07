# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original raster workload controls; no score content or model inference."""
import unittest
from sheet_interpreter.page_processing_budget import decoder_timeout_seconds


class PageProcessingBudgetTest(unittest.TestCase):
    def test_ordinary_pages_keep_the_established_deadline(self):
        for dimensions in [(2048, 2898), (2048, 3906), (1, 1)]:
            self.assertEqual(360, decoder_timeout_seconds(*dimensions))

    def test_accepted_tall_page_has_time_for_complete_decoding(self):
        deadline = decoder_timeout_seconds(2000, 9000)
        self.assertGreater(deadline, 360)
        self.assertLessEqual(deadline, 600)

    def test_largest_accepted_raster_has_a_hard_upper_bound(self):
        self.assertEqual(600, decoder_timeout_seconds(2000, 10000))

    def test_rotating_the_same_workload_keeps_the_same_budget(self):
        self.assertEqual(decoder_timeout_seconds(2000, 9000),
                         decoder_timeout_seconds(9000, 2000))

    def test_invalid_or_oversized_rasters_are_rejected(self):
        for dimensions in [(0, 100), (-1, 100), (2000, 10001),
                           (True, 100), (2048.0, 9000), (2048, "9000")]:
            with self.assertRaises(ValueError):
                decoder_timeout_seconds(*dimensions)

    def test_more_work_never_lowers_or_exceeds_the_bounded_deadline(self):
        deadlines = [decoder_timeout_seconds(2000, height)
                     for height in [4000, 4001, 5000, 7000, 9000, 10000]]
        self.assertEqual(deadlines, sorted(deadlines))
        self.assertTrue(all(360 <= deadline <= 600 for deadline in deadlines))


if __name__ == '__main__':
    unittest.main()
