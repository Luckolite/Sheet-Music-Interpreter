# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy, hashlib, struct, tempfile, unittest
from pathlib import Path
from sheet_interpreter import guide_writer as wire
# Original scalar bytes from both unchanged public and native legacy writers.
LEGACY_SHA256 = {
    260: '63962cb80d25bb70302021ab13034e77a8e0b0c6ba4ece68e57489915c8de071',
    261: '8daaf839ccdd1057ff4ff5bef102cbb5f56a92c517c0a137b5788c38cf346aa5',
    262: 'f240a46d0adfa3dd9a7f19937ed6973568278ad7a2be04490137e33a95be2120',
    263: '381985c4eb596fcabe6550dbbb643effb21f50d4feec73ed25f02756d824e286',
    264: 'f2d7ce29cbc08a0c79524a03fe78678509ce8b26ecb5f319136915779aabf030',
    265: '4b776d44b8ecf35385f4451275c002d65d938e25f5250560ba1e11a119acad68',
    266: '63d9032f2ee1d42765756ad582f91da75f948d68d5b75e4ad4dc51c3dd3470c8',
    267: 'c6300809a6132ea62da041f58f61a3cf445d339fde6dbd24e9ad06dcda29ae19',
    268: 'd54df6d27fb74633557da9943f32b05a23c755144ca063ed7db24c6fbb44db0d',
    269: '6dc1376a6a3a347860b0f3a19639fc348a135265de3ac7414638ae236b13b4ce',
    270: '0d9de5fb98ead16a1af77f78630d1e10a1f528c6a0cf2aa1918ed999eac6c522',
    271: 'da8010a2e029c4995fafafc4c1c5207c3aad15a736e1c64a581a042368aed93f',
    272: 'd7d3fe06f22d01d103cf014d39550120be648bb0745b22297f4c3f7d610ce184',
    273: '9e16f867eda38ce51851d49c0d339713fbee966c2a872002741f31a182fc3dac',
    274: 'c943ba57a80d9b5923264d57592a56ae8580bd0e86893133535204e0f15939d2',
    275: '49707c32ef60cc05fb66f84b464ee11b6ec5c46722fccc5d9020f4ac7024e623',
    276: 'c5c664c414390a50a6c11e1a873f2acc95f79c6c7d724a79f4c1eba626b884a6',
    277: 'c96ba009abe3bcaf26e07332f0a2bac63321fb9b7ea90278f7253844ca5fc452',
    278: '5e331ca354aa6b854a9e1674156b734a501d793a0ea06c9d0e0baafffe261519',
    279: '6561e2b1c966a4b7c0f373a39534d04a3333960755836ee336880128fb36491d',
    280: 'a7d6aa1e0a516b00d9099f2cd1528f444d11f8684ce9964de7328187ba5b0799',
    281: '1a6f3cc7ea5e4838bda40d9e6516b70e6ea772a7066501aedda898cf5f31e46e',
    282: 'fcd995708f25ea3263cc7289ca4ab40757956b10e2963aaafc2d3111c1054816',
}

def score():
    note = dict(measureIndex=0, positionInMeasure=0.375, staffStep=-4, staffIndex=0, staffCount=2, pageY=0.5, tiedFromPrevious=True, augmentationDots=1, beamCount=1, writtenAccidental=-1, unbeamedDurationBeats=0.0, tupletDivisor=3, followingRestBeats=0.25, articulations=0, clefBottomDiatonic=18, crossStaffBeam=False, leadingRestBeats=0.125, compactOpening=False, octaveShift=0, boundaryTies=0, tupletNormalNotes=2)
    rest = dict(measureIndex=0, positionInMeasure=0.5, pageY=0.2, pageHeight=0.03, staffIndex=0, staffCount=2, durationBeats=4.0)
    return dict(measures=[dict(left=0.1, right=0.9, top=0.1, bottom=0.9)], firstMeasureNumber=1, notes=[note], keyChanges=[dict(measureIndex=0, fifths=-2)], tempoChanges=[dict(measureIndex=0, positionInMeasure=0.1, bpm=90.0, beatUnit=1.0)], meterChanges=[dict(measureIndex=0, numerator=3, denominator=4)], rests=[rest, dict(rest, positionInMeasure=0.7, durationBeats=0.25)], techniqueChanges=[], dynamicChanges=[], playbackDirections=[], expressiveEvents=[])

def rest_start(encoded, version):
    offset = 4
    count = struct.unpack_from('>i', encoded, offset)[0]
    offset += 4 + count * 16 + 4
    count = struct.unpack_from('>i', encoded, offset)[0]
    size = 80 if version >= 282 else 79 if version >= 281 else 75 if version >= 276 else 71 if version >= 262 else 67
    offset += 4 + count * size
    for size in (8, 24, 12):
        count = struct.unpack_from('>i', encoded, offset)[0]
        offset += 4 + count * size
    return offset + 4

