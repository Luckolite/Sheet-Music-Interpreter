# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original short-note gate controls, independent of captured scores."""
import copy
import struct
import unittest
from sheet_interpreter.midi import performance_events, performed_document, _performance_events_prepared
from sheet_interpreter.slide_performance import prepare_slides


def document(marks=4, tail=False, hold=None):
    raw=[dict(measureIndex=0,positionInMeasure=x,staffStep=step,staffIndex=0,staffCount=1,
              clefBottomDiatonic=30,articulations=flags,unbeamedDurationBeats=duration,
              beamCount=0,augmentationDots=0,leadingRestBeats=0.,followingRestBeats=0.,tiedFromPrevious=False)
         for x,step,flags,duration in ((.2,5,0,1.),(.6,6,16576|marks,.25))]
    events=[dict(measureIndex=0,staffIndex=0,staffCount=1,midi=midi,startBeat=start,durationBeats=duration,
                 durationFallback=False,tiedFromPrevious=False,clefBottomDiatonic=30)
            for midi,start,duration in ((72,0.,1.),(74,1.,.25))]
    events[1]['slide']=dict(style='continuous',sourceNoteIndex=0,sourceMidi=72)
    if tail:
        raw.append(dict(raw[1],positionInMeasure=.8,articulations=0,tiedFromPrevious=True,unbeamedDurationBeats=.75))
        events.append(dict(events[1],startBeat=1.25,durationBeats=.75,tiedFromPrevious=True))
        events[-1].pop('slide')
    expressions=[]
    if hold is not None:
        x=raw[hold]['positionInMeasure'];bits=struct.unpack('>i',struct.pack('>f',x))[0]
        expressions=[dict(eventId='original-fermata',kind='FERMATA',start=None,end=None,scope='UNRESOLVED',
            staffIndex=0,staffCount=1,targetEventId=f'printed-attack:0:0:1:{bits}',strength='UNSPECIFIED',
            qualifierText='',evidence=[dict(sourceId='fermata-raw-ink',pageIndex=0,visualX=x,
                staffIndex=0,staffCount=1,printedText='fermata')])]
    return dict(pages=[dict(events=events,measureBeats=[3.],totalBeats=3.,score=dict(notes=raw,
        measures=[{}],rests=[],tempoChanges=[],expressiveEvents=expressions,playbackDirections=[]))])


def target_off(messages):
    return [t for t,_,m in messages if m[0]&240==128 and m[1]==74]


class OriginalSlideArticulationGateTests(unittest.TestCase):
    def test_short_staccato_duration_and_curve_use_gated_duration(self):
        _,messages,_=performance_events(document(4),120)
        self.assertEqual([540],target_off(messages))
        bends=[t for t,_,m in messages if m[0]&240==224 and m[1:]==bytes([0,64])]
        self.assertEqual(498,bends[0])

    def test_staccatissimo_takes_precedence(self):
        _,messages,_=performance_events(document(16|4|8),120)
        self.assertEqual([514],target_off(messages))

    def test_tenuto_staccato_gate(self):
        _,messages,_=performance_events(document(4|8),120)
        self.assertEqual([570],target_off(messages))

    def test_gate_applies_after_whole_tied_target(self):
        _,messages,_=performance_events(document(4,tail=True),120)
        self.assertEqual([720],target_off(messages))
        self.assertEqual(1,sum(m[0]&240==144 and m[1]==74 for _,_,m in messages))

    def test_raw_gate_retained_without_source_mutation(self):
        d=document(4|8);before=copy.deepcopy(d);prepared=prepare_slides(d)
        self.assertEqual(.75,prepared['pages'][0]['events'][1].get('_slideGate'))
        self.assertEqual(before,d)

    def test_authoritative_interior_target_hold_overrides_gate(self):
        played,bpm,expressive=performed_document(document(4,tail=True,hold=1),120)
        target=next(n for p in played['pages']for n in p['events']if n.get('_continuousSlide'))
        self.assertIs(True,target['_slideContext'].get('held'))
        self.assertEqual(1,target['_continuousSlide'].get('gate',1))
        _,messages,_=_performance_events_prepared(played,bpm,expressive)
        self.assertEqual([round((target['startBeat']+target['durationBeats'])*480)],target_off(messages))

    def test_other_note_hold_does_not_override_target_gate(self):
        played,bpm,expressive=performed_document(document(4,hold=0),120)
        target=next(n for p in played['pages']for n in p['events']if n.get('_continuousSlide'))
        self.assertIs(False,target['_slideContext'].get('held'))
        self.assertEqual(.5,target['_continuousSlide'].get('gate'))
