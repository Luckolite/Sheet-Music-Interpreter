# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original scalar guide282 fixtures and a pinned actual902 legacy oracle."""
import copy
import hashlib
from pathlib import Path
import struct
import tempfile
import unittest
from sheet_interpreter import guide_writer as wire


def original_score():
    note = dict(measureIndex=0, positionInMeasure=.375, staffStep=-4, staffIndex=0,
        staffCount=1, pageY=.5, tiedFromPrevious=False, augmentationDots=0, beamCount=1,
        writtenAccidental=2, unbeamedDurationBeats=0., tupletDivisor=3,
        followingRestBeats=0., articulations=0, clefBottomDiatonic=18,
        crossStaffBeam=False, leadingRestBeats=0., compactOpening=False,
        octaveShift=0, boundaryTies=0, tupletNormalNotes=2)
    return dict(measures=[dict(left=.1, right=.9, top=.2, bottom=.8)], firstMeasureNumber=1,
        notes=[note], keyChanges=[], tempoChanges=[], meterChanges=[], rests=[],
        techniqueChanges=[], dynamicChanges=[], playbackDirections=[], expressiveEvents=[])


class UnpitchedGuideTests(unittest.TestCase):
    def test_pitched_default_and_explicit_kind_keep_actual902_legacy_bytes(self):
        value = original_score()
        legacy = wire.encode(value, 281)
        # Generated from this original fixture using the exact published902 writer.
        self.assertEqual('f4c7f6da5befc79c69e4a25c845276b3decb9de6e485dc3a4a599e0eb944bf8c',
                         hashlib.sha256(legacy).hexdigest())
        explicit = copy.deepcopy(value)
        explicit['notes'][0]['kind'] = 'PITCHED'
        for version in range(260, 282):
            with self.subTest(version=version):
                self.assertEqual(wire.encode(value, version), wire.encode(explicit, version))

    def test_guide282_adds_one_kind_byte_after_the_actual_stem_field(self):
        value = original_score()
        value['notes'][0].update(tupletDivisor=5, tupletNormalNotes=3, stemDirection=-1)
        legacy = wire.encode(value, 281)
        typed = wire.encode(value, 282)
        self.assertEqual(282, struct.unpack('>i', typed[:4])[0])
        self.assertEqual(len(legacy) + 1, len(typed))
        self.assertEqual(legacy[4:111], typed[4:111])
        self.assertEqual(0, typed[111])
        self.assertEqual(legacy[111:], typed[112:])
        self.assertEqual(-1, struct.unpack('>i', typed[107:111])[0])

    def test_equal_geometry_and_repeated_same_object_keep_every_typed_record(self):
        value = original_score()
        cross = dict(value['notes'][0], kind='UNPITCHED')
        value['notes'].extend([cross, cross])
        original = copy.deepcopy(value)
        encoded = wire.encode(value, 282)
        self.assertEqual(3, struct.unpack('>i', encoded[28:32])[0])
        records = [encoded[32 + index * 80:112 + index * 80] for index in range(3)]
        self.assertEqual([0, 1, 1], [record[79] for record in records])
        self.assertEqual(records[0][:79], records[1][:79])
        self.assertEqual(records[1], records[2])
        self.assertEqual(original, value)

    def test_legacy_layout_cannot_discard_explicit_unpitched_kind(self):
        value = original_score()
        value['notes'][0]['kind'] = 'UNPITCHED'
        for version in range(260, 282):
            with self.subTest(version=version):
                with self.assertRaisesRegex(ValueError, 'Unpitched attacks require guide282'):
                    wire.encode(value, version)
        self.assertEqual(1, wire.encode(value, 282)[111])

    def test_unknown_kind_and_future_layout_reject(self):
        for kind in [None, True, False, 0, 1, 2, '', 'unknown', 'Pitched', 'UNPITCHED ']:
            value = original_score()
            value['notes'][0]['kind'] = kind
            for version in [281, 282]:
                with self.subTest(kind=kind, version=version):
                    with self.assertRaisesRegex(ValueError, 'Invalid attack kind'):
                        wire.encode(value, version)
        with self.assertRaises(ValueError):
            wire.encode(original_score(), 283)

    def test_current_epoch_layout_and_write_new_keep_existing_output(self):
        self.assertEqual(17, wire.RECOGNITION_REVISION)
        self.assertEqual(282, wire.GUIDE_VERSION)
        value = original_score()
        value['notes'][0]['kind'] = 'UNPITCHED'
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'original.guide'
            wire.write_new(path, value)
            expected = wire.encode(value, 282)
            self.assertEqual(expected, path.read_bytes())
            with self.assertRaises(FileExistsError):
                wire.write_new(path, original_score())
            self.assertEqual(expected, path.read_bytes())


if __name__ == '__main__':
    unittest.main()
