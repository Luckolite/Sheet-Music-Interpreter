# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from test_tremolo import attacks, note


class TabMidiTests(unittest.TestCase):
    def effect(self, kind, delta=0):
        n = note(0, 2)
        n['guitarEffect'] = dict(type=kind, semitones=delta, vibrato=False)
        return n

    def test_bend_does_not_detune_an_overlapping_chord_tone(self):
        other = note(0, 2); other['midi'] = 64
        events = attacks([self.effect('bend', 2), other])
        channels = {p: s & 15 for _, s, p, _ in events if s & 0xf0 == 0x90}
        self.assertNotEqual(channels[60], channels[64])
        self.assertTrue(all(s & 15 == channels[60] for _, s, _, _ in events if s & 0xf0 == 0xe0))
        bends = [a + (b << 7) for _, s, a, b in events if s & 0xf0 == 0xe0]
        self.assertGreater(max(bends), 8192)
        self.assertEqual(8192, bends[-1])

    def test_slide_has_one_attack_and_keeps_original_duration(self):
        events = attacks([self.effect('slide', -2)])
        self.assertEqual(1, sum(s & 0xf0 == 0x90 for _, s, _, _ in events))
        self.assertEqual([960], [t for t, s, _, _ in events if s & 0xf0 == 0x80])
        first = next(a + (b << 7) for _, s, a, b in events if s & 0xf0 == 0xe0)
        self.assertLess(first, 8192)

    def test_hammer_has_softer_attack_and_dead_note_has_short_gate(self):
        hammer = attacks([self.effect('hammer_on', -2)])
        dead = attacks([self.effect('dead')])
        self.assertEqual(62, next(v for _, s, _, v in hammer if s & 0xf0 == 0x90))
        self.assertLess(next(t for t, s, _, _ in dead if s & 0xf0 == 0x80), 200)

    def test_invalid_bend_does_not_silently_overflow(self):
        with self.assertRaises(ValueError):
            attacks([self.effect('bend', 50)])
