# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original final-clock records test the MIDI renderer independently of recognition."""
import unittest
from sheet_interpreter.midi import _performance_events_prepared


def event(midi, start, duration, **extra):
    return dict(midi=midi, startBeat=start, durationBeats=duration, staffCount=1,
                staffIndex=0, tiedFromPrevious=False, **extra)


def render(events, tempos=()):
    page = dict(events=events, measureBeats=[4.], totalBeats=4.,
                score=dict(tempoChanges=list(tempos)))
    return _performance_events_prepared(dict(pages=[page]), 120, False)


class ContinuousSlideMidiTests(unittest.TestCase):
    def test_target_pitch_note_on_and_previous_pitch_bend(self):
        _, messages, _ = render([event(72, 1., 1., _continuousSlide={'sourceMidi':60})])
        on = [(t, m) for t, _, m in messages if m[0] & 240 == 144]
        self.assertEqual([(480, 72)], [(t, m[1]) for t, m in on])
        bends = [(t, m[1]+128*m[2]) for t, _, m in messages if m[0] & 240 == 224]
        self.assertEqual((480, 0), bends[0])
        self.assertIn((960, 8192), bends)

    def test_overlapping_unrelated_note_uses_another_channel(self):
        _, messages, _ = render([event(72, 0., 2., _continuousSlide={'sourceMidi':60}), event(80, 0., 2.)])
        ons = {m[1]: m[0] & 15 for _, _, m in messages if m[0] & 240 == 144}
        self.assertNotEqual(ons[72], ons[80])
        self.assertEqual({ons[72]}, {m[0] & 15 for _, _, m in messages if m[0] & 240 == 224})

    def test_large_interval_declares_range_without_clipping(self):
        _, messages, _ = render([event(0, 0., 1., _continuousSlide={'sourceMidi':127})])
        self.assertTrue(any(m[0] & 240 == 176 and m[1:] == bytes([6,127]) for _, _, m in messages))
        self.assertTrue(any(m[0] & 240 == 224 and m[1:] == bytes([127,127]) for _, _, m in messages))

    def test_final_tempo_change_inside_ramp_controls_settlement(self):
        _, messages, _ = render([event(72, 0., 2., _continuousSlide={'sourceMidi':60})],
                               [dict(measureIndex=0, positionInMeasure=.025, bpm=60)])
        center = [t for t, _, m in messages if m[0] & 240 == 224 and m[1:] == bytes([0,64])]
        self.assertEqual(101, center[0])

    def test_conflicting_subdivision_or_guitar_not_silently_rendered(self):
        for extra in (dict(tremoloBeats=.25), dict(guitarEffect={'type':'slide','semitones':2}), dict(tiedFromPrevious=True)):
            n = event(72, 0., 1., _continuousSlide={'sourceMidi':60})
            n.update(extra)
            with self.assertRaises(ValueError):
                render([n])
