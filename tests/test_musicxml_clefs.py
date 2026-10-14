# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original scalar source-clef timelines, including backups and split note fragments."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml, DIVISIONS


def document(rows):
    events, notes = [], []
    for start,duration,pitch,clef in rows:
        head=dict(measureIndex=int(start//4),staffIndex=0,staffCount=1)
        notes.append(dict(head,clefBottomDiatonic=clef))
        events.append(dict(head,midi=pitch,startBeat=start,durationBeats=duration,
                           tiedFromPrevious=False,clefInferred=clef == -1))
    length=max((start+duration for start,duration,_,_ in rows),default=4)
    return dict(pages=[dict(measureBeats=[4]*max(1,int((length+3.999)//4)),events=events,
                           score=dict(notes=notes))])


class MusicXmlClefTests(unittest.TestCase):
    def export(self, value):
        before=copy.deepcopy(value)
        with tempfile.TemporaryDirectory(prefix='original-clef-export-') as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(value,path);root=ET.parse(path).getroot()
        self.assertEqual(before,value)
        return root

    def test_every_supported_pitched_source_clef_survives(self):
        for bottom,sign,line,octave in [(18,'F','4',None),(22,'C','4',None),(24,'C','3',None),
                                       (30,'G','2',None),(37,'G','2','1')]:
            root=self.export(document([(0,4,60,bottom)]))
            self.assertEqual(sign,root.findtext('.//clef/sign'))
            self.assertEqual(line,root.findtext('.//clef/line'))
            self.assertEqual(octave,root.findtext('.//clef/clef-octave-change'))

    def test_source_clef_wins_over_average_pitch(self):
        self.assertEqual('F',self.export(document([(0,4,84,18)])).findtext('.//clef/sign'))
        self.assertEqual('G',self.export(document([(0,4,36,30)])).findtext('.//clef/sign'))

    def test_initial_silence_does_not_add_a_fallback_clef_change(self):
        root=self.export(document([(1,1,60,24)]))
        self.assertEqual(1,len(root.findall('.//clef')))
        self.assertEqual('C',root.findtext('part/measure/attributes/clef/sign'))

    def test_bar_boundary_change_is_retained(self):
        root=self.export(document([(0,4,60,24),(4,4,48,18)]))
        self.assertEqual(['C','F'],[n.findtext('attributes/clef/sign') for n in root.findall('part/measure')])

    def test_mid_measure_change_has_the_actual_clock_after_backup(self):
        root=self.export(document([(0,4,60,30),(1,1,62,30),(2,1,64,24)]))
        cursor=0;marks=[]
        for node in root.find('part/measure'):
            if node.tag=='backup':cursor-=int(node.findtext('duration'))
            elif node.tag=='note' and node.find('chord') is None:cursor+=int(node.findtext('duration'))
            elif node.tag=='attributes' and node.find('clef') is not None:
                marks.append((cursor,node.findtext('clef/sign'),node.findtext('clef/line')))
        self.assertEqual([(0,'G','2'),(2*DIVISIONS,'C','3')],marks)

    def test_change_does_not_repeat_on_tied_continuation_fragments(self):
        root=self.export(document([(0,1,60,30),(1,5,62,24)]))
        self.assertEqual(2,len(root.findall('.//clef')))
        self.assertEqual(2,len(root.findall('.//tied')))

    def test_conflicting_simultaneous_source_clefs_are_not_guessed(self):
        with self.assertRaisesRegex(ValueError,'simultaneous clefs'):
            self.export(document([(0,1,60,30),(0,1,64,18)]))

    def test_unknown_source_preserves_legacy_pitch_based_fallback(self):
        self.assertEqual('F',self.export(document([(0,4,48,-1)])).findtext('.//clef/sign'))
        self.assertEqual('G',self.export(document([(0,4,72,-1)])).findtext('.//clef/sign'))

    def test_mismatched_source_note_cannot_supply_the_clef(self):
        value=document([(0,4,72,24)]);value['pages'][0]['score']['notes'][0]['staffIndex']=1
        self.assertEqual('G',self.export(value).findtext('.//clef/sign'))

    def test_explicit_event_display_clef_is_retained_without_source_notes(self):
        value=document([(0,4,60,24)]);value['pages'][0]['score']['notes']=[]
        value['pages'][0]['events'][0]['clefBottomDiatonic']=24
        self.assertEqual('C',self.export(value).findtext('.//clef/sign'))
