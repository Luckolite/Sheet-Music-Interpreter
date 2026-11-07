# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from sheet_music_recognition.ocr import tempo_numbers


class TempoAnnotationTests(unittest.TestCase):
    def tokens(self, text):
        return tempo_numbers([dict(text=text, left=.1, right=.9, top=.1, bottom=.15)])

    def test_parenthesized_tempo_keeps_digit_bounds(self):
        text = 'Gently (q = 96)'
        token, = self.tokens(text)
        self.assertEqual(96, token['value'])
        self.assertAlmostEqual(.1 + .8 * text.index('96') / len(text), token['left'])
        self.assertAlmostEqual(.1 + .8 * (text.index('96') + 2) / len(text), token['right'])

    def test_bracket_and_whitespace_are_not_part_of_digits(self):
        self.assertEqual(108, self.tokens('[q = 108]  ')[0]['value'])

    def test_unparenthesized_tempo_is_preserved(self):
        self.assertEqual(120, self.tokens('q = 120')[0]['value'])

    def test_unrelated_text_and_invalid_tempos_stay_rejected(self):
        for text in ['96', 'measure 96)', 'q = 900)', 'q = 96 notes', 'q = 12)']:
            self.assertEqual([], self.tokens(text))
