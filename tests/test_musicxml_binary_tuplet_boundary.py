# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original binary composite spans must not invent incompatible tuplet groups."""
import unittest
import xml.etree.ElementTree as ET

from sheet_music_recognition.musicxml import duration_parts, note_type
from tests import test_musicxml_duration_parts as binary_fixture


class BinaryTupletBoundaryTests(unittest.TestCase):
    def test_five_sixteenths_in_six_quarter_bar_keeps_binary_clock(self):
        # 12096 (5:3 half) + 504 (5:4 64th) sums correctly but changes ratios.
        self.assertEqual((10080, 2520), duration_parts(12600))
        fixture = binary_fixture.WrittenDurationPartsTests()
        root = fixture.export([binary_fixture.event(0, 1.25)], beats=(6,))
        notes = root.findall('.//note[pitch]')
        self.assertEqual(['quarter', '16th'], [n.findtext('type') for n in notes])
        self.assertEqual([10080, 2520], [int(n.findtext('duration')) for n in notes])
        self.assertEqual(['start', 'stop'], [n.find('tie').get('type') for n in notes])
        self.assertEqual([], root.findall('.//time-modification'))
        self.assertEqual(60480, sum(int(n.findtext('duration'))
                                   for n in root.findall('.//note')))

    def test_explicit_five_in_three_keeps_source_ratio_on_binary_duration(self):
        ratio = dict(tupletActualNotes=5, tupletNormalNotes=3)
        # This clock is also an ordinary dotted quarter; the source ratio wins.
        parts = duration_parts(15120, [ratio])
        self.assertGreater(len(parts), 1)
        self.assertEqual(15120, sum(parts))
        for part in parts:
            note = ET.Element('note')
            note_type(note, part, ratio)
            self.assertIsNotNone(note.find('type'))
            self.assertEqual('5', note.findtext('time-modification/actual-notes'))
            self.assertEqual('3', note.findtext('time-modification/normal-notes'))
