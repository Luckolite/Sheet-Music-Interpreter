# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original canonical clocks, confirmed by the production Java timing probe."""
import math
import unittest
from sheet_interpreter.arpeggio_notation import plan


def column(preceding, start, duration):
    # Source has fifteen 5:4 sixteenths (or twelve ordinary sixteenths),
    # ending in this two-head chord. Clocks are actual Java Main assignments.
    notes = [dict(kind='PITCHED', measureIndex=preceding, staffIndex=0,
                  staffCount=1, staffStep=step, positionInMeasure=.9200000166893005)
             for step in [0, 4]]
    events = [dict(kind='PITCHED', measureIndex=preceding, staffIndex=0,
                   staffCount=1, sourceNoteIndex=i, startBeat=start,
                   durationBeats=duration, durationFallback=False) for i in range(2)]
    expression = dict(kind='ARPEGGIO', staffIndex=0, staffCount=1,
                      targetEventId=f'arpeggio-column-v1:{preceding}:0:1:0x1.d70a3cp-1:0,4',
                      evidence=[dict(sourceId='printed-arpeggio-wave', staffIndex=0, staffCount=1)])
    page = dict(score=dict(measures=[{} for _ in range(preceding+1)], notes=notes),
                events=events, measureBeats=[3 for _ in range(preceding+1)])
    return expression, page


class FractionalClockTests(unittest.TestCase):
    def test_production_five_to_four_final_chord(self):
        expression, page = column(0, 2.8000000000000003, .2)
        self.assertGreater(page['events'][0]['startBeat']+.2, 3)
        self.assertEqual([0, 1], plan(expression, page))

    def test_ordinary_sixteenth_control(self):
        expression, page = column(0, 2.75, .25)
        self.assertEqual([0, 1], plan(expression, page))

    def test_production_absolute_clocks_after_preceding_bars(self):
        for preceding, start in [(1, 5.800000000000001), (17, 53.8)]:
            expression, page = column(preceding, start, .2)
            self.assertEqual([0, 1], plan(expression, page))

    def test_overrun_outside_existing_java_ulp_bound_abstains(self):
        for preceding, start in [(0, 2.8000000000000003), (1, 5.800000000000001), (17, 53.8)]:
            expression, page = column(preceding, start, .2)
            for event in page['events']:
                event['durationBeats'] += 8*math.ulp(3*(preceding+1))
            self.assertEqual([], plan(expression, page))
