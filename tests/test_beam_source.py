# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original Guide297 physical-column, chord, hook, and malformed-frame controls."""
import base64
import json
import struct
from pathlib import Path
import unittest
from sheet_interpreter import beam_source
from sheet_interpreter import guide_writer

class SourceBeamWireOriginalTest(unittest.TestCase):
    def test_original_controls_and_exact_reencoding(self):
        cases=json.loads(Path(__file__).with_name('beam_source_controls.json').read_text(encoding='utf-8'))
        for row in cases:
            with self.subTest(row=row['name']):
                data=base64.b64decode(row['payload'])
                if row['expected']:
                    source=beam_source.decode_source(data,row['notes'])
                    self.assertEqual(data,beam_source.encode_source(source,row['notes']))
                else:
                    with self.assertRaises(ValueError):beam_source.decode_source(data,row['notes'])
    def test_current297_appends_to_exact_historical296_body(self):
        score=dict(measures=[],notes=[],firstMeasureNumber=0,keyChanges=[],tempoChanges=[],meterChanges=[],
                   rests=[],techniqueChanges=[],dynamicChanges=[],playbackDirections=[],expressiveEvents=[])
        old=guide_writer.encode(score,296);current=guide_writer.encode(score,297)
        self.assertEqual(297,struct.unpack_from('>i',current)[0])
        self.assertEqual(122,guide_writer.RECOGNITION_REVISION)
        self.assertEqual(old[4:],current[4:len(old)])
        self.assertEqual(beam_source.encode_source(None,0),current[len(old):])
        with self.assertRaises(ValueError):guide_writer.encode(score,max(guide_writer.SUPPORTED_GUIDE_VERSIONS)+1)
    def test_legacy_preflight_preserves_every_kind_of_source_fact(self):
        beam_source.require_legacy_compatible(None,2)
        a=dict(pageIndex=0,noteIndex=0)
        for source in [dict(groups=[],detachedColumns=[dict(members=[a])],noteOrigins=[None]*2),
                       dict(groups=[],detachedColumns=[],noteOrigins=[a,None])]:
            with self.assertRaises(ValueError):beam_source.require_legacy_compatible(source,2)

if __name__=='__main__': unittest.main()
