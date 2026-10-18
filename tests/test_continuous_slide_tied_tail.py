# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from sheet_interpreter.midi import _performance_events_prepared


class ContinuousSlideTiedTailTests(unittest.TestCase):
    def test_unmarked_tied_tail_extends_existing_slide_without_second_attack(self):
        target=dict(midi=72,startBeat=0.,durationBeats=.1,staffCount=1,staffIndex=0,
                    tiedFromPrevious=False,_continuousSlide={'sourceMidi':60})
        tail=dict(midi=72,startBeat=.1,durationBeats=1.9,staffCount=1,staffIndex=0,tiedFromPrevious=True)
        page=dict(events=[target,tail],measureBeats=[4.],totalBeats=4.,score=dict(tempoChanges=[]))
        _,messages,_=_performance_events_prepared(dict(pages=[page]),120,False)
        self.assertEqual([0],[t for t,_,m in messages if m[0]&240==144])
        self.assertEqual([960],[t for t,_,m in messages if m[0]&240==128])
        center=[t for t,_,m in messages if m[0]&240==224 and m[1:]==bytes([0,64])]
        self.assertEqual(154,center[0])

    def test_marked_new_target_never_merges_into_prior_normal_tone(self):
        source=dict(midi=72,startBeat=0.,durationBeats=1.,staffCount=1,staffIndex=0,tiedFromPrevious=False)
        target=dict(midi=72,startBeat=1.,durationBeats=1.,staffCount=1,staffIndex=0,
                    tiedFromPrevious=False,_continuousSlide={'sourceMidi':60})
        page=dict(events=[source,target],measureBeats=[4.],totalBeats=4.,score=dict(tempoChanges=[]))
        _,messages,_=_performance_events_prepared(dict(pages=[page]),120,False)
        self.assertEqual([0,480],[t for t,_,m in messages if m[0]&240==144])
