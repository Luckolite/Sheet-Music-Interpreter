# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from sheet_interpreter.midi import _performance_events_prepared


class ContinuousSlidePedalTests(unittest.TestCase):
    def test_held_previous_pitch_is_isolated_and_target_reset_waits_for_release(self):
        source=dict(midi=60,startBeat=0.,durationBeats=1.,staffCount=1,staffIndex=0,tiedFromPrevious=False)
        target=dict(midi=72,startBeat=1.,durationBeats=1.,staffCount=1,staffIndex=0,tiedFromPrevious=False,
                    _continuousSlide={'sourceMidi':60})
        page=dict(events=[source,target],measureBeats=[4.],totalBeats=4.,score=dict(tempoChanges=[]),
                  pedalControls=[dict(beat=0.,staffCount=1,value=127),dict(beat=3.,staffCount=1,value=0)])
        _,messages,_=_performance_events_prepared(dict(pages=[page]),120,False)
        channels={m[1]:m[0]&15 for _,_,m in messages if m[0]&240==144}
        self.assertNotEqual(channels[60],channels[72])
        self.assertTrue(any(t==1440 and m==bytes([224|channels[72],0,64]) for t,_,m in messages))
        self.assertFalse(any(t==960 and m==bytes([224|channels[72],0,64]) for t,_,m in messages))
