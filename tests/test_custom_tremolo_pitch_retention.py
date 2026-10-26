# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original transpose/repeat records, through the actual standalone performance and MIDI path."""
import copy
import unittest
from test_paired_tremolo import document
from sheet_interpreter.midi import performance_events

class CustomTremoloPitchRetentionTest(unittest.TestCase):
    def test_pitch_change_preserves_paired_attacks_and_repeats(self):
        for repeat in (False, True):
            with self.subTest(repeat=repeat):
                source = document(chord=False, repeat=repeat)
                page = source['pages'][0]
                for note in page['score']['notes']:
                    note['unbeamedDurationBeats'] = 2
                for side, event in enumerate(page['events']):
                    event.update(startBeat=side, durationBeats=1)
                shifted = copy.deepcopy(source)
                for event in shifted['pages'][0]['events']:
                    event['midi'] += 12
                before_source, before_shifted = copy.deepcopy(source), copy.deepcopy(shifted)
                _, before, _ = performance_events(source)
                _, after, _ = performance_events(shifted)
                attacks = lambda messages: [(tick, message[1]) for tick, _, message in messages
                                            if message[0] & 240 == 144 and message[2]]
                original, transposed = attacks(before), attacks(after)
                self.assertEqual(24 if repeat else 12, len(original))
                self.assertEqual([(tick, pitch + 12) for tick, pitch in original], transposed)
                self.assertEqual(before_source, source)
                self.assertEqual(before_shifted, shifted)

if __name__ == '__main__':
    unittest.main()
