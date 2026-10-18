# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original valid DC/DS-to-coda fractional route, no scanned input or mock route."""
import json
from test_original_slide_articulation_gate import document
from sheet_interpreter.slide_performance import prepare_slides, resolve_slides
from sheet_interpreter.navigation import project_navigation


def source():
    d=document(0)
    def mark(kind, at, identifier, **extra):
        return dict(measureBoundary=0,kind=kind,details=dict(quarterBeatOffset=at,eventId=identifier,**extra))
    d['pages'][0]['score']['playbackDirections']=[
        mark(0,0.,'original-segno'),
        mark(1,1.-5e-9,'original-to-coda',targetId='original-coda'),
        mark(3,1.,'original-coda'),
        mark(2,2.,'original-ds',targetId='original-segno',codaTargetId='original-coda'),
    ]
    return d
