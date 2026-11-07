# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import tempfile
import unittest
from pathlib import Path
from sheet_music_recognition.midi import write_midi


def note(start, duration, subdivision=0, tied=False, staff=0):
    return dict(midi=60, startBeat=start, durationBeats=duration,
                tremoloBeats=subdivision, tiedFromPrevious=tied, staffCount=2, staffIndex=staff)


def attacks(notes):
    page = dict(events=notes, measureBeats=[4], totalBeats=4, score=dict(tempoChanges=[]))
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / 'tremolo.mid'
        write_midi(dict(pages=[page]), path)
        data = path.read_bytes()[22:]
    index = tick = 0
    result = []
    while index < len(data):
        delta = 0
        while True:
            value = data[index]; index += 1
            delta = (delta << 7) | (value & 127)
            if value < 128:
                break
        tick += delta
        status = data[index]; index += 1
        if status == 255:
            index += 1
            size = data[index]; index += 1 + size
        else:
            pitch, velocity = data[index:index + 2]; index += 2
            result.append((tick, status, pitch, velocity))
    return result


class TremoloMidiTests(unittest.TestCase):
    def test_eighth_note_repeats_twice_without_extending_it(self):
        events = attacks([note(0, .5, .25)])
        self.assertEqual([0, 120], [t for t, s, p, v in events if s == 0x90])
        self.assertEqual([120, 240], [t for t, s, p, v in events if s == 0x80])

    def test_half_note_with_three_strokes_repeats_sixteen_times(self):
        events = attacks([note(0, 2, .125)])
        self.assertEqual(list(range(0, 960, 60)), [t for t, s, p, v in events if s == 0x90])
        self.assertEqual(960, events[-1][0])

    def test_tied_tremolo_keeps_a_continuous_subdivision(self):
        events = attacks([note(0, .375, .25), note(.375, .625, .25, True)])
        self.assertEqual([0, 120, 240, 360], [t for t, s, p, v in events if s == 0x90])
        self.assertEqual(480, events[-1][0])

    def test_unmarked_tied_continuation_retains_tremolo(self):
        events = attacks([note(0, .375, .25), note(.375, .625, tied=True)])
        self.assertEqual([0, 120, 240, 360], [t for t, s, p, v in events if s == 0x90])

    def test_held_unison_keeps_its_channel_while_other_staff_repeats(self):
        events = attacks([note(0, 2), note(0, 1, .25, staff=1)])
        ons = [e for e in events if e[1] & 0xF0 == 0x90]
        self.assertEqual(5, len(ons))
        held = next(e for e in events if e[0] == 960)
        channel = held[1] & 15
        self.assertEqual(1, sum((e[1] & 15) == channel for e in ons))

    def test_invalid_subdivision_is_rejected(self):
        for value in (-1, .3, float('nan')):
            with self.assertRaises(ValueError):
                attacks([note(0, 1, value)])
