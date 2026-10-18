# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original written endpoints, independent of any scanned source."""
import copy
import unittest
from unittest.mock import patch
from sheet_interpreter.midi import performance_events, performed_document
from sheet_interpreter.navigation import _project_one
from sheet_interpreter.slide_performance import prepare_slides, resolve_slides


def document():
    notes = [dict(measureIndex=0, positionInMeasure=x, staffStep=step, staffIndex=0,
                  staffCount=1, pageY=.4, articulations=flags, clefBottomDiatonic=30,
                  writtenAccidental=2, unbeamedDurationBeats=duration, beamCount=0,
                  augmentationDots=0, followingRestBeats=0., leadingRestBeats=0.,
                  tiedFromPrevious=False)
             for x, step, flags, duration in ((.2,5,0,1.),(.8,6,16576,5.))]
    events = [dict(measureIndex=0,staffIndex=0,staffCount=1,midi=midi,startBeat=start,
                   durationBeats=duration,durationFallback=False,tiedFromPrevious=False,
                   clefBottomDiatonic=30)
              for midi,start,duration in ((72,0.,1.),(74,1.,5.))]
    events[1]['slide'] = dict(style='continuous',sourceNoteIndex=0,sourceMidi=72)
    return dict(pages=[dict(events=events,measureBeats=[3.,3.],totalBeats=6.,score=dict(
        notes=notes,measures=[dict(left=.1,right=.9,top=.3,bottom=.5)]*2,rests=[],
        tempoChanges=[],meterChanges=[dict(measureIndex=0,numerator=3,denominator=4)],
        keyChanges=[dict(measureIndex=0,fifths=0)],playbackDirections=[],expressiveEvents=[]))])


def occurrence(identifier, first, last, performed):
    return dict(occurrenceId=identifier,start=dict(measureIndex=first,quarterBeatOffset=0.),
                end=dict(measureIndex=last,quarterBeatOffset=0.),performanceStartBeat=performed,
                performanceEndBeat=performed+3*(last-first))


class ContinuousSlideContextTests(unittest.TestCase):
    def project(self, route):
        prepared = prepare_slides(document())
        with patch('sheet_interpreter.navigation._route',return_value=dict(complete=True,diagnostics=[],occurrences=route)):
            return resolve_slides(_project_one(prepared,120))

    def test_linear_real_endpoint_is_rendered_without_input_mutation(self):
        d=document();before=copy.deepcopy(d)
        _, events, _=performance_events(d,120)
        self.assertEqual(before,d)
        self.assertTrue(any(m[0]&240==224 for _,_,m in events))




    def test_written_rest_gap_invalid_original_metadata_rejected(self):
        d=document();d['pages'][0]['events'][0]['durationBeats']=.5
        with self.assertRaises(ValueError):
            prepare_slides(d)

    def test_real_expression_bridge_preserves_endpoint_on_seconds_clock(self):
        d=document()
        d['pages'][0]['score']['expressiveEvents']=[dict(
            eventId='original-rit',kind='RITARDANDO',start=dict(measureIndex=0,quarterBeatOffset=0.),
            end=dict(measureIndex=2,quarterBeatOffset=0.),scope='SCORE',staffIndex=0,staffCount=1,
            targetEventId=None,strength='UNSPECIFIED',qualifierText='',evidence=[dict(
                sourceId='original-synthetic',pageIndex=0,visualX=.1,staffIndex=0,staffCount=1,
                printedText='rit.')])]
        played,bpm,expressive=performed_document(d,120)
        self.assertTrue(expressive)
        self.assertEqual(120,bpm)
        targets=[n for p in played['pages']for n in p['events']if n.get('_continuousSlide')]
        self.assertEqual(1,len(targets))
        self.assertEqual({'sourceMidi':72},targets[0]['_continuousSlide'])
        self.assertGreater(targets[0]['durationBeats'],5.)
