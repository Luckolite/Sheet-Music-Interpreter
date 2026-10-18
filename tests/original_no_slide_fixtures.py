# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import json

from sheet_interpreter.midi import performance_events

def n(pitch, start, length, **extra):
    return dict(midi=pitch,startBeat=start,durationBeats=length,staffCount=1,staffIndex=0,
                tiedFromPrevious=False,**extra)

fixtures = [
    [n(60,0.,1.),n(64,1.,1.)],
    [n(60,0.,1.,guitarEffect=dict(type='slide',semitones=-3)),n(64,1.,1.)],
    [n(84,0.,1.,glissando=dict(style='white_keys',targetMidi=60)),n(60,1.,1.)],
]
