# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from sheet_music_recognition.ocr import page_annotations

class SeparateOcrChannelsTest(unittest.TestCase):
    def word(self,text):return dict(text=text,left=.1,top=.1,right=.2,bottom=.2)
    def test_native_number_cannot_hide_octave_or_navigation(self):
        words=[self.word('8vb'),self.word('D.C. al Fine')];tabs=[self.word('12')]
        a=page_annotations(words,tabs)
        self.assertEqual(words,a['words']);self.assertEqual(tabs,a['tabWords'])
    def test_tempo_candidates_come_from_musical_ocr(self):
        a=page_annotations([self.word('q = 104 Dmaj7')],[self.word('88')])
        self.assertEqual([104],[x['value'] for x in a['tempoNumbers']])
        self.assertEqual([],a['measureNumbers'])
    def test_empty_native_channel_retains_directions(self):
        a=page_annotations([self.word('8va')],[])
        self.assertEqual('8va',a['words'][0]['text']);self.assertEqual([],a['tabWords'])
    def test_lists_are_independent_of_inputs(self):
        words=[self.word('8vb')];tabs=[self.word('3')];a=page_annotations(words,tabs)
        a['words'].clear();a['tabWords'].clear()
        self.assertEqual(1,len(words));self.assertEqual(1,len(tabs))