class WholeRestWriterTest(unittest.TestCase):

    def test_legacy_actual_writer_bytes_are_unchanged_for_all_versions(self):
        original = score()
        for version in range(260, 283):
            with self.subTest(version=version):
                baseline = LEGACY_SHA256[version]
                self.assertEqual(baseline, hashlib.sha256(wire.encode(original, version)).hexdigest())

    def test_explicit_literal_kind_preserves_legacy_bytes(self):
        original = score()
        literal = copy.deepcopy(original)
        for row in literal['rests']:
            row['kind'] = 'LITERAL'
        for version in range(260, 283):
            self.assertEqual(LEGACY_SHA256[version], hashlib.sha256(wire.encode(literal, version)).hexdigest())

    def test_guide283_appends_exactly_one_rest_byte_and_leaves_all_prior_fields(self):
        original = score()
        legacy = wire.encode(original, 282)
        new = wire.encode(original, 283)
        start = rest_start(legacy, 282)
        self.assertEqual(len(legacy) + 2, len(new))
        self.assertEqual(283, struct.unpack_from('>i', new)[0])
        self.assertEqual(legacy[4:start], new[4:start])
        for i in range(2):
            self.assertEqual(legacy[start + i * 32:start + (i + 1) * 32], new[start + i * 33:start + i * 33 + 32])
            self.assertEqual(0, new[start + i * 33 + 32])
        self.assertEqual(legacy[start + 64:], new[start + 66:])

    def test_full_measure_kind_has_separate_byte_and_raw_base_four(self):
        original = score()
        original['rests'][0]['kind'] = 'FULL_MEASURE'
        new = wire.encode(original, 283)
        start = rest_start(new, 283)
        self.assertEqual(1, new[start + 32])
        self.assertEqual(4, struct.unpack_from('>d', new, start + 24)[0])
        self.assertEqual(new, wire.encode(original))

    def test_full_measure_rejects_every_legacy_target(self):
        original = score()
        original['rests'][0]['kind'] = 'FULL_MEASURE'
        for version in range(260, 283):
            for writer in (wire,):
                with self.subTest(version=version, writer=writer.__name__), self.assertRaisesRegex(ValueError, 'requires guide283'):
                    writer.encode(original, version)

    def test_unknown_explicit_kind_is_rejected_including_null_boolean_numeric(self):
        for kind in [None, True, False, 0, 1, 2, '', 'unknown', 'literal', 'FULL_MEASURE ']:
            for writer in (wire,):
                for version in (282, 283):
                    original = score()
                    original['rests'][0]['kind'] = kind
                    with self.subTest(kind=kind, writer=writer.__name__, version=version), self.assertRaisesRegex(ValueError, 'Invalid rest kind'):
                        writer.encode(original, version)

    def test_full_measure_requires_whole_undotted_base(self):
        for duration in [0.25, 1, 2, 3, 6, 7]:
            for writer in (wire,):
                original = score()
                original['rests'][0].update(kind='FULL_MEASURE', durationBeats=duration)
                with self.subTest(duration=duration), self.assertRaisesRegex(ValueError, 'glyph base'):
                    writer.encode(original, 283)

    def test_rest_duration_is_finite_positive_bounded_number(self):
        for duration in [None, True, False, '4', 0, -1, 17, float('nan'), float('inf')]:
            for writer in (wire,):
                original = score()
                original['rests'][0]['durationBeats'] = duration
                with self.subTest(duration=duration), self.assertRaises(ValueError):
                    writer.encode(original, 283)

    def test_unknown_future_layout_rejects(self):
        for writer in (wire,):
            with self.assertRaises(ValueError):
                writer.encode(score(), 284)

    def test_same_geometry_literal_and_full_remain_distinct_records(self):
        original = score()
        original['rests'] = [original['rests'][0], dict(original['rests'][0], kind='FULL_MEASURE')]
        new = wire.encode(original, 283)
        start = rest_start(new, 283)
        self.assertEqual(new[start:start + 32], new[start + 33:start + 65])
        self.assertEqual([0, 1], [new[start + 32], new[start + 65]])

    def test_inputs_remain_exact_and_default_writer_is283(self):
        original = score()
        original['rests'][0]['kind'] = 'FULL_MEASURE'
        before = copy.deepcopy(original)
        for writer in (wire,):
            self.assertEqual(writer.encode(original, 283), writer.encode(original))
            self.assertEqual(before, original)

    def test_write_new_preserves_previous_candidate(self):
        for writer in (wire,):
            with tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'original.guide'
                original = score()
                original['rests'][0]['kind'] = 'FULL_MEASURE'
                writer.write_new(path, original)
                before = path.read_bytes()
                with self.assertRaises(FileExistsError):
                    writer.write_new(path, score())
                self.assertEqual(before, path.read_bytes())

    def test_empty_rest_section_needs_no_phantom_byte(self):
        original = score()
        original['rests'] = []
        old = wire.encode(original, 282)
        new = wire.encode(original, 283)
        self.assertEqual(old[4:], new[4:])
        self.assertEqual(len(old), len(new))
if __name__ == '__main__':
    unittest.main()
