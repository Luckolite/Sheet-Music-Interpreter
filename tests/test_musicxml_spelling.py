# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original scalar heads: enharmonic spelling, auxiliary identity and clock invariants."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml
from sheet_interpreter.spelling_export import retained


def document(diatonic=29, accidental=-1, midi=61, shift=0, bottom=30):
    head = dict(measureIndex=0, staffIndex=0, staffCount=1, clefBottomDiatonic=bottom,
                staffStep=diatonic-bottom, writtenAccidental=accidental, octaveShift=shift,
                articulations=32 | 1024)
    event = dict(measureIndex=0, staffIndex=0, staffCount=1, midi=midi,
                 startBeat=0, durationBeats=1)
    return dict(pages=[dict(measureBeats=[4], events=[event], score=dict(notes=[head]))])


class MusicXmlSpellingTests(unittest.TestCase):
    def export(self, doc):
        before = copy.deepcopy(doc)
        with tempfile.TemporaryDirectory(prefix='original-spelling-') as folder:
            path = Path(folder)/'score.musicxml'
            write_musicxml(doc, path)
            root = ET.parse(path).getroot()
        self.assertEqual(before, doc)
        return root

    def pitch(self, doc):
        pitch = self.export(doc).find('.//pitch')
        return pitch.findtext('step'), int(pitch.findtext('alter', '0')), int(pitch.findtext('octave'))

    def test_flat_principal_preserves_diatonic_ornament_neighbor(self):
        root = self.export(document())
        self.assertEqual('D', root.findtext('.//pitch/step'))
        self.assertEqual('-1', root.findtext('.//pitch/alter'))
        self.assertEqual('natural', root.findtext('.//accidental-mark'))
        # An upper natural belongs to E above Db, not D above an invented C#.
        self.assertEqual('10080', root.findtext('.//note[pitch]/duration'))

    def test_double_accidentals_and_octave_crossings(self):
        for d, a, midi, expected in [(28,-1,59,('C',-1,4)), (27,1,60,('B',1,3)),
                                      (28,3,62,('C',2,4)), (29,-2,60,('D',-2,4))]:
            with self.subTest(expected=expected):
                self.assertEqual(expected, self.pitch(document(d,a,midi)))

    def test_explicit_natural_overrides_key(self):
        doc = document(31,0,65)
        doc['pages'][0]['score']['keyChanges'] = [dict(measureIndex=0,fifths=1)]
        self.assertEqual(('F',0,4), self.pitch(doc))

    def test_from_key_uses_resolved_bar_key(self):
        for key,d,midi,expected in [(1,31,66,('F',1,4)),(-1,34,70,('B',-1,4)),
                                    (7,27,60,('B',1,3)),(-7,28,59,('C',-1,4))]:
            doc = document(d,2,midi)
            doc['pages'][0]['score']['keyChanges'] = [dict(measureIndex=0,fifths=key)]
            self.assertEqual(expected, self.pitch(doc))

    def test_octave_shift_and_supported_clefs(self):
        for bottom in (18,22,24,30,37):
            self.assertEqual(('D',-1,5), self.pitch(document(29,-1,73,1,bottom)))

    def test_edited_pitch_uses_existing_fallback(self):
        self.assertEqual(('D',0,4), self.pitch(document(midi=62)))

    def test_later_measure_key_and_page_inheritance(self):
        doc = document(34,2,70)
        page = doc['pages'][0]
        page['measureBeats'] = [4,4]
        page['score']['keyChanges'] = [dict(measureIndex=1,fifths=-1)]
        page['events'][0].update(measureIndex=1,startBeat=4)
        page['score']['notes'][0]['measureIndex'] = 1
        following = document(34,2,70)['pages'][0]
        doc['pages'].append(following)
        self.assertEqual([('B','-1'),('B','-1')],
                         [(p.findtext('step'),p.findtext('alter'))
                          for p in self.export(doc).findall('.//pitch')])

    def test_tied_fragments_keep_spelling_and_exact_clock(self):
        doc = document()
        doc['pages'][0]['measureBeats'] = [4,4]
        doc['pages'][0]['events'][0]['durationBeats'] = 5
        root = self.export(doc)
        notes = root.findall('.//note[pitch]')
        self.assertEqual([('D','-1','4')]*2,
                         [(n.findtext('pitch/step'),n.findtext('pitch/alter'),
                           n.findtext('pitch/octave')) for n in notes])
        self.assertEqual(5*10080,sum(int(n.findtext('duration')) for n in notes))
        self.assertEqual(['start','stop'],[n.find('tie').get('type') for n in notes])
        self.assertEqual(1,len(root.findall('.//trill-mark')))

    def test_unproved_source_mapping_does_not_borrow_spelling(self):
        for mode in ('count','index','staff','measure','inferred','unknown','missing'):
            doc = document(); page = doc['pages'][0]
            if mode == 'count': page['score']['notes'].append(copy.deepcopy(page['score']['notes'][0]))
            elif mode == 'index': page['events'][0]['sourceNoteIndex'] = 1
            elif mode == 'staff': page['score']['notes'][0]['staffIndex'] = 1
            elif mode == 'measure': page['score']['notes'][0]['measureIndex'] = 1
            elif mode == 'inferred': page['events'][0]['clefInferred'] = True
            elif mode == 'unknown': page['score']['notes'][0]['clefBottomDiatonic'] = -1
            else: del page['score']['notes'][0]['staffStep']
            self.assertIsNone(retained(page,0,0),mode)
            self.assertEqual(('C',1,4), self.pitch(doc),mode)

    def test_invalid_source_types_are_rejected(self):
        for key,value in [('staffStep',True),('writtenAccidental',4),('octaveShift',0.5)]:
            doc = document(); doc['pages'][0]['score']['notes'][0][key] = value
            with self.assertRaises(ValueError): self.export(doc)

    def test_unpitched_identity_is_not_respelt(self):
        page = document()['pages'][0]
        page['events'][0] = dict(kind='UNPITCHED',displayStep='C',displayOctave=4)
        self.assertIsNone(retained(page,0,0))

    def test_legacy_head_without_source_geometry_keeps_fallback(self):
        doc = document(); doc['pages'][0]['score']['notes'] = []
        self.assertEqual(('C',1,4), self.pitch(doc))
