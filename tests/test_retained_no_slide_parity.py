# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import json
import unittest
from pathlib import Path
from original_no_slide_fixtures import fixtures
from sheet_interpreter.midi import performance_events
class RetainedNoSlideParityTests(unittest.TestCase):
    def check_case(self,index):
        expected=json.loads((Path(__file__).parent/'fixtures/straight_slide_no_slide_baseline.json').read_text(encoding='utf-8'))[index]
        events=fixtures[index]
        document=dict(pages=[dict(events=events,measureBeats=[4.],totalBeats=4.,score=dict(tempoChanges=[]))])
        ppq,messages,end=performance_events(document,120)
        actual=dict(ppq=ppq,end=end,messages=[[tick,priority,message.hex()]for tick,priority,message in messages])
        self.assertEqual(expected,actual)
    def test_ordinary_baseline_exact(self): self.check_case(0)
    def test_legacy_guitar_slide_baseline_exact(self): self.check_case(1)
    def test_white_key_gliss_baseline_exact(self): self.check_case(2)
