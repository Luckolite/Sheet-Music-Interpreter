# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from sheet_music_recognition.ocr import tempo_numbers


class TempoChordSuffixTest(unittest.TestCase):
    def token(self, text):
        return tempo_numbers([dict(text=text,left=.1,right=.7,top=.1,bottom=.15)])

    def test_major_triangle_chord_does_not_swallow_tempo(self):
        for chord in ('DΔ7','D∆7','Dmaj7'):
            self.assertEqual(104,self.token('q = 104 '+chord)[0]['value'])

    def test_minor_suspended_and_slash_chords(self):
        for chord in ('F#m7','Bbsus4','C/E','A♭7','Gø7'):
            self.assertEqual(88,self.token('q = 88 '+chord)[0]['value'])

    def test_digit_bounds_and_direction_anchor_exclude_chord(self):
        text='q = 104 Dmaj7';t,=self.token(text)
        self.assertAlmostEqual(.1+.6*text.index('104')/len(text),t['left'])
        self.assertAlmostEqual(.1+.6*(text.index('104')+3)/len(text),t['right'])
        self.assertEqual(.1,t['annotationLeft'])

    def test_unrelated_prose_and_fraction_are_not_tempos(self):
        for text in ('q = 88 notes','q = 88 And','q = 88/96','q = 88 D lyrics','q = 1000 C'):
            self.assertEqual([],self.token(text))

    def test_original_parenthesized_and_plain_tempos(self):
        for text in ('q = 88','(q = 88)','[q = 88]   '):
            self.assertEqual(88,self.token(text)[0]['value'])

    def test_input_words_are_not_modified(self):
        word=dict(text='q = 104 Dmaj7',left=.1,right=.7,top=.1,bottom=.15);before=word.copy()
        tempo_numbers([word]);self.assertEqual(before,word)
