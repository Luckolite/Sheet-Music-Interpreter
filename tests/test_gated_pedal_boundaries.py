# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from sheet_interpreter.midi import _performance_events_prepared


def render(down):
    target=dict(midi=72,startBeat=0.,durationBeats=2.,staffCount=1,staffIndex=0,tiedFromPrevious=False,
                _continuousSlide=dict(sourceMidi=60,gate=.5))
    other=dict(midi=76,startBeat=1.5,durationBeats=.5,staffCount=1,staffIndex=0,tiedFromPrevious=False)
    page=dict(events=[target,other],measureBeats=[4.],totalBeats=4.,score=dict(tempoChanges=[]),
              pedalControls=[dict(beat=down,staffCount=1,value=127),dict(beat=3.,staffCount=1,value=0)])
    return _performance_events_prepared(dict(pages=[page]),120,False)[1]


class GatedPedalBoundaryTests(unittest.TestCase):
    def test_pedal_after_gated_off_does_not_hold_old_target(self):
        messages=render(1.5)
        self.assertEqual([480],[t for t,_,m in messages if m[0]&240==128 and m[1]==72])
        bends=[t for t,_,m in messages if m[0]&240==224 and m[1:]==bytes([0,64])]
        self.assertEqual(480,bends[-1])
        self.assertNotIn(1440,bends)

    def test_pedal_enclosing_gated_off_holds_reset_and_channel_to_up(self):
        messages=render(.5)
        channels={m[1]:m[0]&15 for _,_,m in messages if m[0]&240==144}
        self.assertNotEqual(channels[72],channels[76])
        self.assertEqual([480],[t for t,_,m in messages if m[0]&240==128 and m[1]==72])
        self.assertTrue(any(t==1440 and m==bytes([224|channels[72],0,64])for t,_,m in messages))
