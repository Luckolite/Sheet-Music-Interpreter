# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import struct
import unittest
from sheet_interpreter import guide_writer as wire


def identity(directions=8, string=0, count=6, fret=5, open_pitch=55):
    return directions | 16 | string << 5 | (count - 6) << 8 | fret << 9 | open_pitch << 15


def score():
    note = dict(measureIndex=0, positionInMeasure=0.5, staffStep=0, staffIndex=0,
                staffCount=1, pageY=0.5, tiedFromPrevious=False, augmentationDots=0,
                beamCount=0, writtenAccidental=2, unbeamedDurationBeats=4.0,
                tupletDivisor=1, followingRestBeats=0.0, articulations=0,
                clefBottomDiatonic=18, crossStaffBeam=False, leadingRestBeats=0.0,
                compactOpening=False, octaveShift=0, boundaryTies=0,
                tupletNormalNotes=1, stemDirection=0, kind='PITCHED')
    return dict(measures=[dict(left=.1, right=.9, top=.1, bottom=.9)],
                firstMeasureNumber=1, notes=[note], keyChanges=[], tempoChanges=[],
                meterChanges=[], rests=[], techniqueChanges=[], dynamicChanges=[],
                playbackDirections=[], expressiveEvents=[])


class TypedTabTieWriterTests(unittest.TestCase):
    def test_current_format_preserves_the_existing_physical_note_layout(self):
        original = score()
        old = wire.encode(original, 283)
        new = wire.encode(original)
        self.assertEqual(284, struct.unpack_from('>i', new)[0])
        self.assertEqual(28, wire.RECOGNITION_REVISION)
        self.assertEqual(old[4:], new[4:])
        original['notes'][0]['boundaryTies'] = identity()
        typed = wire.encode(original)
        self.assertEqual(len(new), len(typed))
        self.assertEqual(identity(), struct.unpack_from('>i', typed, 32 + 67)[0])
        self.assertEqual(new[4:99], typed[4:99])
        self.assertEqual(new[103:], typed[103:])

    def test_every_older_supported_writer_rejects_typed_identity(self):
        original = score()
        original['notes'][0]['boundaryTies'] = identity()
        for version in range(260, 284):
            with self.subTest(version=version), self.assertRaisesRegex(ValueError, 'requires guide284'):
                wire.encode(original, version)

    def test_legacy_directions_still_write_to_every_supported_format(self):
        for direction in range(16):
            original = score()
            original['notes'][0]['boundaryTies'] = direction
            for version in wire.SUPPORTED_GUIDE_VERSIONS:
                wire.encode(original, version)

    def test_valid_six_and_seven_string_extremes_round_trip_exactly(self):
        for flags in (identity(1, 5, 6, 0, 0), identity(15, 6, 7, 36, 91)):
            original = score()
            original['notes'][0]['boundaryTies'] = flags
            before = copy.deepcopy(original)
            data = wire.encode(original)
            self.assertEqual(flags, struct.unpack_from('>i', data, 99)[0])
            self.assertEqual(before, original)

    def test_invalid_metadata_is_rejected(self):
        invalid = [True, False, None, 1.0, -1, 16, 32 | 8, identity() | 1 << 22,
                   identity(string=6), identity(string=7, count=7),
                   identity(fret=37), identity(fret=36, open_pitch=92)]
        for flags in invalid:
            original = score()
            original['notes'][0]['boundaryTies'] = flags
            with self.subTest(flags=flags), self.assertRaisesRegex(ValueError, 'boundary tie'):
                wire.encode(original)

    def test_typed_identity_on_unpitched_attack_is_rejected(self):
        original = score()
        original['notes'][0].update(kind='UNPITCHED', boundaryTies=identity())
        with self.assertRaisesRegex(ValueError, 'Unpitched tab tie'):
            wire.encode(original)

    def test_future_format_rejects(self):
        with self.assertRaises(ValueError):
            wire.encode(score(), 285)
