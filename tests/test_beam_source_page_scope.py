# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original malformed cross-page physical stems and independent-page groups."""
import base64
import json
from pathlib import Path
import unittest
try:
    from sheet_interpreter import beam_source
except ImportError:
    import beam_source

class SourceBeamPageScopeTest(unittest.TestCase):
    def test_physical_scope_and_lossless_valid_frames(self):
        cases=json.loads(Path(__file__).with_name('beam_source_page_scope_controls.json').read_text(encoding='utf-8'))
        for c in cases:
            with self.subTest(name=c['name']):
                frame=base64.b64decode(c['frame'])
                if c['expected']:
                    self.assertEqual(frame,beam_source.encode_source(c['source'],c['notes']))
                    self.assertEqual(c['source'],beam_source.decode_source(frame,c['notes']))
                else:
                    with self.assertRaises(ValueError):beam_source.checked(c['source'],c['notes'])
                    with self.assertRaises(ValueError):beam_source.decode_source(frame,c['notes'])

if __name__=='__main__': unittest.main()
