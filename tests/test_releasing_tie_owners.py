# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original decoded overlapping voices through actual XML/MIDI and Python sound aggregation."""
import copy
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

from sheet_interpreter.musicxml import write_musicxml
from sheet_interpreter.performance import _perform_one
from sheet_interpreter.tie_owners import releasing_links
from test_tremolo import attacks


def note(start, duration, tied=False, staff=0):
    return dict(kind='PITCHED', midi=60, measureIndex=0, startBeat=start, durationBeats=duration,
                durationFallback=False, tiedFromPrevious=tied, staffCount=1, staffIndex=staff)


def document(notes):
    return dict(pages=[dict(events=notes, measureBeats=[4], totalBeats=4,
                score=dict(tempoChanges=[], notes=[], measures=[{}], expressiveEvents=[]))])


def xml(notes):
    with tempfile.TemporaryDirectory() as directory:
        output = Path(directory)/'original.musicxml'
        write_musicxml(document(notes), output)
        return ET.fromstring(output.read_text(encoding='utf-8'))


def bridge_sound_capture(data, captured):
    """Transport-only replacement: inspect actual Python-owned sounds, never run Java policy."""
    offset = 0
    def read(fmt):
        nonlocal offset
        value = struct.unpack_from(fmt, data, offset)
        offset += struct.calcsize(fmt)
        return value
    magic, bpm, measures = read('>idi')
    assert magic == 0x45585031
    beats = [read('>d')[0] for _ in range(measures)]
    tempos = read('>i')[0]
    offset += tempos*struct.calcsize('>ifdd')
    count = read('>i')[0]
    for _ in range(count):
        length = read('>i')[0]
        identifier = data[offset:offset+length].decode('utf-8')
        offset += length
        start, end, staff, staff_count = read('>ddii')
        captured.append(dict(id=identifier, start=start, end=end, staff=staff, count=staff_count))
    return dict(openingBpm=bpm, segments=[], holds=[], notes=[], attacks=[], pedalControls=[],
                durationSeconds=sum(beats)*60/bpm)


class ReleasingTieTests(unittest.TestCase):
    def test_xml_held_voice_survives_later_short_unison(self):
        root = xml([note(0, 2), note(1, .5), note(2, 1, True)])
        written = [value for value in root.findall('.//note') if value.find('pitch') is not None]
        held = next(value for value in written if value.findtext('duration') == '20160')
        continuation = next(value for value in written if value.findtext('duration') == '10080')
        short = next(value for value in written if value.findtext('duration') == '5040')
        self.assertEqual(['start'], [value.get('type') for value in held.findall('tie')])
        self.assertEqual(['stop'], [value.get('type') for value in continuation.findall('tie')])
        self.assertEqual([], short.findall('tie'))

    def test_midi_held_voice_is_not_retriggered_or_shortened(self):
        events = attacks([note(0, 2), note(1, .5), note(2, 1, True)])
        self.assertEqual([0, 480], [tick for tick, status, _, _ in events if status & 0xf0 == 0x90])
        self.assertEqual([720, 1440], sorted(tick for tick, status, _, _ in events if status & 0xf0 == 0x80))

    def test_two_releasing_owners_and_two_successors_stay_ambiguous(self):
        cases = [[note(0, 2), note(1, 1), note(2, 1, True)],
                 [note(0, 2), note(2, 1, True), note(2, 2, True)]]
        for notes in cases:
            with self.subTest(notes=notes):
                self.assertEqual([], xml(notes).findall('.//tie'))
                events = attacks(notes)
                self.assertEqual(3, sum(status & 0xf0 == 0x90 for _, status, _, _ in events))

    def test_different_staff_cannot_supply_a_tie(self):
        notes = [note(0, 2, staff=1), note(2, 1, True)]
        self.assertEqual([], xml(notes).findall('.//tie'))
        self.assertEqual(2, sum(status & 0xf0 == 0x90 for _, status, _, _ in attacks(notes)))

    def test_unmarked_and_gapped_continuations_keep_their_attack(self):
        for notes in [[note(0, 2), note(2, 1)], [note(0, 1), note(2, 1, True)]]:
            self.assertEqual([], xml(notes).findall('.//tie'))
            self.assertEqual(2, sum(status & 0xf0 == 0x90 for _, status, _, _ in attacks(notes)))

    def test_tremolo_subdivision_survives_an_unmarked_tied_middle_note(self):
        notes = [dict(note(0, 1), tremoloBeats=.25), note(1, 1, True),
                 dict(note(2, 1, True), tremoloBeats=.25)]
        events = attacks(notes)
        self.assertEqual(list(range(0, 1440, 120)),
                         [tick for tick, status, _, _ in events if status & 0xf0 == 0x90])
        self.assertEqual(1440, events[-1][0])

    def test_source_linking_is_order_independent_and_preserves_domain_tolerance(self):
        rows = [dict(lane='source', start=2, end=3, tied=True),
                dict(lane='source', start=0, end=2), dict(lane='source', start=1, end=1.5)]
        self.assertEqual({0: 1}, releasing_links(rows, lambda row: [(row['lane'], 0)]))
        rows[1]['end'] = 1.999
        self.assertEqual({}, releasing_links(rows, lambda row: [(row['lane'], 0)]))
        self.assertEqual({0: 1}, releasing_links(rows, lambda row: [(row['lane'], .01)]))

    def test_actual_python_sound_aggregation_before_mocked_bridge_transport(self):
        for notes, expected in [([note(0, 2), note(1, .5), note(2, 1, True)], [(0, 3), (1, 1.5)]),
                                ([note(0, 2), note(1, 1), note(2, 1, True)], [(0, 2), (1, 2), (2, 3)]),
                                ([note(0, 2), note(2, 1, True), note(2, 2, True)], [(0, 2), (2, 3), (2, 4)])]:
            doc = document(notes)
            before, captured = copy.deepcopy(doc), []
            with patch('sheet_interpreter.performance._bridge', side_effect=lambda data: bridge_sound_capture(data, captured)):
                _perform_one(doc['pages'], 120)
            self.assertEqual(expected, [(value['start'], value['end']) for value in captured])
            self.assertEqual(before, doc)


if __name__ == '__main__':
    unittest.main()
