# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Correct dict-return harness; original two assertions and data remain unchanged."""
import unittest
from test_original_slide_tremolo_conflict import document
from sheet_interpreter.slide_performance import prepare_slides, resolve_slides
from sheet_interpreter.performance import perform_expressions


class OriginalSlideTremoloConflictTests(unittest.TestCase):
    def test_original_conflict_is_rejected_before_projection(self):
        with self.assertRaises(ValueError):
            prepare_slides(document())

    def test_actual_expression_projection_cannot_silently_drop_three_strikes(self):
        try:
            prepared=prepare_slides(document())
        except ValueError:
            return
        projected=perform_expressions(prepared,120)
        before=[n for p in projected['pages']for n in p['events']if n.get('midi')==74]
        self.assertEqual(4,len(before))
        resolved=resolve_slides(projected)
        after=[n for p in resolved['pages']for n in p['events']if n.get('midi')==74]
        self.assertEqual(4,len(after),'Original four tremolo strikes were erased by slide coalescing')
