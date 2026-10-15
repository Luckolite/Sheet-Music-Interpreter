# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original synthetic compound subdivisions; no external score data."""
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import note_type, duration_parts, DIVISIONS

class CompoundTupletExportTests(unittest.TestCase):
    def test_written_glyph_and_performed_clock_preserve_explicit_ratio(self):
        for actual,normal,beats,glyph in ((2,3,.75,'eighth'),(4,6,.375,'16th'),(4,3,.375,'eighth')):
            with self.subTest(actual=actual,normal=normal):
                ratio=dict(tupletActualNotes=actual,tupletNormalNotes=normal)
                ticks=round(beats*DIVISIONS)
                self.assertEqual((ticks,),duration_parts(ticks,[ratio]))
                note=ET.Element('note');note_type(note,ticks,ratio)
                self.assertEqual(glyph,note.findtext('type'))
                self.assertEqual(str(actual),note.findtext('time-modification/actual-notes'))
                self.assertEqual(str(normal),note.findtext('time-modification/normal-notes'))
                self.assertIsNone(note.find('dot'))

    def test_invalid_actual_count_is_rejected(self):
        with self.assertRaises(ValueError):
            note_type(ET.Element('note'),DIVISIONS,dict(tupletActualNotes=8,tupletNormalNotes=3))
