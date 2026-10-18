# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original typed clock/identity fixtures; no source scores or copied classifier logic."""
import copy, json, sys, tempfile
from pathlib import Path
import xml.etree.ElementTree as ET

def chain(count):
    notes=[]; events=[]
    for i in range(count):
        notes.append(dict(measureIndex=i,positionInMeasure=.5,staffStep=i%5+5,staffIndex=0,staffCount=1,pageY=.4,
            articulations=16576 if i else 0,clefBottomDiatonic=30,writtenAccidental=2,unbeamedDurationBeats=4.,
            beamCount=0,augmentationDots=0,followingRestBeats=0.,leadingRestBeats=0.,tiedFromPrevious=False))
        e=dict(measureIndex=i,staffIndex=0,staffCount=1,midi=72+i%5,startBeat=4.*i,durationBeats=4.,
            durationFallback=False,tiedFromPrevious=False,clefBottomDiatonic=30)
        if i:e['slide']=dict(style='continuous',sourceNoteIndex=i-1,sourceMidi=events[-1]['midi'])
        events.append(e)
    return dict(inputName='Original sequential whole-note chain',pages=[dict(events=events,measureBeats=[4.]*count,totalBeats=4.*count,
        score=dict(notes=notes,measures=[dict(left=.1,right=.9,top=.3,bottom=.5) for i in range(count)],rests=[],tempoChanges=[],
        meterChanges=[dict(measureIndex=0,numerator=4,denominator=4)],keyChanges=[dict(measureIndex=0,fifths=0)]))])

def exported(d):
    from sheet_interpreter.musicxml import write_musicxml
    before=copy.deepcopy(d)
    with tempfile.TemporaryDirectory() as t:
        p=Path(t)/'original.musicxml';write_musicxml(d,p,meter=(4,4),key_fifths=0)
        r=ET.parse(p).getroot()
    assert d==before
    return r
