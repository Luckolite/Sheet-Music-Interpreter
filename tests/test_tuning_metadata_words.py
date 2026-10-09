# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from unittest.mock import Mock
from sheet_interpreter.pdf_annotations import tab_words, tuning_words


class TuningMetadataWordsTests(unittest.TestCase):
    def test_document_header_uses_one_canonical_metadata_box(self):
        header = 'Tuning : D A D G B E'
        expected = [dict(text=header, left=.01, top=.001, right=.9, bottom=.009)]
        self.assertEqual(expected, tuning_words(header))
        first = tuning_words(header)
        first[0]['text'] = 'changed caller copy'
        self.assertEqual(expected, tuning_words(header))

    def test_empty_header_never_invents_tuning(self):
        self.assertEqual([], tuning_words(''))

    def test_normal_tab_words_reuses_the_canonical_document_metadata(self):
        page = Mock()
        textpage = page.get_textpage.return_value
        textpage.get_text_range.return_value = ''
        page.get_width.return_value = 500
        page.get_height.return_value = 400
        header = 'Tuning : D A D G B E'
        self.assertEqual(tuning_words(header), tab_words(page, header))
        textpage.close.assert_called_once_with()

    def test_normal_page_without_document_header_stays_empty(self):
        page = Mock()
        textpage = page.get_textpage.return_value
        textpage.get_text_range.return_value = ''
        page.get_width.return_value = 500
        page.get_height.return_value = 400
        self.assertEqual([], tab_words(page))
        textpage.close.assert_called_once_with()
