# Copyright 2026 Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
"""Original decoded events through the real Java bridges and MIDI writer."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from sheet_interpreter.midi import write_midi


def unresolved(expressive=False):
    page=dict(measureBeats=[4,4],totalBeats=8,events=[dict(startBeat=0,midi=60,durationBeats=1,staffIndex=0,staffCount=1),dict(startBeat=4,midi=62,durationBeats=1,staffIndex=0,staffCount=1)],score=dict(playbackDirections=[dict(measureBoundary=1,kind=13)]))
    if expressive:
        page['score']['expressiveEvents']=[dict(eventId='rit',kind='RITARDANDO',start=dict(measureIndex=0,quarterBeatOffset=0),end=dict(measureIndex=1,quarterBeatOffset=0),scope='SCORE',staffIndex=0,staffCount=1,targetEventId=None,strength='UNSPECIFIED',qualifierText='',evidence=[dict(sourceId='original-synthetic',pageIndex=0,visualX=.1,staffIndex=0,staffCount=1,printedText='rit.')])]
    return dict(pages=[page])


class NavigationAdmissionTest(unittest.TestCase):
    def refuses_before_output(self, document):
        with tempfile.TemporaryDirectory() as folder:
            target=Path(folder)/'unresolved.mid'
            with self.assertRaises(ValueError):
                write_midi(document,target)
            self.assertFalse(target.exists())

    def test_actual_ordinary_bridge_refuses_unexpanded_measure_repeat(self):
        self.refuses_before_output(unresolved())

    def test_actual_expressive_bridge_refuses_unexpanded_measure_repeat(self):
        self.refuses_before_output(unresolved(True))

    def test_python_refuses_legacy_complete_route_with_diagnostics(self):
        # Protocol-value control for the retained old-bridge diagnostic defense.
        route=dict(complete=True,diagnostics=[dict(code='UNSUPPORTED_MEASURE_REPEAT')],occurrences=[])
        with patch('sheet_interpreter.navigation._route',return_value=route):
            self.refuses_before_output(unresolved())
