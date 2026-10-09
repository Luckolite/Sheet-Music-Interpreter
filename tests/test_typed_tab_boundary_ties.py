# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.boundary_ties import resolve_boundary_ties
from sheet_interpreter.midi import write_midi
from sheet_interpreter.musicxml import write_musicxml


def identity(directions, string=0, count=6, fret=5, open_pitch=55):
    return directions | 16 | string << 5 | (count - 6) << 8 | fret << 9 | open_pitch << 15


def document():
    pages = []
    for page, flags in ((1, identity(8)), (2, identity(2))):
        event = dict(midi=60, boundaryTies=flags, sourceNoteIndex=0,
                     staffIndex=0, staffCount=1, startBeat=0, durationBeats=4,
                     tiedFromPrevious=False)
        note = dict(tiedFromPrevious=False, writtenAccidental=2)
        pages.append(dict(sourcePage=page, events=[event], totalBeats=4,
                          measureBeats=[4], score=dict(tempoChanges=[], notes=[note])))
    return dict(pages=pages)


class TypedTabBoundaryTieTests(unittest.TestCase):
    def test_matching_identity_resolves_without_diatonic_metadata_or_mutation(self):
        original = document()
        before = copy.deepcopy(original)
        result = resolve_boundary_ties(original)
        self.assertTrue(result['pages'][1]['events'][0]['tiedFromPrevious'])
        self.assertTrue(result['pages'][1]['score']['notes'][0]['tiedFromPrevious'])
        self.assertEqual(2, result['pages'][1]['score']['notes'][0]['writtenAccidental'])
        self.assertEqual(before, original)
        self.assertEqual(result, resolve_boundary_ties(result))

    def test_equal_midi_cannot_substitute_for_each_identity_field(self):
        for flags in (identity(2, string=1), identity(2, count=7),
                      identity(2, fret=6, open_pitch=54), identity(2, open_pitch=56)):
            original = document()
            original['pages'][1]['events'][0]['boundaryTies'] = flags
            with self.subTest(flags=flags):
                self.assertFalse(resolve_boundary_ties(original)['pages'][1]['events'][0]['tiedFromPrevious'])

    def test_legacy_and_typed_cannot_match_in_either_direction(self):
        for page in (0, 1):
            original = document()
            event = original['pages'][page]['events'][0]
            event.update(boundaryTies=8 if page == 0 else 2, boundaryPitch=28, boundaryAccidental=2)
            self.assertFalse(resolve_boundary_ties(original)['pages'][1]['events'][0]['tiedFromPrevious'])

    def test_wrong_direction_midi_staff_page_or_time_prevents_match(self):
        for field, value in [('boundaryTies', identity(1)), ('midi', 61),
                             ('staffIndex', 1), ('staffCount', 2), ('startBeat', .5)]:
            original = document()
            original['pages'][1]['events'][0][field] = value
            with self.subTest(field=field):
                self.assertFalse(resolve_boundary_ties(original)['pages'][1]['events'][0]['tiedFromPrevious'])
        original = document()
        original['pages'][1]['sourcePage'] = 3
        self.assertFalse(resolve_boundary_ties(original)['pages'][1]['events'][0]['tiedFromPrevious'])
        original = document()
        original['pages'][0]['events'][0]['durationBeats'] = 3
        self.assertFalse(resolve_boundary_ties(original)['pages'][1]['events'][0]['tiedFromPrevious'])

    def test_invalid_identity_rejects_before_page_matching(self):
        for flags in (-1, True, 16, 40, identity(8) | 1 << 22,
                      identity(8, string=6), identity(8, fret=37), identity(8, fret=36, open_pitch=92)):
            original = document()
            original['pages'][0]['events'][0]['boundaryTies'] = flags
            with self.subTest(flags=flags), self.assertRaisesRegex(ValueError, 'boundary tie'):
                resolve_boundary_ties(original)

    def test_exports_use_one_attack_and_matching_tie_endpoints(self):
        with tempfile.TemporaryDirectory() as folder:
            midi, xml = Path(folder)/'test.mid', Path(folder)/'test.musicxml'
            write_midi(document(), midi)
            write_musicxml(document(), xml)
            self.assertEqual(1, midi.read_bytes().count(bytes((0x90, 60))))
            self.assertEqual(['start', 'stop'], [n.attrib['type'] for n in ET.parse(xml).findall('.//tie')])
