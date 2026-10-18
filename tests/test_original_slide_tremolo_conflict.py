# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original expression/subdivision counterexample, frozen before the V3 guard."""
import unittest
from sheet_interpreter.slide_performance import prepare_slides, resolve_slides
from sheet_interpreter.performance import perform_expressions


def document():
    raw=[dict(measureIndex=0,positionInMeasure=x,staffStep=step,staffIndex=0,staffCount=1,
              clefBottomDiatonic=30,articulations=flags,unbeamedDurationBeats=1.,beamCount=0,
              augmentationDots=0,leadingRestBeats=0.,followingRestBeats=0.,tiedFromPrevious=False)
         for x,step,flags in ((.2,5,0),(.8,6,147648))]
    events=[dict(measureIndex=0,staffIndex=0,staffCount=1,midi=midi,startBeat=float(i),
                 durationBeats=1.,durationFallback=False,tiedFromPrevious=False,clefBottomDiatonic=30)
            for i,midi in enumerate((72,74))]
    events[1].update(tremoloBeats=.25,slide=dict(style='continuous',sourceNoteIndex=0,sourceMidi=72))
    expression=dict(eventId='original-rit',kind='RITARDANDO',start=dict(measureIndex=0,quarterBeatOffset=0.),
                    end=dict(measureIndex=1,quarterBeatOffset=0.),scope='SCORE',staffIndex=0,staffCount=1,
                    targetEventId=None,strength='UNSPECIFIED',qualifierText='',evidence=[dict(
                        sourceId='original-synthetic',pageIndex=0,visualX=.1,staffIndex=0,staffCount=1,printedText='rit.')])
    return dict(pages=[dict(events=events,measureBeats=[3.],totalBeats=3.,score=dict(
        notes=raw,measures=[{}],rests=[],tempoChanges=[],expressiveEvents=[expression],playbackDirections=[]))])
