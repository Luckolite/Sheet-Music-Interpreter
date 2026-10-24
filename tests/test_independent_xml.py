# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from sheet_interpreter.musicxml import write_musicxml

def fixture(actual=1, normal=1):
    scale = normal / actual
    events = []
    notes = []
    for i, (start, duration, pitch) in enumerate(((0, 1, 48), (0, 1, 52), (1, 1, 60), (2, .5, 55), (2.5, .5, 55))):
        n = dict(measureIndex=0, staffIndex=0, staffCount=1, kind='PITCHED',
                 positionInMeasure=(.1 if i < 2 else (.4 if i == 2 else .75 if i == 3 else .9)),
                 beamCount=0 if i < 3 else 1, augmentationDots=0, unbeamedDurationBeats=1 if i < 3 else 0,
                 tupletDivisor=actual, tupletNormalNotes=normal, articulations=0, stemDirection=-1)
        notes.append(n)
        events.append(dict(measureIndex=0, staffIndex=0, staffCount=1, kind='PITCHED', midi=pitch,
                           startBeat=start*scale, durationBeats=duration*scale,
                           tupletActualNotes=actual, tupletNormalNotes=normal))
    origins = [dict(pageIndex=0, noteIndex=i) for i in range(5)]
    pair = dict(id='original-independent', first=origins[:2], second=origins[2:3], strokes=3,
                clockPolicy='INDEPENDENT_ENDPOINT_DURATIONS')
    return dict(initialMeter=[3,4], pages=[dict(measureBeats=[3*scale], totalBeats=3*scale, events=events,
                           score=dict(notes=notes, measures=[{}], tremoloSource=dict(pairs=[pair], noteOrigins=origins)))])

class IndependentXmlTests(unittest.TestCase):
    def test_canonical_clock_and_original_filled_head_survive_chord(self):
        for actual, normal in ((1, 1),):
            doc = fixture(actual, normal)
            before = copy.deepcopy(doc)
            with tempfile.TemporaryDirectory() as folder:
                target = Path(folder)/'original.musicxml'
                write_musicxml(doc, target)
                root = ET.parse(target).getroot()
            notes = [n for n in root.findall('.//note') if n.find('pitch') is not None]
            for note in notes[:3]:
                self.assertEqual('half', note.findtext('type'))
                self.assertEqual(str(2*actual), note.findtext('time-modification/actual-notes'))
                self.assertEqual(str(normal), note.findtext('time-modification/normal-notes'))
                self.assertEqual('yes', note.find('notehead').get('filled'))
                self.assertEqual('normal', note.findtext('notehead'))
                self.assertEqual('down', note.findtext('stem'))
                self.assertEqual(round(10080*normal/actual), int(note.findtext('duration')))
            self.assertEqual(2, len(root.findall('.//tremolo')))
            self.assertEqual(before, doc)

    def test_unresolved_keeps_ordinary_written_type_and_no_paired_marks(self):
        doc = fixture()
        doc['pages'][0]['score']['tremoloSource']['pairs'][0]['clockPolicy'] = 'UNRESOLVED'
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder)/'ordinary.musicxml'; write_musicxml(doc, target)
            root = ET.parse(target).getroot()
        self.assertEqual([], root.findall('.//tremolo'))
        self.assertEqual('quarter', root.findtext('.//note/type'))

    def test_unbound_outer_tuplet_group_refuses_before_native_import(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(ValueError, 'proved outer tuplet group'):
                write_musicxml(fixture(3, 2), Path(folder)/'unsupported-tuplet.musicxml')

    def test_unsupported_independent_source_shape_refuses_explicitly(self):
        doc = fixture()
        for index in (0, 1, 2):
            doc['pages'][0]['score']['notes'][index]['unbeamedDurationBeats'] = 2
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(ValueError, 'quarter-head|clock'):
                write_musicxml(doc, Path(folder)/'unsupported.musicxml')

