# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import gzip
import struct
import tempfile
import unittest
from pathlib import Path
import numpy as np
from PIL import Image
from sheet_music_recognition.reader import tile_starts, grayscale, write_page
from sheet_music_recognition.midi import write_midi, variable_length


class FrontendTests(unittest.TestCase):
    def test_page_tiling_covers_final_edge_without_duplicates(self):
        for size in (1, 319, 320, 321, 512, 2048, 2650):
            starts = tile_starts(size)
            self.assertEqual(starts, sorted(set(starts)))
            self.assertEqual(0, starts[0])
            self.assertEqual(max(0, size - 320), starts[-1])
            self.assertTrue(all(b - a <= 192 for a, b in zip(starts, starts[1:])))

    def test_transparent_background_becomes_white(self):
        image = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.assertTrue(np.all(grayscale(image, 64) == 255))

    def test_oversized_analysis_is_rejected(self):
        with self.assertRaises(ValueError):
            grayscale(Image.new('L', (1, 6000)), 2048)

    def test_page_file_keeps_unsigned_bytes_and_dimensions(self):
        gray = np.array([[0, 255], [128, 42]], np.uint8)
        labels = np.array([[0, 5], [2, 4]], np.uint8)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'page.gz'
            write_page(path, labels, gray)
            self.assertEqual(struct.pack('>III', 0x52535031, 2, 2) + labels.tobytes() + gray.tobytes(),
                             gzip.decompress(path.read_bytes()))

    def test_annotation_block_accepts_unicode(self):
        gray = np.full((2, 2), 255, np.uint8)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'page.gz'
            write_page(path, np.zeros_like(gray), gray,
                       {'words': [{'text': 'più', 'left': 0, 'top': 0, 'right': .5, 'bottom': .5}]})
            self.assertIn('più'.encode(), gzip.decompress(path.read_bytes()))

    def test_midi_delta_boundary(self):
        self.assertEqual(b'\x7f', variable_length(127))
        self.assertEqual(b'\x81\x00', variable_length(128))
        with self.assertRaises(ValueError):
            variable_length(-1)

    def midi(self, notes):
        page = {'events': notes, 'measureBeats': [4], 'totalBeats': 4, 'score': {'tempoChanges': []}}
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'preview.mid'
            write_midi({'pages': [page]}, path)
            return path.read_bytes()

    def note(self, start, duration, tied=False, staff=0):
        return {'midi': 60, 'startBeat': start, 'durationBeats': duration,
                'tiedFromPrevious': tied, 'staffCount': 2, 'staffIndex': staff}

    def test_tied_note_has_one_attack(self):
        midi = self.midi([self.note(0, 1), self.note(1, 1, True)])
        self.assertTrue(midi.startswith(b'MThd'))
        self.assertEqual(1, midi.count(b'\x90\x3c\x50'))

    def test_untied_repeat_rearticulates(self):
        midi = self.midi([self.note(0, 1), self.note(1, 1)])
        self.assertEqual(2, midi.count(b'\x90\x3c\x50'))

    def test_short_attack_can_become_a_held_tie_across_other_notes(self):
        accompaniment = self.note(1, .5)
        accompaniment['midi'] = 64
        midi = self.midi([self.note(0, .5), self.note(.5, 2.5, True),
                          accompaniment, self.note(3, 1, True)])
        self.assertEqual(1, midi.count(b'\x90\x3c\x50'))
        self.assertEqual(1, midi.count(b'\x80\x3c\x00'))
        self.assertIn(b'\x90\x40\x50', midi)

    def test_overlapping_unison_does_not_cut_off_another_voice(self):
        midi = self.midi([self.note(0, 3), self.note(1, 1, staff=1)])
        self.assertIn(b'\x90\x3c\x50', midi)
        self.assertIn(b'\x91\x3c\x50', midi)
        self.assertIn(b'\x81\x3c\x00', midi)


if __name__ == '__main__':
    unittest.main()
