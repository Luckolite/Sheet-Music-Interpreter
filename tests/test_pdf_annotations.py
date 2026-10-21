# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Native text extraction from an original, generated PDF; no score fixtures."""
import importlib.util
from contextlib import closing
from pathlib import Path
import tempfile
import unittest
from sheet_interpreter.pdf_annotations import tab_words, tuning_header


def fixture(stream=None):
    if stream is None:
        stream = b'BT /F1 12 Tf 30 370 Td (Tuning : E A D G B E) Tj ET\nBT /F1 14 Tf 100 280 Td (12) Tj ET\nBT /F1 14 Tf 170 280 Td (1) Tj ET\nBT /F1 14 Tf 184 280 Td (4) Tj ET'
    objects = [b'<< /Type /Catalog /Pages 2 0 R >>', b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>', b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 500 400] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>', b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>', b'<< /Length ' + str(len(stream)).encode() + b' >>\nstream\n' + stream + b'\nendstream']
    out = bytearray(b'%PDF-1.4\n'); offsets = [0]
    for i, obj in enumerate(objects, 1):
        offsets.append(len(out)); out.extend(f'{i} 0 obj\n'.encode() + obj + b'\nendobj\n')
    xref = len(out); out.extend(f'xref\n0 {len(offsets)}\n0000000000 65535 f \n'.encode())
    for offset in offsets[1:]: out.extend(f'{offset:010} 00000 n \n'.encode())
    out.extend(f'trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n'.encode())
    return out


@unittest.skipUnless(importlib.util.find_spec('pypdfium2'), 'optional PDF dependency')
class PdfTextTest(unittest.TestCase):
    def test_vertical_chord_digits_never_form_a_tall_compound_fret(self):
        import pypdfium2 as pdfium
        stream=b'BT /F1 12 Tf 96 256 Td (5) Tj ET\nBT /F1 12 Tf 96 156 Td (5) Tj ET'
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'original-chord.pdf';path.write_bytes(fixture(stream))
            with closing(pdfium.PdfDocument(path)) as doc:
                page=doc[0]
                try:
                    words=tab_words(page)
                    self.assertEqual(2,sum(w['text']=='5' for w in words))
                    self.assertFalse(any(w['text']=='55' for w in words))
                finally:page.close()
    def test_header_and_normalized_fret_boxes(self):
        import pypdfium2 as pdfium
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'synthetic.pdf'; path.write_bytes(fixture())
            with closing(pdfium.PdfDocument(path)) as doc:
                page = doc[0]
                try:
                    header = tuning_header(page)
                    self.assertEqual(header, 'Tuning : E A D G B E')
                    words = tab_words(page, header)
                    fret = next(w for w in words if w['text'] == '12')
                    self.assertAlmostEqual(fret['left'], .2, delta=.01)
                    self.assertTrue(.25 < fret['top'] < fret['bottom'] < .32)
                    self.assertTrue(any(w['text'] == '1' and w['left'] > .33 for w in words))
                    self.assertTrue(any(w['text'] == '4' and w['left'] > .36 for w in words))
                    self.assertEqual(words[-1]['text'], header)
                finally:
                    page.close()

    def test_no_injected_header_without_document_metadata(self):
        import pypdfium2 as pdfium
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'synthetic.pdf'; path.write_bytes(fixture())
            with closing(pdfium.PdfDocument(path)) as doc:
                page = doc[0]
                try:
                    self.assertFalse(any(w['text'].startswith('Tuning') for w in tab_words(page)))
                finally:
                    page.close()
