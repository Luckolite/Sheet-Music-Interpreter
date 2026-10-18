# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Additive route fixtures reach the navigation branch; earlier empty-route fixtures retained."""
import unittest
from unittest.mock import patch
from test_continuous_slide_context import document, occurrence
from sheet_interpreter.slide_performance import prepare_slides, resolve_slides
from sheet_interpreter.navigation import _project_one, project_navigation


class ContinuousSlideNavigationQualifiedTests(unittest.TestCase):
    def project(self, route):
        d=document()
        d['pages'][0]['score']['playbackDirections']=[dict(measureBoundary=0,kind=4)]
        with patch('sheet_interpreter.navigation._route',return_value=dict(complete=True,diagnostics=[],occurrences=route)):
            return resolve_slides(_project_one(prepare_slides(d),120))

    def test_reached_jump_into_target_abstains(self):
        d=self.project([occurrence('jump',1,2,0.)])
        self.assertFalse(any(n.get('_continuousSlide')for n in d['pages'][0]['events']))

    def test_reached_repeated_route_has_two_original_attacks(self):
        d=self.project([occurrence('a',0,1,0.),occurrence('b',1,2,3.),
                        occurrence('c',0,1,6.),occurrence('d',1,2,9.)])
        targets=[n for n in d['pages'][0]['events']if n.get('_continuousSlide')]
        self.assertEqual([1.,7.],[n['startBeat']for n in targets])
        self.assertEqual([5.,5.],[n['durationBeats']for n in targets])

    def test_actual_java_repeat_route_preserves_two_whole_targets(self):
        d=document()
        d['pages'][0]['score']['playbackDirections']=[dict(measureBoundary=0,kind=4),dict(measureBoundary=2,kind=5)]
        played=resolve_slides(project_navigation(prepare_slides(d),120))
        targets=[n for n in played['pages'][0]['events']if n.get('_continuousSlide')]
        self.assertEqual([1.,7.],[n['startBeat']for n in targets])
        self.assertEqual([5.,5.],[n['durationBeats']for n in targets])
