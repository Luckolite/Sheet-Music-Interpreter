# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original typed silent voices; no source image, OCR capture or score-specific geometry."""
import copy
import math
from pathlib import Path
import struct
import tempfile
import unittest
import xml.etree.ElementTree as ET

from sheet_interpreter.musicxml import DIVISIONS, write_musicxml
from sheet_interpreter.performance import _rest_column_span, _rest_span, perform_expressions


def rest(m=0, position=.5, staff=0, count=1, duration=4, kind='FULL_MEASURE'):
    return dict(measureIndex=m, positionInMeasure=position, pageY=.3, staffHeight=.04,
                staffIndex=staff, staffCount=count, durationBeats=duration, kind=kind)


def owned_fermata(row):
    bits = struct.unpack('>i', struct.pack('>f', row['positionInMeasure']))[0]
    return dict(eventId='original-rest-hold', kind='FERMATA', start=None, end=None,
                scope='UNRESOLVED', staffIndex=row['staffIndex'], staffCount=row['staffCount'],
                targetEventId=f"printed-rest:{row['measureIndex']}:{row['staffIndex']}:{row['staffCount']}:{bits}",
                strength='UNSPECIFIED', qualifierText='',
                evidence=[dict(sourceId='fermata-rest-raw-ink', printedText='', pageIndex=0, visualX=.5,
                               staffIndex=row['staffIndex'], staffCount=row['staffCount'], confidence=.99)])


def document(beats=(3,), meter=(3, 4), rests=None, moving=False, fermata=False):
    rows = [rest()] if rests is None else rests
    events, raw = [], []
    if moving:
        for index in range(int(beats[0])):
            events.append(dict(midi=60+index, measureIndex=0, staffIndex=0, staffCount=1,
                               startBeat=index, durationBeats=1, durationFallback=False,
                               tiedFromPrevious=False))
            raw.append(dict(measureIndex=0, positionInMeasure=.15+.25*index,
                            staffIndex=0, staffCount=1, articulations=0))
    return dict(inputName='Original typed silent voices', initialMeter=list(meter), initialBpm=120,
                pages=[dict(measureBeats=list(beats), totalBeats=sum(beats), events=events,
                            score=dict(notes=raw, rests=rows, meterChanges=[], keyChanges=[],
                                       tempoChanges=[], expressiveEvents=[owned_fermata(rows[0])] if fermata else []))])


def export(doc):
    before = copy.deepcopy(doc)
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory)/'original.musicxml'
        write_musicxml(doc, path)
        result = ET.parse(path).getroot()
    if before != doc:
        raise AssertionError('Export mutated the input')
    return result


