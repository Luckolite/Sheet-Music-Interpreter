# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original synthetic pickup framing; no source scores or retained library data."""
import struct
import unittest
from sheet_interpreter import guide_writer as wire

def score():
    return dict(measures=[dict(left=.1, right=.9, top=.2, bottom=.3)], notes=[],
                firstMeasureNumber=0, keyChanges=[], tempoChanges=[], meterChanges=[],
                rests=[], techniqueChanges=[], dynamicChanges=[])

class PickupGuideWriterTest(unittest.TestCase):
    def test_default_false_appends_one_real_byte(self):
        old = wire.encode(score(), 285)
        new = wire.encode(score(), 286)
        self.assertEqual(286, struct.unpack(">i", new[:4])[0])
        self.assertEqual(old[4:], new[4:-1])
        self.assertEqual(0, new[-1])

    def test_proved_pickup_is_retained(self):
        self.assertEqual(1, wire.encode(dict(score(), openingPickup=True), 286)[-1])

    def test_old_format_cannot_silently_drop_proof(self):
        with self.assertRaises(ValueError):
            wire.encode(dict(score(), openingPickup=True), 285)

    def test_false_keeps_legacy_bytes_exact(self):
        self.assertEqual(wire.encode(score(), 285), wire.encode(dict(score(), openingPickup=False), 285))

    def test_integer_is_not_boolean_proof(self):
        with self.assertRaises(ValueError):
            wire.encode(dict(score(), openingPickup=1), 286)

    def test_null_is_not_boolean_proof(self):
        with self.assertRaises(ValueError):
            wire.encode(dict(score(), openingPickup=None), 286)

    def test_continuation_cannot_claim_opening_pickup(self):
        with self.assertRaises(ValueError):
            wire.encode(dict(score(), openingPickup=True, firstMeasureNumber=7), 286)

    def test_empty_geometry_cannot_claim_pickup(self):
        with self.assertRaises(ValueError):
            wire.encode(dict(score(), openingPickup=True, measures=[]), 286)

if __name__ == "__main__":
    unittest.main()
