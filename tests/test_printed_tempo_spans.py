# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original synthetic page boundaries, without source score material."""
import copy
import math
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.printed_tempo_spans import OPEN, resolve_pages, has_open
from sheet_interpreter.performance import perform_expressions
from sheet_interpreter.musicxml import write_musicxml


def document():
    def proof(source):
        return dict(sourceId=source, pageIndex=0, visualX=0, staffIndex=0,
                    staffCount=1, printedText='faster')
    event = dict(eventId='faster', kind='UNRESOLVED_DIRECTION',
                 start=dict(measureIndex=0, quarterBeatOffset=0), end=None,
                 scope='UNRESOLVED', staffIndex=0, staffCount=1,
                 targetEventId='expression-column:0:0:1:0', strength='UNSPECIFIED',
                 qualifierText='', evidence=[proof('printed-expression-word'), proof(OPEN+'2')])
    def page(number, bpm, expressions):
        return dict(events=[dict(midi=60, startBeat=0, durationBeats=1,
                                 staffIndex=0, staffCount=1, tiedFromPrevious=False)],
                    measureBeats=[4, 4], totalBeats=8,
                    score=dict(firstMeasureNumber=number,
                               notes=[dict(measureIndex=0, positionInMeasure=0,
                                           staffIndex=0, staffCount=1)],
                               tempoChanges=[dict(measureIndex=0, positionInMeasure=0, bpm=bpm)],
                               expressiveEvents=expressions))
    return dict(pages=[page(1, 100, [event]), page(3, 150, [])])


class PrintedTempoSpanTests(unittest.TestCase):
    def test_document_clock_reaches_written_target_without_mutation(self):
        doc = document()
        before = copy.deepcopy(doc)
        performed = perform_expressions(doc, 100)
        expected = 60*8/50*math.log(1.5) + 60*8/150
        self.assertAlmostEqual(expected, performed['expressivePerformance'][0]['durationSeconds'])
        self.assertEqual(before, doc)

    def test_excluded_endpoint_does_not_invent_acceleration(self):
        doc = document()
        doc['pages'] = doc['pages'][:1]
        performed = perform_expressions(doc, 100)
        self.assertAlmostEqual(4.8, performed['expressivePerformance'][0]['durationSeconds'])
        self.assertTrue(has_open(resolve_pages(doc['pages'])[0]['score']['expressiveEvents'][0]))

    def test_conflicting_current_mark_is_not_order_dependent(self):
        for values in ((100, 110), (110, 100)):
            doc = document()
            doc['pages'][0]['score']['tempoChanges'] = [
                dict(measureIndex=0, positionInMeasure=0, bpm=bpm) for bpm in values]
            event = resolve_pages(doc['pages'])[0]['score']['expressiveEvents'][0]
            self.assertEqual('UNRESOLVED_DIRECTION', event['kind'])

    def test_xml_dashes_end_on_next_page_opening(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'synthetic.musicxml'
            write_musicxml(document(), path)
            root = ET.parse(path).getroot()
        found = []
        for measure in root.findall('part/measure'):
            for direction in measure.findall('direction'):
                dash = direction.find('direction-type/dashes')
                if dash is not None:
                    found.append((measure.attrib['number'], dash.attrib['type'],
                                  dash.attrib['number'], direction.findtext('offset')))
        self.assertEqual([('1', 'start', '1', '0'), ('3', 'stop', '1', '0')], found)
