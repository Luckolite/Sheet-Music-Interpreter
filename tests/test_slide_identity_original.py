# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original performed-identity controls; no bridge, score pixels or model calls."""
import copy
import importlib
from pathlib import Path
import sys
import types
import unittest

from sheet_interpreter import slide_performance as slide

def note(identity,start,duration,midi=60,visit='r',attack=True,entry=False,staff=0):
    return dict(_slideOrigins=[identity],startBeat=start,durationBeats=duration,
        midi=midi,staffIndex=staff,staffCount=1,tiedFromPrevious=False,
        _slideContext=dict(visitId=visit,attack=attack,clippedEntry=entry,clippedRelease=False))
def target(start=2,duration=2,visit='r',attack=True,entry=False,identity='t',source='s',midi=62):
    n=note(identity,start,duration,midi,visit,attack,entry)
    n['_slideRelation']=dict(sourceId=source,targetId=identity,sourceMidi=60)
    n['slide']=dict(style='continuous',sourceNoteIndex=0,sourceMidi=60)
    return n
def document(notes,projected=True,extent=16):
    d=dict(pages=[dict(events=notes,totalBeats=extent)])
    if projected:d['navigationOccurrences']=[]
    return d
def marked(d):
    return [n for p in d['pages'] for n in p['events'] if '_continuousSlide' in n]
def original():
    raw=[dict(measureIndex=0,staffIndex=0,staffCount=1,clefBottomDiatonic=30,
              articulations=0,positionInMeasure=0,kind='PITCHED'),
         dict(measureIndex=0,staffIndex=0,staffCount=1,clefBottomDiatonic=30,
              articulations=16576,positionInMeasure=.5,kind='PITCHED')]
    events=[dict(measureIndex=0,staffIndex=0,staffCount=1,midi=60,startBeat=0,
                 durationBeats=2,durationFallback=False,tiedFromPrevious=False),
            dict(measureIndex=0,staffIndex=0,staffCount=1,midi=62,startBeat=2,
                 durationBeats=2,durationFallback=False,tiedFromPrevious=False,
                 slide=dict(style='continuous',sourceNoteIndex=0,sourceMidi=60))]
    return dict(pages=[dict(events=events,totalBeats=4,score=dict(notes=raw))])

class IdentityOriginals(unittest.TestCase):
    def test_original_validation_and_input_immutable(self):
        d=original();snapshot=copy.deepcopy(d)
        r=slide.resolve_slides(slide.prepare_slides(d))
        self.assertEqual(d,snapshot);self.assertEqual(marked(r)[0]['_continuousSlide'],dict(sourceMidi=60))
    def test_no_slide_fast_path(self):
        d=document([dict(startBeat=0,durationBeats=1,midi=60)],False)
        self.assertIs(slide.prepare_slides(d),d);self.assertIs(slide.resolve_slides(d),d)
    def test_caller_cannot_supply_trusted_markers(self):
        d=document([target()],False)
        d['pages'][0]['events'][0].pop('slide')
        prepared=slide.prepare_slides(d)
        self.assertNotIn('_slideOrigins',prepared['pages'][0]['events'][0])
        self.assertFalse(marked(slide.resolve_slides(prepared)))
    def test_invalid_original_rest_gap(self):
        d=original();d['pages'][0]['events'][1]['startBeat']=3
        with self.assertRaises(ValueError):slide.prepare_slides(d)
    def test_same_pitch_wrong_identity_abstains(self):
        self.assertFalse(marked(slide.resolve_slides(document([note('wrong',0,2),target()]))))
    def test_skipped_source_visit_abstains(self):
        self.assertFalse(marked(slide.resolve_slides(document([note('s',0,2,visit='old'),target(visit='new')]))))
    def test_source_clipped_without_attack_abstains(self):
        self.assertFalse(marked(slide.resolve_slides(document([note('s',0,2,attack=False,entry=True),target()]))))
    def test_source_continuation_keeps_proved_attack(self):
        r=slide.resolve_slides(document([note('s',0,1),note('s',1,1,attack=False,entry=True),target()]))
        self.assertEqual(len(marked(r)),1)
    def test_target_clipped_entry_abstains(self):
        self.assertFalse(marked(slide.resolve_slides(document([note('s',0,2),target(entry=True)]))))
    def test_projected_context_mandatory(self):
        a=note('s',0,2);a.pop('_slideContext')
        with self.assertRaises(ValueError):slide.resolve_slides(document([a,target()]))
    def test_whole_target_duration_coalesced(self):
        r=slide.resolve_slides(document([note('s',0,2),target(duration=1),target(start=3,duration=4,attack=False,entry=True)]))
        self.assertEqual(len(r['pages'][0]['events']),2)
        self.assertEqual(marked(r)[0]['durationBeats'],5)
    def test_target_gap_rejects(self):
        with self.assertRaises(ValueError):slide.resolve_slides(document([note('s',0,2),target(duration=1),target(start=4,attack=False,entry=True)]))
    def test_target_pitch_change_rejects(self):
        with self.assertRaises(ValueError):slide.resolve_slides(document([note('s',0,2),target(duration=1),target(start=3,attack=False,entry=True,midi=64)]))
    def test_target_attack_twice_same_visit_rejects(self):
        with self.assertRaises(ValueError):slide.resolve_slides(document([note('s',0,2),target(duration=1),target(start=3)]))
    def test_duplicate_source_projection_rejects(self):
        with self.assertRaises(ValueError):slide.resolve_slides(document([note('s',0,2),note('s',0,2),target()]))
    def test_fork_source_rejects(self):
        with self.assertRaises(ValueError):slide.resolve_slides(document([note('s',0,2),target(),target(identity='t2',midi=64)]))
    def test_post_tie_actual_source_pitch(self):
        r=slide.resolve_slides(document([note('s',0,2,midi=67),target()]))
        self.assertEqual(marked(r)[0]['_continuousSlide']['sourceMidi'],67)
    def test_repeated_visits_independent_ramps(self):
        r=slide.resolve_slides(document([note('s',0,2,visit='a'),target(visit='a'),note('s',4,2,visit='b'),target(start=6,visit='b')]))
        self.assertEqual(len(marked(r)),2)
    def test_chain_preserves_two_endpoint_relations(self):
        middle=target(identity='b',duration=1)
        last=target(start=3,source='b',identity='c',midi=64)
        r=slide.resolve_slides(document([note('s',0,2),middle,last]))
        self.assertEqual([n['_continuousSlide']['sourceMidi'] for n in marked(r)],[60,62])
    def test_changed_part_rejects(self):
        a=note('s',0,2,staff=1)
        with self.assertRaises(ValueError):slide.resolve_slides(document([a,target()]))
    def test_conflicting_effect_rejects(self):
        a=target();a['guitarEffect']=dict(type='bend',semitones=2)
        with self.assertRaises(ValueError):slide.resolve_slides(document([note('s',0,2),a]))
    def test_target_coalescing_cannot_drop_gain_change(self):
        later=target(start=3,duration=1,attack=False,entry=True)
        later['performanceAttack']=dict(gain=.5,settledGain=1,seconds=.1)
        with self.assertRaises(ValueError):slide.resolve_slides(document([note('s',0,2),target(duration=1),later]))
