# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Correct APP elapsed-frame gate; earlier beat-fraction oracle stays historical."""
import unittest
from test_original_slide_articulation_gate import document, target_off
from sheet_interpreter.midi import performance_events


class OriginalSlideTempoSecondsGateTests(unittest.TestCase):
    def test_final_elapsed_seconds_gate_before_curve(self):
        d=document(4)
        d['pages'][0]['score']['tempoChanges']=[dict(measureIndex=0,positionInMeasure=1.02/3,bpm=60)]
        _,messages,_=performance_events(d,120)
        # Full duration:10/960+110/480 seconds. Half elapsed time ends at tick542.5.
        self.assertEqual([543],target_off(messages))
        centers=[t for t,_,m in messages if m[0]&240==224 and m[1:]==bytes([0,64])]
        self.assertEqual(503,centers[0])
