# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from test_straight_slide_consumer import document, export


class StraightSlideIdentityTests(unittest.TestCase):
    def test_inferred_pitch_identity_rejected(self):
        d = document()
        d['pages'][0]['events'][0]['clefInferred'] = True
        with self.assertRaises(ValueError):
            export(d)

    def test_unknown_raw_clef_rejected(self):
        d = document()
        for n in d['pages'][0]['score']['notes']:
            n['clefBottomDiatonic'] = -1
        with self.assertRaises(ValueError):
            export(d)

    def test_malformed_raw_identity_rejected(self):
        d = document()
        del d['pages'][0]['score']['notes'][0]['positionInMeasure']
        with self.assertRaises(ValueError):
            export(d)

    def test_explicit_indices_agree_and_preserve_endpoint(self):
        d = document()
        for i, e in enumerate(d['pages'][0]['events']):
            e['sourceNoteIndex'] = i
        self.assertEqual(2, len(export(d).findall('.//slide')))
