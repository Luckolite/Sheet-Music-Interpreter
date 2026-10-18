# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original scalar clock controls; no source score or captured geometry."""
import importlib.util
from pathlib import Path
import unittest

from sheet_interpreter.continuous_slide_curve import curve


def tempo(tick, micros):
    return tick, 0, b'\xff\x51\x03'+micros.to_bytes(3, 'big')


class ContinuousSlideCurveTests(unittest.TestCase):
    def test_long_ascending_uses_previous_pitch_and_point16_seconds(self):
        size, bends = curve(0, 960, 60, 72, [tempo(0, 500000)])
        self.assertEqual(12, size)
        self.assertEqual((0, 0), bends[0])
        self.assertEqual((154, 8192), bends[-1])

    def test_descending_large_interval_has_sufficient_range(self):
        size, bends = curve(0, 960, 127, 0, [tempo(0, 500000)])
        self.assertEqual(127, size)
        self.assertEqual(16383, bends[0][1])
        self.assertEqual(8192, bends[-1][1])

    def test_short_note_uses_thirty_percent_duration(self):
        _, bends = curve(0, 48, 60, 62, [tempo(0, 500000)])
        self.assertEqual((15, 8192), bends[-1])

    def test_tempo_change_inside_ramp_integrates_piecewise(self):
        _, bends = curve(0, 960, 60, 64, [tempo(0, 500000), tempo(48, 1000000)])
        # .05 seconds to tick48, then .11 seconds / (1/480 seconds per tick).
        self.assertEqual((101, 8192), bends[-1])
        self.assertIn(48, [tick for tick, _ in bends])

    def test_same_tick_last_tempo_matches_midi_order(self):
        a = curve(0, 960, 60, 64, [tempo(0, 1000000), tempo(0, 500000)])
        b = curve(0, 960, 60, 64, [tempo(0, 500000)])
        self.assertEqual(a, b)

    def test_extremely_short_note_does_not_extend_clock(self):
        _, bends = curve(30, 31, 50, 60, [tempo(0, 150000)])
        self.assertEqual([30], [tick for tick, _ in bends])

    def test_ascending_and_descending_are_monotone(self):
        for source, target in ((40, 90), (90, 40)):
            _, bends = curve(100, 1100, source, target, [tempo(0, 500000)])
            values = [v for _, v in bends]
            self.assertEqual(sorted(values, reverse=source > target), values)
            self.assertEqual(8192, values[-1])

    def test_invalid_midi_or_tempo_rejected(self):
        with self.assertRaises(ValueError):
            curve(0, 1, True, 60, [])
        with self.assertRaises(ValueError):
            curve(0, 1, 50, 60, [tempo(0, 0)])
