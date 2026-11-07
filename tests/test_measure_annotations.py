# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from sheet_music_recognition.ocr import measure_numbers

class MeasureAnnotationsTest(unittest.TestCase):
    def word(self,text):
        return dict(text=text,left=.08,top=.3,right=.1,bottom=.32)
    def test_isolated_number_keeps_geometry(self):
        self.assertEqual(measure_numbers([self.word(' 79 ')]),
                         [dict(value=79,left=.08,top=.3,right=.1,bottom=.32)])
    def test_non_measure_text_is_not_promoted(self):
        self.assertEqual(measure_numbers([self.word(t) for t in
            ['q = 120','3/4','12a','-1','0','10000','１２','3.5']]),[])
    def test_candidates_still_require_decoder_geometry(self):
        self.assertEqual([n['value'] for n in measure_numbers([self.word('8'),self.word('23')])],[8,23])

if __name__=='__main__':unittest.main()
