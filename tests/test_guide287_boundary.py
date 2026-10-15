# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original synthetic end-boundary signatures; no source score data."""
import struct
import unittest
from sheet_interpreter import guide_writer as wire


def score():
    return dict(measures=[dict(left=.1, right=.9, top=.2, bottom=.3)], notes=[],
                firstMeasureNumber=1, keyChanges=[], tempoChanges=[], meterChanges=[],
                rests=[], techniqueChanges=[], dynamicChanges=[])


class CourtesyBoundaryGuideTest(unittest.TestCase):
    def test_signatures_at_both_boundaries_are_retained(self):
        value = score()
        value['keyChanges'] = [dict(measureIndex=0, fifths=-1), dict(measureIndex=1, fifths=0)]
        value['meterChanges'] = [dict(measureIndex=0, numerator=3, denominator=8),
                                 dict(measureIndex=1, numerator=4, denominator=4)]
        data = wire.encode(value, 287)
        self.assertEqual((2, 0, -1, 1, 0), struct.unpack_from('>iiiii', data, 32))
        self.assertEqual((2, 0, 3, 8, 1, 4, 4), struct.unpack_from('>iiiiiii', data, 56))

    def test_ordinary_records_keep_legacy_layout(self):
        value = score()
        value['keyChanges'] = [dict(measureIndex=0, fifths=2)]
        value['meterChanges'] = [dict(measureIndex=0, numerator=6, denominator=8)]
        self.assertEqual(wire.encode(value, 286)[4:], wire.encode(value, 287)[4:])

    def test_invalid_or_legacy_boundaries_are_rejected(self):
        for field, row in [('keyChanges', dict(fifths=0)),
                           ('meterChanges', dict(numerator=4, denominator=4))]:
            for version, index, empty in [(286, 1, False), (287, 2, False),
                                           (287, -1, False), (287, True, False),
                                           (287, 0, True)]:
                with self.subTest(field=field, version=version, index=index, empty=empty):
                    value = score()
                    if empty:
                        value['measures'] = []
                    value[field] = [dict(row, measureIndex=index)]
                    with self.assertRaises(ValueError):
                        wire.encode(value, version)


if __name__ == '__main__':
    unittest.main()
