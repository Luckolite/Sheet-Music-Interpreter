# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_music_recognition.boundary_ties import resolve_boundary_ties
from sheet_music_recognition.midi import write_midi
from sheet_music_recognition.musicxml import write_musicxml


def document():
    pages = []
    for page, flags in ((1, 8), (2, 2)):
        event = dict(midi=60, boundaryPitch=28, boundaryAccidental=2,
                     boundaryTies=flags, sourceNoteIndex=0, staffIndex=0, staffCount=1,
                     startBeat=0, durationBeats=4, tiedFromPrevious=False)
        pages.append(dict(sourcePage=page, events=[event], totalBeats=4, measureBeats=[4],
                          score=dict(tempoChanges=[], notes=[dict(tiedFromPrevious=False, writtenAccidental=2)])))
    return dict(pages=pages)


class BoundaryTieTests(unittest.TestCase):
    def test_both_shoulders_resolve_without_mutating_input(self):
        original = document()
        snapshot = copy.deepcopy(original)
        result = resolve_boundary_ties(original)
        self.assertTrue(result['pages'][1]['events'][0]['tiedFromPrevious'])
        self.assertTrue(result['pages'][1]['score']['notes'][0]['tiedFromPrevious'])
        self.assertEqual(original, snapshot)

    def test_required_evidence_cannot_be_replaced_by_equal_pitch(self):
        for field, value in [('boundaryTies', 0), ('boundaryTies', 1),
                             ('boundaryPitch', 29), ('staffIndex', 1), ('staffCount', 2),
                             ('startBeat', .5)]:
            with self.subTest(field=field, value=value):
                doc = document()
                doc['pages'][1]['events'][0][field] = value
                self.assertFalse(resolve_boundary_ties(doc)['pages'][1]['events'][0]['tiedFromPrevious'])

    def test_gap_after_outgoing_note_prevents_tie(self):
        doc = document()
        doc['pages'][0]['events'][0]['durationBeats'] = 3
        self.assertFalse(resolve_boundary_ties(doc)['pages'][1]['events'][0]['tiedFromPrevious'])

    def test_skipped_source_page_prevents_tie(self):
        doc = document()
        doc['pages'][1]['sourcePage'] = 3
        self.assertFalse(resolve_boundary_ties(doc)['pages'][1]['events'][0]['tiedFromPrevious'])

    def test_explicit_pitch_contradiction_prevents_tie(self):
        doc = document()
        doc['pages'][1]['events'][0].update(midi=61, boundaryAccidental=1)
        self.assertFalse(resolve_boundary_ties(doc)['pages'][1]['events'][0]['tiedFromPrevious'])

    def test_unmarked_note_keeps_tied_pitch_across_key_change(self):
        doc = document()
        doc['pages'][0]['events'][0]['midi'] = 61
        result = resolve_boundary_ties(doc)
        self.assertEqual(61, result['pages'][1]['events'][0]['midi'])
        self.assertEqual(1, result['pages'][1]['score']['notes'][0]['writtenAccidental'])
        self.assertEqual(result, resolve_boundary_ties(result))

    def test_midi_and_musicxml_exports_use_resolved_ties(self):
        with tempfile.TemporaryDirectory() as folder:
            midi, xml = Path(folder)/'test.mid', Path(folder)/'test.musicxml'
            write_midi(document(), midi)
            write_musicxml(document(), xml)
            self.assertEqual(1, midi.read_bytes().count(bytes((0x90, 60))))
            self.assertEqual(['start', 'stop'], [n.attrib['type'] for n in ET.parse(xml).findall('.//tie')])