class FullMeasureRestTests(unittest.TestCase):
    def test_full_span_uses_actual_three_beats_for_both_meters(self):
        for meter in ((3, 4), (6, 8)):
            doc = document(meter=meter)
            self.assertEqual((dict(measureIndex=0, quarterBeatOffset=0),
                              dict(measureIndex=0, quarterBeatOffset=3)),
                             _rest_span(owned_fermata(doc['pages'][0]['score']['rests'][0]), doc['pages'][0]))

    def test_full_span_supports_five_and_eight_beats(self):
        for beats in (5, 8):
            page = document(beats=(beats,), meter=(beats, 4))['pages'][0]
            self.assertEqual(beats, _rest_column_span(page, 0, 0, 1, .5, .018)[1]['quarterBeatOffset'])

    def test_pickup_is_actual_span_not_nominal_meter_or_raw_four(self):
        page = document(beats=(1,), meter=(4, 4))['pages'][0]
        self.assertEqual((dict(measureIndex=0, quarterBeatOffset=0), dict(measureIndex=0, quarterBeatOffset=1)),
                         _rest_column_span(page, 0, 0, 1, .5, .018))

    def test_full_span_survives_parallel_note_columns_and_fallback(self):
        page = document(moving=True)['pages'][0]
        page['events'][1]['durationFallback'] = True
        self.assertEqual(3, _rest_column_span(page, 0, 0, 1, .5, .018)[1]['quarterBeatOffset'])

    def test_full_onset_is_zero_independent_of_optical_position(self):
        for position in (.15, .8):
            page = document(rests=[rest(position=position)])['pages'][0]
            self.assertEqual(0, _rest_column_span(page, 0, 0, 1, position, .018)[0]['quarterBeatOffset'])

    def test_full_does_not_fill_or_inflate_literal_gap_sum(self):
        page = document(beats=(4,), meter=(4, 4), rests=[rest(position=.2, kind='LITERAL'), rest()])['pages'][0]
        self.assertEqual((dict(measureIndex=0, quarterBeatOffset=0), dict(measureIndex=0, quarterBeatOffset=4)),
                         _rest_column_span(page, 0, 0, 1, .2, .018))

    def test_physical_staff_count_is_part_of_rest_ownership(self):
        page = document(rests=[rest(count=2), rest(count=3)])['pages'][0]
        for count in (2, 3):
            self.assertEqual(3, _rest_column_span(page, 0, 0, count, .5, .018)[1]['quarterBeatOffset'])
        self.assertIsNone(_rest_column_span(page, 0, 0, 1, .5, .018))

    def test_ambiguous_full_column_does_not_get_owned_fermata(self):
        page = document(rests=[rest(), rest()])['pages'][0]
        self.assertIsNone(_rest_column_span(page, 0, 0, 1, .5, .018))

    def test_unknown_or_null_kind_rejects_in_both_consumers(self):
        for kind in ('MEASURE', None, 1, True):
            doc = document(rests=[rest(kind=kind)])
            with self.assertRaises(ValueError):
                _rest_column_span(doc['pages'][0], 0, 0, 1, .5, .018)
            with self.assertRaises(ValueError):
                export(doc)

    def test_full_invalid_glyph_base_rejects(self):
        for duration in (3, 6, math.nan, '4', True):
            doc = document(rests=[rest(duration=duration)])
            with self.assertRaises(ValueError):
                _rest_column_span(doc['pages'][0], 0, 0, 1, .5, .018)
            with self.assertRaises(ValueError):
                export(doc)

    def test_expressive_entry_rejects_unknown_kind_without_fermata(self):
        for kind in ('UNKNOWN', None):
            with self.assertRaises(ValueError):
                perform_expressions(document(rests=[rest(kind=kind)]), 120)

    def test_full_unknown_or_invalid_span_rejects(self):
        for beats in (None, [], [0], [-1], [math.nan], [math.inf], [True]):
            doc = document()
            if beats is None:
                del doc['pages'][0]['measureBeats']
            else:
                doc['pages'][0]['measureBeats'] = beats
            with self.assertRaises(ValueError):
                _rest_column_span(doc['pages'][0], 0, 0, 1, .5, .018)
            with self.assertRaises(ValueError):
                export(doc)

    def test_small_positive_pickup_span_is_preserved_by_resolver_and_xml(self):
        doc = document(beats=(.0625,), meter=(4, 4))
        page = doc['pages'][0]
        self.assertEqual(.0625, _rest_span(owned_fermata(page['score']['rests'][0]), page)[1]['quarterBeatOffset'])
        root = export(doc)
        self.assertEqual(str(round(.0625*DIVISIONS)), root.findtext('part/measure/note/duration'))
        self.assertEqual('whole', root.findtext('part/measure/note/type'))
        self.assertEqual('yes', root.find('part/measure').get('implicit'))

    def test_full_invalid_owner_or_measure_rejects(self):
        for patch in (dict(measureIndex=-1), dict(measureIndex=1), dict(measureIndex=True),
                      dict(staffIndex=1, staffCount=1), dict(staffIndex=0, staffCount=0),
                      dict(staffCount=True), dict(positionInMeasure=math.nan)):
            row = dict(rest(), **patch)
            with self.assertRaises(ValueError):
                export(document(rests=[row]))

    def test_legacy_absent_kind_remains_literal(self):
        row = rest(kind='LITERAL'); del row['kind']
        page = document(beats=(4,), meter=(4, 4), rests=[row])['pages'][0]
        self.assertEqual(4, _rest_column_span(page, 0, 0, 1, .5, .018)[1]['quarterBeatOffset'])
        self.assertIsNone(_rest_column_span(dict(page, measureBeats=[3]), 0, 0, 1, .5, .018))
        root = export(dict(pages=[page], initialMeter=[4, 4]))
        self.assertEqual({}, root.find('part/measure/note/rest').attrib)

    def test_xml_whole_glyph_keeps_actual_three_five_and_eight_beat_durations(self):
        for meter, beats in (((3, 4), 3), ((6, 8), 3), ((5, 4), 5), ((8, 4), 8)):
            root = export(document(beats=(beats,), meter=meter))
            notes = root.findall('part/measure/note')
            self.assertEqual(1, len(notes))
            self.assertEqual({'measure': 'yes'}, notes[0].find('rest').attrib)
            self.assertEqual('whole', notes[0].findtext('type'))
            self.assertEqual([], notes[0].findall('dot'))
            self.assertEqual(str(beats*DIVISIONS), notes[0].findtext('duration'))

    def test_xml_meter_change_and_pickup_keep_written_meter_actual_span(self):
        doc = document(beats=(1, 3), meter=(4, 4), rests=[rest(), rest(m=1)])
        doc['pages'][0]['score']['meterChanges'] = [dict(measureIndex=1, numerator=6, denominator=8)]
        bars = export(doc).findall('part/measure')
        self.assertEqual(['yes', None], [bar.get('implicit') for bar in bars])
        self.assertEqual(['4', '6'], [bar.findtext('attributes/time/beats') for bar in bars])
        self.assertEqual(['4', '8'], [bar.findtext('attributes/time/beat-type') for bar in bars])
        self.assertEqual([str(DIVISIONS), str(3*DIVISIONS)], [bar.findtext('note/duration') for bar in bars])
        self.assertEqual(['whole', 'whole'], [bar.findtext('note/type') for bar in bars])

    def test_xml_rest_only_staffs_do_not_disappear(self):
        root = export(document(rests=[rest(staff=0, count=2), rest(staff=1, count=2)]))
        self.assertEqual(2, len(root.findall('part')))
        self.assertEqual(2, len(root.findall('.//rest[@measure="yes"]')))
        self.assertEqual(2, len(root.findall('.//note')))

    def test_xml_staff_count_identities_are_not_merged(self):
        root = export(document(rests=[rest(count=2), rest(count=3)]))
        self.assertEqual(2, len(root.findall('part')))
        self.assertEqual(2, len({part.get('id') for part in root.findall('part')}))
        self.assertEqual(2, len(root.findall('.//rest[@measure="yes"]')))

    def test_xml_parallel_event_on_other_staff_count_does_not_merge(self):
        doc = document(moving=True, rests=[rest(count=2)])
        for row in doc['pages'][0]['events']+doc['pages'][0]['score']['notes']:
            row['staffCount'] = 3
        root = export(doc)
        self.assertEqual(2, len(root.findall('part')))
        self.assertEqual(1, len(root.findall('part')[0].findall('.//rest[@measure="yes"]')))
        self.assertEqual(3, len(root.findall('part')[1].findall('.//pitch')))

    def test_xml_full_parallel_voice_has_own_fermata_and_no_note_fermata(self):
        root = export(document(moving=True, fermata=True))
        measure = root.find('part/measure')
        full = [n for n in measure.findall('note') if n.find('rest[@measure="yes"]') is not None]
        self.assertEqual(1, len(full))
        self.assertEqual('1001', full[0].findtext('voice'))
        self.assertIsNotNone(full[0].find('notations/fermata'))
        self.assertEqual([], [n for n in measure.findall('note') if n.find('pitch') is not None and n.find('notations/fermata') is not None])
        self.assertEqual(str(3*DIVISIONS), measure.findtext('backup/duration'))
        self.assertEqual([], measure.findall('direction/direction-type/words'))

    def test_xml_rest_only_full_fermata_has_single_voice(self):
        root = export(document(fermata=True))
        self.assertEqual(1, len(root.findall('.//note')))
        self.assertEqual(1, len(root.findall('.//fermata')))
        self.assertEqual([], root.findall('.//backup'))

    def test_xml_full_and_literal_other_bars_stay_distinct(self):
        root = export(document(beats=(3, 4), rests=[rest(), rest(m=1, kind='LITERAL')]))
        rests = root.findall('.//note/rest')
        self.assertEqual([{'measure': 'yes'}, {}], [row.attrib for row in rests])
        self.assertEqual(['whole', 'whole'], [row.text for row in root.findall('.//note/type')])

    def test_actual_expressive_policy_has_full_silent_hold_without_sound(self):
        doc = document(fermata=True)
        before = copy.deepcopy(doc)
        result = perform_expressions(doc, 120)
        self.assertIsNotNone(result)
        self.assertEqual([], result['pages'][0]['events'])
        policy = result['expressivePerformance'][0]
        self.assertEqual(3, policy['holds'][0]['beat'])
        self.assertEqual([], policy['holds'][0]['sustainedTargets'])
        self.assertEqual(3, policy['durationSeconds'])
        self.assertEqual(before, doc)

    def test_actual_parallel_full_hold_leaves_note_attacks_and_releases_written(self):
        result = perform_expressions(document(moving=True, fermata=True), 120)
        policy = result['expressivePerformance'][0]
        self.assertEqual(3, policy['holds'][0]['beat'])
        self.assertEqual([], policy['holds'][0]['sustainedTargets'])
        self.assertEqual([0, 1, 2], [n['startBeat'] for n in result['pages'][0]['events']])
        self.assertEqual([1, 1, 1], [n['durationBeats'] for n in result['pages'][0]['events']])
