# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import base64
import copy
import json
from pathlib import Path
import unittest
from sheet_interpreter import clock_context as wire

class ClockContextOriginalTest(unittest.TestCase):
    def test_original_record_controls_and_byte_roundtrip(self):
        cases=json.loads(Path(__file__).with_name('clock_context_original_controls.json').read_text())['cases']
        for case in cases:
            with self.subTest(case=case['name']):
                data=base64.b64decode(case['payload']);counts=(case['notes'],case['measures'],case['rests'])
                if case['accepted']:
                    self.assertEqual(data,wire.encode(wire.decode(data,*counts),*counts))
                else:
                    with self.assertRaises(ValueError):wire.decode(data,*counts)

    def test_actual_host_binding_preserves_producer_raster_and_missing_authorities(self):
        source=wire.unavailable(0,0,0)
        source['origin'].update(width=100,height=120,rasterSha256='a'*64,labelsSha256='a'*64)
        before=copy.deepcopy(source)
        bound=wire.bind_document(source,0,0,0,'b'*64,3,9)
        self.assertEqual(before,source)
        self.assertEqual('a'*64,bound['origin']['rasterSha256'])
        self.assertEqual(9,bound['origin']['physicalPageCount'])
        self.assertTrue(any(m['requirement']=='METER_SCOPE'for m in bound['missing']))
        for sha,page,count in [('c'*64,3,9),('b'*64,4,9),('b'*64,3,8)]:
            with self.assertRaises(ValueError):wire.bind_document(bound,0,0,0,sha,page,count)

    def test_legacy_writer_refuses_every_noncanonical_unknown_or_known_context(self):
        source=wire.unavailable(0,0,0)
        wire.require_legacy_compatible(source,0,0,0)
        changed=copy.deepcopy(source);changed['missing']=[]
        with self.assertRaises(ValueError):wire.require_legacy_compatible(changed,0,0,0)
        bound=wire.bind_document(source,0,0,0,'b'*64,3,9)
        with self.assertRaises(ValueError):wire.require_legacy_compatible(bound,0,0,0)

if __name__=='__main__':unittest.main()
