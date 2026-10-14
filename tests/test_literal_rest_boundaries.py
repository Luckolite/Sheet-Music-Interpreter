# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml, DIVISIONS

def raw(x):
    return dict(measureIndex=0, positionInMeasure=x, staffIndex=0, staffCount=1)
def rest(x,d):
    return dict(raw(x), durationBeats=d, kind='LITERAL')
def event(start,d):
    return dict(midi=60,startBeat=start,durationBeats=d,staffIndex=0,staffCount=1)

class LiteralRestBoundaryTests(unittest.TestCase):
    def export(self, columns, events, rests):
        doc=dict(inputName='Original rest boundaries',pages=[dict(measureBeats=[4],events=events,
                 score=dict(notes=[raw(x) for x in columns],rests=rests))])
        original=copy.deepcopy(doc)
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'original.musicxml';write_musicxml(doc,path);root=ET.parse(path).getroot()
        self.assertEqual(original,doc)
        return root
    def durations(self,root):
        return [int(n.findtext('duration')) for n in root.findall('.//note') if n.find('rest') is not None]
    def ticks(self,*beats):return [round(b*DIVISIONS) for b in beats]
    def test_trailing_three_written_rests(self):
        self.assertEqual(self.ticks(.5,1,2),self.durations(self.export([.1],[event(0,.5)],
                         [rest(.3,.5),rest(.5,1),rest(.8,2)])))
    def test_leading_two_written_rests(self):
        self.assertEqual(self.ticks(1,1),self.durations(self.export([.8],[event(2,2)],
                         [rest(.1,1),rest(.4,1)])))
    def test_internal_two_written_rests(self):
        self.assertEqual(self.ticks(.5,1),self.durations(self.export([.1,.8],[event(0,.5),event(2,2)],
                         [rest(.3,.5),rest(.5,1)])))
    def test_unrecognized_gap_unchanged(self):
        self.assertEqual(self.ticks(3.5),self.durations(self.export([.1],[event(0,.5)],[])))
    def test_incomplete_optical_rest_slot_keeps_gap(self):
        self.assertEqual(self.ticks(3.5),self.durations(self.export([.1],[event(0,.5)],[rest(.3,1)])))
    def test_ambiguous_same_column_rests_keep_gap(self):
        self.assertEqual(self.ticks(3.5),self.durations(self.export([.1],[event(0,.5)],[rest(.3,1.5),rest(.3,2)])))
    def test_full_measure_rest_keeps_independent_voice(self):
        r=dict(raw(.5),kind='FULL_MEASURE',durationBeats=4)
        root=self.export([.1],[event(0,4)],[r]);self.assertEqual(self.ticks(4),self.durations(root))
        self.assertEqual('yes',root.find('.//rest').get('measure'))
