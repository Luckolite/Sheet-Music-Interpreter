# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from sheet_interpreter import semantic_wire


def mark(scope='UNRESOLVED', start=None, end=None):
    return dict(eventId='fall-off:exact-note', kind='FALL_OFF',
                start=start, end=end, scope=scope, staffIndex=0, staffCount=1,
                targetEventId='fall-off-note-v1:0:0:1:3f000000:3:3e800000',
                strength='UNSPECIFIED', qualifierText='falloff-v1:curved',
                evidence=[dict(sourceId='printed-fall-off', pageIndex=0,
                               visualX=.5, staffIndex=0, staffCount=1, printedText='○')])


class FallOffSemanticWireOriginals(unittest.TestCase):
    def test_unresolved_target_roundtrips_at_295_and_old_versions_refuse(self):
        row = mark()
        data = semantic_wire.encode([], [row], 1, 295)
        self.assertEqual(([], [row]), semantic_wire.decode(data, 1, 295))
        with self.assertRaises(ValueError):
            semantic_wire.encode([], [row], 1, 294)
        with self.assertRaises(ValueError):
            semantic_wire.decode(data, 1, 294)

    def test_unresolved_owner_does_not_accept_anchor_or_span(self):
        with self.assertRaises(ValueError):
            semantic_wire.expression(mark(start=dict(measureIndex=0, quarterBeatOffset=1)), 1)
        with self.assertRaises(ValueError):
            semantic_wire.expression(mark(end=dict(measureIndex=0, quarterBeatOffset=2)), 1)
        with self.assertRaises(ValueError):
            semantic_wire.expression(mark(scope='PART'), 1)

    def test_resolved_note_owner_still_requires_exact_target(self):
        row = mark(scope='NOTE', start=dict(measureIndex=0, quarterBeatOffset=1))
        self.assertEqual(row['targetEventId'], semantic_wire.expression(row, 1)['targetEventId'])
        row['targetEventId'] = None
        with self.assertRaises(ValueError):
            semantic_wire.expression(row, 1)


if __name__ == '__main__':
    unittest.main()



