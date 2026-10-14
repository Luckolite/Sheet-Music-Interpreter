# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original composite tuplet heads; exact clocks and explicit written ratios."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml, note_type, duration_parts, DIVISIONS


def event(duration, actual=3, normal=2, midi=60, start=0):
    return dict(startBeat=start,durationBeats=duration,midi=midi,measureIndex=int(start//4),
                staffIndex=0,staffCount=1,tupletActualNotes=actual,tupletNormalNotes=normal)


class CompositeTupletTests(unittest.TestCase):
    def export(self,events,beats=(4,),heads=None):
        doc=dict(pages=[dict(measureBeats=list(beats),events=events,score=dict(notes=heads or []))])
        before=copy.deepcopy(doc)
        with tempfile.TemporaryDirectory(prefix='original-composite-tuplets-') as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(doc,path);root=ET.parse(path).getroot()
        self.assertEqual(before,doc)
        return root

    def assert_ratio(self,notes,actual,normal):
        for note in notes:
            self.assertIsNotNone(note.find('type'))
            self.assertEqual(str(actual),note.findtext('time-modification/actual-notes'))
            self.assertEqual(str(normal),note.findtext('time-modification/normal-notes'))

    def test_composite_triplet_is_two_tied_glyphs(self):
        root=self.export([event(5/3)])
        notes=root.findall('.//note[pitch]')
        self.assertEqual(['half','eighth'],[n.findtext('type') for n in notes])
        self.assert_ratio(notes,3,2)
        self.assertEqual(16800,sum(int(n.findtext('duration')) for n in notes))
        self.assertEqual(['start','stop'],[n.find('tie').get('type') for n in notes])

    def test_explicit_quintuplet_does_not_become_ordinary_quarter(self):
        root=self.export([event(1,5,4)])
        notes=root.findall('.//note[pitch]')
        self.assertEqual(['quarter','16th'],[n.findtext('type') for n in notes])
        self.assert_ratio(notes,5,4)
        self.assertEqual(DIVISIONS,sum(int(n.findtext('duration')) for n in notes))

    def test_septuplet_composite_has_exact_clock(self):
        notes=self.export([event(5/7,7,4)]).findall('.//note[pitch]')
        self.assert_ratio(notes,7,4)
        self.assertEqual(7200,sum(int(n.findtext('duration')) for n in notes))
        self.assertEqual(2,len(notes))

    def test_equivalent_sextuplet_keeps_original_ratio(self):
        notes=self.export([event(5/3,6,4)]).findall('.//note[pitch]')
        self.assert_ratio(notes,6,4)
        self.assertEqual(16800,sum(int(n.findtext('duration')) for n in notes))

    def test_nonstandard_normal_count_uses_exact_non_greedy_cover(self):
        ratio=dict(tupletActualNotes=7,tupletNormalNotes=3)
        self.assertEqual((405,270),duration_parts(675,[ratio]))
        for duration in (405,270):
            note=ET.Element('note');note_type(note,duration,ratio)
            self.assertIsNotNone(note.find('type'))
            self.assertEqual('7',note.findtext('time-modification/actual-notes'))

    def test_explicit_ratio_never_falls_back_to_another_ratio_or_plain_type(self):
        note=ET.Element('note');note_type(note,DIVISIONS,dict(tupletActualNotes=5,tupletNormalNotes=4))
        self.assertIsNone(note.find('type'))
        self.assertIsNone(note.find('time-modification'))

    def test_single_explicit_tuplet_stays_single(self):
        for actual,normal in ((3,2),(5,4),(6,4),(7,4),(7,3)):
            duration=DIVISIONS*normal//actual
            note=ET.Element('note');note_type(note,duration,dict(tupletActualNotes=actual,tupletNormalNotes=normal))
            self.assertIsNotNone(note.find('type'))
            self.assertEqual((duration,),duration_parts(duration,[dict(tupletActualNotes=actual,tupletNormalNotes=normal)]))

    def test_chord_heads_share_fragment_clock_and_attack_marks(self):
        events=[event(5/3),event(5/3,midi=64)]
        heads=[dict(measureIndex=0,staffIndex=0,staffCount=1,articulations=1)]*2
        root=self.export(events,heads=heads);notes=root.findall('.//note[pitch]')
        self.assertEqual(['C','E','C','E'],[n.findtext('pitch/step') for n in notes])
        self.assertEqual([False,True,False,True],[n.find('chord') is not None for n in notes])
        self.assertEqual([13440,13440,3360,3360],[int(n.findtext('duration')) for n in notes])
        self.assert_ratio(notes,3,2)
        self.assertEqual(1,len(root.findall('.//accent')))

    def test_cross_bar_fragments_retain_ratio_and_complete_ties(self):
        notes=self.export([event(2.5,start=3)],beats=(4,4)).findall('.//note[pitch]')
        self.assertEqual(3,len(notes))
        self.assert_ratio(notes,3,2)
        self.assertEqual(25200,sum(int(n.findtext('duration')) for n in notes))
        self.assertEqual(4,len([tie for n in notes for tie in n.findall('tie')]))

    def test_mixed_chord_ratios_use_shared_exact_fragment_clock(self):
        heads=[dict(tupletActualNotes=3,tupletNormalNotes=2),dict(tupletActualNotes=1,tupletNormalNotes=1)]
        parts=duration_parts(25200,heads)
        self.assertEqual((20160,5040),parts)
        for duration in parts:
            for head in heads:
                note=ET.Element('note');note_type(note,duration,head)
                self.assertIsNotNone(note.find('type'))
                if head['tupletActualNotes']==3:
                    self.assertEqual('3',note.findtext('time-modification/actual-notes'))

    def test_arbitrary_clock_is_not_rounded_into_tuplet_evidence(self):
        ratio=dict(tupletActualNotes=3,tupletNormalNotes=2)
        self.assertEqual((10082,),duration_parts(10082,[ratio]))
        self.assertEqual((7056,),duration_parts(7056))

    def test_exact_composites_for_supported_ratios_preserve_sum_and_type(self):
        for actual,normal in ((3,2),(5,4),(6,4),(7,4),(7,3)):
            ratio=dict(tupletActualNotes=actual,tupletNormalNotes=normal)
            for written in (1.25,2.5,3.75,5,6.5):
                duration=round(written*DIVISIONS*normal/actual)
                parts=duration_parts(duration,[ratio])
                self.assertEqual(duration,sum(parts))
                for part in parts:
                    note=ET.Element('note');note_type(note,part,ratio)
                    self.assertIsNotNone(note.find('type'),(actual,normal,duration,parts))
                    self.assertEqual(str(actual),note.findtext('time-modification/actual-notes'))
