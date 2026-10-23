# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml, DIVISIONS


def document():
    notes = [dict(measureIndex=0, positionInMeasure=p, staffIndex=0, staffCount=1,
                  staffStep=2*i, articulations=0) for i, p in enumerate((.17, .86))]
    events = [dict(measureIndex=0, staffIndex=0, staffCount=1, sourceNoteIndex=i,
                   midi=60+4*i, startBeat=0 if i == 0 else 3,
                   durationBeats=3 if i == 0 else 1, durationFallback=False) for i in range(2)]
    change = dict(measureIndex=0, positionInMeasure=.17, staffIndex=0, staffCount=1,
                  endMeasureIndex=0, endPosition=.86, decibels=0, direction=1)
    return dict(initialMeter=[4, 4], initialKeyFifths=0,
                pages=[dict(measureBeats=[4], events=events, score=dict(notes=notes, dynamicChanges=[change]))])


class HairpinExportTests(unittest.TestCase):
    def xml(self, doc):
        before = copy.deepcopy(doc)
        with tempfile.TemporaryDirectory(prefix='original-hairpin-') as folder:
            path = Path(folder)/'score.musicxml'
            write_musicxml(doc, path)
            root = ET.parse(path).getroot()
        self.assertEqual(before, doc)
        return [(p.get('id'), int(m.get('number')), w.get('type'), int(d.findtext('offset')), w.get('number'))
                for p in root.findall('part') for m in p.findall('measure') for d in m.findall('direction')
                if (w := d.find('direction-type/wedge')) is not None]

    def expected(self, stop=3, shape='crescendo'):
        return [('P1', 1, shape, 0, '1'), ('P1', 1, 'stop', stop*DIVISIONS, '1')]

    def test_uneven_owned_columns_use_written_clocks(self):
        self.assertEqual(self.expected(), self.xml(document()))

    def test_edited_written_clock_changes_stop(self):
        doc = document(); events = doc['pages'][0]['events']
        events[0]['durationBeats'] = 2
        events[1].update(startBeat=2, durationBeats=2)
        self.assertEqual(self.expected(2), self.xml(doc))

    def test_owned_attack_can_begin_a_hold_across_barline(self):
        doc = document(); page = doc['pages'][0]
        page['measureBeats'] = [4, 4]
        page['events'][1]['durationBeats'] = 5
        self.assertEqual(self.expected(), self.xml(doc))

    def test_diminuendo_shape_is_preserved(self):
        doc = document(); doc['pages'][0]['score']['dynamicChanges'][0]['direction'] = -1
        self.assertEqual(self.expected(shape='diminuendo'), self.xml(doc))

    def test_centered_whole_head_starts_zero_and_unknown_stop_keeps_fraction(self):
        doc = document(); page = doc['pages'][0]
        page['score']['notes'] = [dict(page['score']['notes'][0], positionInMeasure=.4)]
        page['events'] = [dict(page['events'][0], durationBeats=4)]
        page['score']['dynamicChanges'][0].update(positionInMeasure=.1, endPosition=.75)
        self.assertEqual(self.expected(), self.xml(doc))

    def test_partial_hold_does_not_invent_zero(self):
        doc = document(); page = doc['pages'][0]
        page['score']['notes'] = [dict(page['score']['notes'][0], positionInMeasure=.4)]
        page['events'] = [dict(page['events'][0], startBeat=1, durationBeats=3)]
        page['score']['dynamicChanges'][0].update(positionInMeasure=.125, endPosition=.75)
        self.assertEqual([('P1', 1, 'crescendo', DIVISIONS//2, '1'),
                          ('P1', 1, 'stop', 3*DIVISIONS, '1')], self.xml(doc))

    def whole_with_foreign_topology(self, foreign_position):
        doc = document(); page = doc['pages'][0]
        page['score']['notes'] = [dict(page['score']['notes'][0], positionInMeasure=.4)]
        page['events'] = [dict(page['events'][0], durationBeats=4)]
        page['score']['dynamicChanges'][0].update(positionInMeasure=.1, endPosition=.75)
        page['score']['notes'].append(dict(page['score']['notes'][0], staffCount=2,
                                          positionInMeasure=foreign_position))
        page['events'].append(dict(page['events'][0], staffCount=2, sourceNoteIndex=1,
                                   midi=64, startBeat=1, durationBeats=3))
        return doc

    def test_whole_hold_ignores_another_topology_at_same_head_column(self):
        self.assertEqual(self.expected(), self.xml(self.whole_with_foreign_topology(.4)))

    def test_whole_hold_ignores_another_topology_at_later_column(self):
        self.assertEqual(self.expected(), self.xml(self.whole_with_foreign_topology(.6)))

    def test_exact_column_mixed_topology_preserves_java_abstention(self):
        doc = document(); page = doc['pages'][0]
        page['score']['notes'].append(dict(page['score']['notes'][0], staffCount=2))
        page['events'].append(dict(page['events'][0], staffCount=2, sourceNoteIndex=2,
                                   midi=67, startBeat=1, durationBeats=3))
        self.assertEqual([], self.xml(doc))

    def test_full_bar_stop_stays_in_source_bar(self):
        doc = document(); page = doc['pages'][0]; page['measureBeats'] = [4, 4]
        page['score']['dynamicChanges'][0]['endPosition'] = 1
        self.assertEqual(self.expected(4), self.xml(doc))

    def test_following_bar_zero_stop_keeps_explicit_ownership(self):
        doc = document(); page = doc['pages'][0]; page['measureBeats'] = [4, 4]
        page['score']['dynamicChanges'][0].update(endMeasureIndex=1, endPosition=0)
        self.assertEqual([('P1', 1, 'crescendo', 0, '1'), ('P1', 2, 'stop', 0, '1')], self.xml(doc))

    def test_wrong_source_identity_abstains(self):
        doc = document(); doc['pages'][0]['events'][0]['sourceNoteIndex'] = 1
        self.assertEqual([], self.xml(doc))

    def test_estimated_stop_clock_abstains(self):
        doc = document(); doc['pages'][0]['events'][1]['durationFallback'] = True
        self.assertEqual([], self.xml(doc))

    def test_conflicting_exact_column_clocks_abstain(self):
        doc = document(); doc['pages'][0]['score']['notes'][1]['positionInMeasure'] = .17
        self.assertEqual([], self.xml(doc))

    def test_unowned_geometry_keeps_legacy_fraction(self):
        doc = document(); doc['pages'][0]['score']['dynamicChanges'][0]['endPosition'] = .75
        self.assertEqual(self.expected(), self.xml(doc))

    def test_duplicate_interval_is_emitted_once(self):
        doc = document(); doc['pages'][0]['score']['dynamicChanges'] *= 2
        self.assertEqual(self.expected(), self.xml(doc))

    def test_shared_interval_is_paired_on_each_existing_part(self):
        doc = document(); page = doc['pages'][0]
        for n in page['score']['notes']: n['staffCount'] = 2
        for e in page['events']: e['staffCount'] = 2
        page['score']['notes'] += [dict(n, staffIndex=1) for n in page['score']['notes'][:]]
        page['events'] += [dict(e, staffIndex=1, sourceNoteIndex=i+2, midi=e['midi']-12)
                           for i, e in enumerate(page['events'][:])]
        page['score']['dynamicChanges'][0].update(staffCount=2, sharedStaffs=True)
        self.assertEqual(self.expected()+[(p.replace('P1', 'P2'), m, s, t, n)
                                         for p, m, s, t, n in self.expected()], self.xml(doc))

    def test_overlapping_intervals_have_distinct_paired_numbers(self):
        doc = document(); page = doc['pages'][0]
        page['score']['dynamicChanges'].append(dict(page['score']['dynamicChanges'][0], direction=-1, endPosition=1))
        marks = self.xml(doc)
        self.assertEqual(['1', '1', '2', '2'], [m[-1] for m in marks])
        self.assertEqual(['crescendo', 'stop', 'diminuendo', 'stop'], [m[2] for m in marks])

    def test_malformed_scope_never_creates_a_wedge(self):
        for patch in (dict(direction=True), dict(staffCount=True), dict(endPosition=float('nan')),
                      dict(endMeasureIndex=10), dict(sharedTiming=1)):
            doc = document(); doc['pages'][0]['score']['dynamicChanges'][0].update(patch)
            self.assertEqual([], self.xml(doc))
