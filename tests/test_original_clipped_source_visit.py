# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Actual Java routes, preserving distinct jumps and ordinary internal boundaries."""
import unittest
from original_tiny_coda_probe import source
from test_original_slide_articulation_gate import document
from sheet_interpreter.navigation import project_navigation
from sheet_interpreter.slide_performance import prepare_slides,resolve_slides


class OriginalClippedSourceVisitTests(unittest.TestCase):
    def test_actual_fractional_coda_does_not_bind_truncated_source(self):
        projected=project_navigation(prepare_slides(source()),120)
        clipped=[n for p in projected['pages']for n in p['events']if n['midi']==72 and n['_slideContext']['clippedRelease']]
        self.assertEqual(1,len(clipped))
        resolved=resolve_slides(projected)
        accepted=[n['startBeat']for p in resolved['pages']for n in p['events']if n.get('_continuousSlide')]
        self.assertEqual([1.],accepted)


    def test_genuine_target_attack_with_terminal_clipped_release_remains_bound(self):
        d=document(0)
        d['pages'][0]['score']['playbackDirections']=[
            dict(measureBoundary=0,kind=12,details=dict(quarterBeatOffset=1.1,eventId='original-fine')),
            dict(measureBoundary=0,kind=9,details=dict(quarterBeatOffset=2.,eventId='original-dc'))]
        projected=project_navigation(prepare_slides(d),120)
        resolved=resolve_slides(projected)
        targets=[n for p in resolved['pages']for n in p['events']if n.get('_continuousSlide')]
        self.assertEqual(2,len(targets))
        self.assertTrue(targets[-1]['_slideContext']['clippedRelease'])
        self.assertAlmostEqual(.1,targets[-1]['durationBeats'])
