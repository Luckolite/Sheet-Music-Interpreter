# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original persisted-column controls, exercising the shipped performance module."""
import unittest
from sheet_interpreter.performance import _resolve_pedal


class PedalGlyphColumnsTest(unittest.TestCase):
    def event(self, measure=0, kind='PEDAL_DOWN'):
        return dict(kind=kind, staffIndex=1, staffCount=2,
                    targetEventId=f'pedal-glyph-column-v1:{measure}:0:0:0:1:2',
                    evidence=[dict(sourceId='printed-pedal-glyph', staffIndex=1, staffCount=2)],
                    start=dict(measureIndex=99, quarterBeatOffset=4),
                    end=dict(measureIndex=100, quarterBeatOffset=4), scope='PART')

    def resolve(self, event, expected):
        result = _resolve_pedal(event, dict(measureBeats=[6, 6], score=dict(notes=[]), events=[]))
        self.assertEqual(expected, result['start']['measureIndex'] if result['start'] else None)
        self.assertIsNone(result['end'])
        self.assertEqual('PART' if expected is not None else 'UNRESOLVED', result['scope'])
        return result

    def test_boundaries_and_repeat_resolution(self):
        for measure, kind, expected in [(0, 'PEDAL_DOWN', 0), (1, 'PEDAL_DOWN', 1),
                                        (2, 'PEDAL_UP', 2), (2, 'PEDAL_DOWN', None),
                                        (3, 'PEDAL_DOWN', None)]:
            with self.subTest(measure=measure, kind=kind):
                self.resolve(self.event(measure, kind), expected)
        self.resolve(self.resolve(self.event(), 0), 0)

    def test_invalid_evidence_clears_stale_cached_anchors(self):
        for target in [None, 'pedal-glyph-column-v1:bad',
                       'pedal-glyph-column-v1:0:0:2143289344:0:1:2']:
            with self.subTest(target=target):
                event = self.event()
                event['targetEventId'] = target
                self.resolve(event, None)
        event = self.event()
        event['evidence'][0]['sourceId'] = 'untrusted'
        self.resolve(event, None)
        event = self.event()
        event['staffIndex'] = 0
        self.resolve(event, None)
