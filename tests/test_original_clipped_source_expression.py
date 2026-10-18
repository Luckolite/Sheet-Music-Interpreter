# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from original_tiny_coda_probe import source
from sheet_interpreter.slide_performance import prepare_slides,resolve_slides
from sheet_interpreter.performance import perform_expressions


class OriginalClippedSourceExpressionTests(unittest.TestCase):
    def test_actual_java_interval_clipped_release_cannot_bind_coda_target(self):
        d=source()
        d['pages'][0]['score']['expressiveEvents']=[dict(eventId='original-rit',kind='RITARDANDO',
            start=dict(measureIndex=0,quarterBeatOffset=0.),end=dict(measureIndex=1,quarterBeatOffset=0.),
            scope='SCORE',staffIndex=0,staffCount=1,targetEventId=None,strength='UNSPECIFIED',qualifierText='',
            evidence=[dict(sourceId='original-synthetic',pageIndex=0,visualX=.1,staffIndex=0,
                           staffCount=1,printedText='rit.')])]
        projected=perform_expressions(prepare_slides(d),120)
        source_parts=[n for p in projected['pages']for n in p['events']if n['midi']==72]
        self.assertTrue(any(n['_slideContext']['clippedRelease']for n in source_parts))
        resolved=resolve_slides(projected)
        self.assertEqual(1,sum(bool(n.get('_continuousSlide'))for p in resolved['pages']for n in p['events']))
