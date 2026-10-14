# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original exact binary spans, chord fragments and silent gaps."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml, duration_parts, DIVISIONS


def event(start, duration, midi=60, **extra):
    return dict(startBeat=start, durationBeats=duration, midi=midi,
                measureIndex=int(start//4), staffIndex=0, staffCount=1, **extra)


class WrittenDurationPartsTests(unittest.TestCase):
    def export(self, events, beats=(4,), **score):
        document = dict(pages=[dict(events=events, measureBeats=list(beats), score=score)])
        before = copy.deepcopy(document)
        with tempfile.TemporaryDirectory(prefix='original-binary-spans-') as folder:
            path = Path(folder)/'score.musicxml'; write_musicxml(document,path)
            root = ET.parse(path).getroot()
        self.assertEqual(before,document)
        return root

    def test_unknown_two_and_half_beat_gap_has_exact_written_values(self):
        root = self.export([event(0,1.5)])
        rests = [n for n in root.findall('.//note') if n.find('rest') is not None]
        self.assertEqual(['half','eighth'],[n.findtext('type') for n in rests])
        self.assertEqual(5*DIVISIONS//2,sum(int(n.findtext('duration')) for n in rests))
        self.assertEqual([], root.findall('.//rest/../tie'))

    def test_non_single_note_is_tied_with_original_pitch_and_end(self):
        root = self.export([event(0,2.5,61)])
        notes = [n for n in root.findall('.//note') if n.find('pitch') is not None]
        self.assertEqual(['half','eighth'],[n.findtext('type') for n in notes])
        self.assertEqual(['start','stop'],[n.get('type') for n in root.findall('.//tied')])
        self.assertEqual(['1','1'],[n.findtext('pitch/alter') for n in notes])
        self.assertEqual(5*DIVISIONS//2,sum(int(n.findtext('duration')) for n in notes))

    def test_chord_heads_share_each_fragment_clock(self):
        root = self.export([event(0,2.5,60),event(0,2.5,64)])
        notes = [n for n in root.findall('.//note') if n.find('pitch') is not None]
        self.assertEqual(['C','E','C','E'],[n.findtext('pitch/step') for n in notes])
        self.assertEqual([False,True,False,True],[n.find('chord') is not None for n in notes])
        self.assertEqual(['half','half','eighth','eighth'],[n.findtext('type') for n in notes])
        self.assertEqual(4,len(root.findall('.//tied')))

    def test_articulation_and_guitar_text_only_on_original_attack(self):
        root = self.export([event(0,2.5,guitarEffect=dict(type='bend',semitones=2))],
                           notes=[dict(measureIndex=0,staffIndex=0,staffCount=1,articulations=1)])
        self.assertEqual(1,len(root.findall('.//accent')))
        self.assertEqual(1,len(root.findall('.//other-notation')))
        self.assertEqual('bend (+2 semitones)',root.findtext('.//other-notation'))

    def test_cross_bar_continuation_has_no_extra_attack_or_missing_tie(self):
        root = self.export([event(0,6.5,guitarEffect=dict(type='bend'))],beats=(4,4))
        notes = [n for n in root.findall('.//note') if n.find('pitch') is not None]
        self.assertEqual(['whole','half','eighth'],[n.findtext('type') for n in notes])
        self.assertEqual(4,len(root.findall('.//tied')))
        self.assertEqual(1,len(root.findall('.//other-notation')))

    def test_supported_single_values_and_tuplets_stay_single(self):
        for beats in (.5,1,1.5,1.75,2,3,3.5,4,1/3,4/5,4/7):
            duration=round(beats*DIVISIONS)
            self.assertEqual((duration,),duration_parts(duration))
        self.assertEqual((25200,),duration_parts(25200,[dict(tupletActualNotes=3,tupletNormalNotes=2)]))

    def test_unproved_fractional_clock_is_not_rounded_or_reinterpreted(self):
        self.assertEqual((10081,),duration_parts(10081))
        self.assertEqual((7056,),duration_parts(7056))

    def test_release_symbol_stays_on_final_silent_fragment(self):
        from sheet_interpreter.musicxml import emit_rest_gap
        measure=ET.Element('measure')
        emit_rest_gap(measure,0,25200,1,[dict(kind='FERMATA',start=0,release=25200)])
        notes=measure.findall('note')
        self.assertEqual(2,len(notes))
        self.assertIsNone(notes[0].find('.//fermata'))
        self.assertIsNotNone(notes[1].find('.//fermata'))

    def test_phrase_endpoint_stays_on_original_attack_fragment(self):
        from tests.test_musicxml_slurs import document, arc
        doc=document(events=[(0,2.5,60),(2.5,1.5,62)],spans=[arc('original',0,1)])
        with tempfile.TemporaryDirectory(prefix='original-split-phrase-') as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(doc,path)
            notes=ET.parse(path).findall('.//note[pitch]')
        self.assertEqual(3,len(notes))
        self.assertEqual('start',notes[0].find('notations/slur').get('type'))
        self.assertIsNone(notes[1].find('notations/slur'))
        self.assertEqual('stop',notes[2].find('notations/slur').get('type'))

    def test_exhaustive_exact_binary_spans_keep_duration_and_have_type(self):
        from sheet_interpreter.musicxml import note_type
        for duration in range(DIVISIONS//16,8*DIVISIONS+1,DIVISIONS//16):
            parts=duration_parts(duration)
            self.assertEqual(duration,sum(parts))
            for part in parts:
                node=ET.Element('note');note_type(node,part)
                self.assertIsNotNone(node.find('type'),(duration,parts))
