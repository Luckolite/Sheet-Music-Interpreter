# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original silent two-staff captions; no source score, pixels or OCR coordinates."""
import copy
import struct
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

from sheet_interpreter.musicxml import write_musicxml, DIVISIONS
from tests.test_full_measure_rests import document, rest
from tests.test_expressive_performance import expression


def caption_document(beats=3, text='let pedal ring'):
    rows = [rest(staff=0, count=2), rest(staff=1, count=2)]
    doc = document(beats=(beats,), meter=(beats, 4), rests=rows)
    bits = struct.unpack('>i', struct.pack('>f', .5))[0]
    mark = expression('UNRESOLVED_DIRECTION', scope='UNRESOLVED',
                      target=f'expression-rest-column:0:1:2:{bits}',
                      source='printed-expression-word', staff=1)
    mark.update(start=None, end=None)
    mark['evidence'][0]['printedText'] = text
    doc['pages'][0]['score']['expressiveEvents'] = [mark]
    return doc


def export(doc):
    before = copy.deepcopy(doc)
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / 'original.musicxml'
        write_musicxml(doc, path)
        result = ET.parse(path).getroot()
    if before != doc:
        raise AssertionError('Neutral caption export mutated the source')
    return result


class NeutralRestCaptionExportTests(unittest.TestCase):
    def test_literal_caption_is_below_its_bass_staff_without_pedal_action(self):
        root = export(caption_document())
        directions = [d for d in root.findall('.//direction') if d.find('direction-type/words') is not None]
        self.assertEqual(1, len(directions))
        self.assertEqual('let pedal ring', directions[0].findtext('direction-type/words'))
        self.assertEqual('below', directions[0].get('placement'))
        self.assertEqual(['let pedal ring'], [n.text for n in root.findall("part[@id='P2']//words")])
        self.assertEqual([], root.findall("part[@id='P1']//words"))
        self.assertIsNone(directions[0].find('staff'))
        self.assertEqual('0', directions[0].findtext('offset'))
        self.assertEqual([], root.findall('.//pedal'))
        self.assertFalse(any('damper-pedal' in s.attrib for s in root.findall('.//sound')))
        notes = root.findall('.//note')
        self.assertEqual(2, len(notes))
        self.assertTrue(all(n.find('rest') is not None for n in notes))
        self.assertEqual([str(3 * DIVISIONS)] * 2, [n.findtext('duration') for n in notes])

    def test_complete_case_and_optional_article_preserve_original_literal(self):
        for text in ('Let the pedal ring!', '  LET PEDAL RING.  '):
            self.assertEqual([text], [n.text for n in export(caption_document(text=text)).findall('.//words')])
        doc = caption_document()
        mark = doc['pages'][0]['score']['expressiveEvents'][0]
        mark['evidence'].append(copy.deepcopy(mark['evidence'][0]))
        self.assertEqual(['let pedal ring'], [n.text for n in export(doc).findall('.//words')])

    def test_pickup_span_stays_literal_one_beat(self):
        root = export(caption_document(beats=1))
        self.assertEqual(['let pedal ring'], [n.text for n in root.findall('.//words')])
        self.assertEqual([str(DIVISIONS)] * 2, [n.findtext('duration') for n in root.findall('.//note')])

    def test_sounding_owner_cannot_be_rebound_as_a_silent_caption(self):
        doc = caption_document()
        doc['pages'][0]['score']['notes'] = [dict(measureIndex=0, staffIndex=1, staffCount=2)]
        self.assertEqual([], export(doc).findall('.//words'))

    def test_duplicate_or_literal_duration_rest_cannot_supply_caption(self):
        for duplicate in (False, True):
            doc = caption_document()
            rows = doc['pages'][0]['score']['rests']
            if duplicate:
                rows.append(copy.deepcopy(rows[1]))
            else:
                rows[1]['kind'] = 'LITERAL'
            self.assertEqual([], export(doc).findall('.//words'))

    def test_wrong_identity_or_evidence_staff_refuses(self):
        for field, value in (('targetEventId', 'expression-rest-column:0:1:2:2147483648'),
                             ('targetEventId', 'expression-rest-column:9:1:2:1056964608'),
                             ('staffIndex', 0)):
            doc = caption_document()
            doc['pages'][0]['score']['expressiveEvents'][0][field] = value
            self.assertEqual([], export(doc).findall('.//words'))

    def test_unknown_prose_is_not_admitted_on_a_full_rest(self):
        for text in ('do not let pedal ring', 'let pedal ring after the page turn', ''):
            self.assertEqual([], export(caption_document(text=text)).findall('.//words'))

    def test_foreign_evidence_or_noninteger_staff_cannot_claim_the_rest(self):
        for source, staff in (('manual-caption', 1), ('printed-expression-word', True)):
            doc = caption_document()
            mark = doc['pages'][0]['score']['expressiveEvents'][0]
            mark['evidence'][0]['sourceId'] = source
            mark['staffIndex'] = staff
            self.assertEqual([], export(doc).findall('.//words'))
        doc = caption_document()
        mark = doc['pages'][0]['score']['expressiveEvents'][0]
        foreign = copy.deepcopy(mark['evidence'][0])
        foreign['sourceId'] = 'manual-caption'
        mark['evidence'].append(foreign)
        self.assertEqual([], export(doc).findall('.//words'))

    def test_a_different_physical_staff_count_cannot_supply_the_caption(self):
        doc = caption_document()
        doc['pages'][0]['score']['rests'][1]['staffCount'] = 3
        self.assertEqual([], export(doc).findall('.//words'))

    def test_literal_caption_has_no_midi_or_silent_timing_effect(self):
        from sheet_interpreter.audio import _sample_events
        doc = caption_document()
        original = copy.deepcopy(doc)
        plain = copy.deepcopy(doc)
        plain['pages'][0]['score']['expressiveEvents'] = []
        self.assertEqual(_sample_events(plain, 120), _sample_events(doc, 120))
        self.assertEqual(original, doc)
