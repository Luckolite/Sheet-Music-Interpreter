# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml, DIVISIONS


def note(pitch, start, duration, staff=0, tied=False):
    return dict(midi=pitch, startBeat=start, durationBeats=duration, staffIndex=staff, tiedFromPrevious=tied)


class MusicXmlTests(unittest.TestCase):
    def export(self, notes, beats=(4, 4), **score):
        doc = dict(inputName='A & B <score>', pages=[dict(events=notes, measureBeats=beats, score=score)])
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'score.musicxml'
            write_musicxml(doc, path)
            return ET.parse(path).getroot()

    def test_chords_overlap_and_silence_preserve_the_clock(self):
        root = self.export([note(60,0,2), note(64,0,2), note(67,1,1)])
        bar = root.find('part/measure')
        self.assertEqual(1, len(bar.findall('note/chord')))
        self.assertEqual(str(4*DIVISIONS), bar.findtext('backup/duration'))
        self.assertEqual({'1','2'}, {n.findtext('voice') for n in bar.findall('note')})
        self.assertEqual('A & B <score>', root.findtext('work/work-title'))
        cursor = 0
        for n in bar:
            if n.tag == 'backup': cursor -= int(n.findtext('duration'))
            if n.tag == 'note' and n.find('chord') is None: cursor += int(n.findtext('duration'))
        self.assertEqual(4*DIVISIONS,cursor)

    def test_cross_bar_sustain_is_split_and_tied(self):
        root = self.export([note(60,3,2)])
        self.assertEqual(['start','stop'], [n.attrib['type'] for n in root.findall('.//tie')])
        self.assertEqual(['start','stop'], [n.attrib['type'] for n in root.findall('.//tied')])

    def test_explicit_tie_and_distinct_staff_parts(self):
        root = self.export([note(60,0,1),note(60,1,1,tied=True),note(43,0,4,staff=1)])
        self.assertEqual(2,len(root.findall('part')))
        self.assertEqual('F',root.findall('part')[1].findtext('measure/attributes/clef/sign'))
        self.assertEqual(2,len(root.findall('.//tie')))

    def test_key_meter_tempo_and_triplet(self):
        root=self.export([note(61,0,1/3)],beats=(4,3),keyChanges=[dict(measureIndex=0,fifths=-2)],meterChanges=[dict(measureIndex=1,numerator=3,denominator=4)],tempoChanges=[dict(measureIndex=1,positionInMeasure=.5,bpm=90)])
        self.assertEqual('D',root.findtext('part/measure/note/pitch/step'))
        self.assertEqual('-1',root.findtext('part/measure/note/pitch/alter'))
        self.assertEqual('3',root.findtext('.//time-modification/actual-notes'))
        second=root.findall('part/measure')[1]
        self.assertEqual('3',second.findtext('attributes/time/beats'))
        self.assertEqual(str(round(1.5*DIVISIONS)),second.findtext('direction/offset'))

    def test_invalid_event_does_not_get_silently_truncated(self):
        for n in (note(60,7,2),note(130,0,1),note(60,float('nan'),1)):
            with self.assertRaises(ValueError): self.export([n])

    def test_empty_bar_contains_rest(self):
        root=self.export([],beats=(4,))
        self.assertIsNotNone(root.find('part/measure/note/rest'))
        self.assertEqual(str(4*DIVISIONS),root.findtext('part/measure/note/duration'))
