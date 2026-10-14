# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original logical note columns; no score images or external source material."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml


def document(rows):
    notes, events = [], []
    for start, duration, midi, flags in rows:
        head = dict(measureIndex=int(start//4), staffIndex=0, staffCount=1)
        notes.append(dict(head, articulations=flags))
        events.append(dict(head, midi=midi, startBeat=start, durationBeats=duration))
    length = max((s+d for s,d,_,_ in rows), default=4)
    return dict(pages=[dict(measureBeats=[4]*max(1,int((length+3.999)//4)),
                           events=events, score=dict(notes=notes))])


class ArticulationExportTests(unittest.TestCase):
    def export(self, doc):
        before = copy.deepcopy(doc)
        with tempfile.TemporaryDirectory(prefix='original-articulation-export-') as folder:
            path = Path(folder)/'score.musicxml'
            write_musicxml(doc, path)
            root = ET.parse(path).getroot()
        self.assertEqual(before, doc)
        return root

    def marks(self, root):
        return [n.tag for n in root.findall('.//notations/articulations/*')]

    def test_all_five_marks_survive_with_marcato_orientation(self):
        root = self.export(document([(0,1,60,31)]))
        self.assertEqual(['accent','strong-accent','staccato','tenuto','staccatissimo'], self.marks(root))
        self.assertEqual('up', root.find('.//strong-accent').get('type'))

    def test_cross_bar_tied_fragment_does_not_repeat_attack(self):
        root = self.export(document([(3,2,60,5)]))
        self.assertEqual(['accent','staccato'], self.marks(root))
        self.assertEqual(2, len(root.findall('.//tied')))
        self.assertEqual([], root.findall('part/measure[2]/note/notations/articulations'))

    def test_common_chord_flags_emit_once_and_differences_remain(self):
        root = self.export(document([(0,1,60,5),(0,1,64,9)]))
        self.assertEqual(['accent','staccato','tenuto'], self.marks(root))
        self.assertEqual(1, len(root.findall('.//chord')))

    def test_distinct_voices_keep_their_own_marks(self):
        root = self.export(document([(0,2,60,1),(0,1,64,1)]))
        self.assertEqual(['accent','accent'], self.marks(root))
        self.assertEqual({'1','2'}, {n.findtext('voice') for n in root.findall('.//note')})

    def test_partial_source_or_reordered_identity_is_not_borrowed(self):
        for field in ('count','sourceNoteIndex','measureIndex','staffIndex','staffCount'):
            doc = document([(0,1,60,1)])
            if field == 'count': doc['pages'][0]['score']['notes'] = []
            else: doc['pages'][0]['events'][0][field] = 3
            self.assertEqual([], self.marks(self.export(doc)))

    def test_legacy_events_without_source_have_no_invented_marks(self):
        doc = document([(0,1,60,1)]); doc['pages'][0]['score'] = {}
        self.assertEqual([], self.marks(self.export(doc)))

    def test_ornament_bits_are_not_reinterpreted_as_basic_marks(self):
        self.assertEqual(['accent'], self.marks(self.export(document([(0,1,60,1|(1<<15)|(1<<24))]))))

    def test_invalid_source_flags_refused(self):
        for flags in (True, -1, 1.5, '1', 1<<32):
            with self.assertRaisesRegex(ValueError, 'articulation flags'):
                self.export(document([(0,1,60,flags)]))

    def test_unpitched_head_retains_written_mark(self):
        doc = document([(0,1,60,4)]); event = doc['pages'][0]['events'][0]
        event.pop('midi'); event.update(kind='UNPITCHED', displayStep='C', displayOctave=5, sourceNoteIndex=0)
        root = self.export(doc)
        self.assertEqual(['staccato'], self.marks(root))
        self.assertIsNotNone(root.find('.//unpitched'))

    def test_pitches_voices_ties_and_clocks_are_unchanged(self):
        doc = document([(0,4,48,9),(0,1,60,5),(3,2,64,1),(4,1,67,16)])
        marked = self.export(doc)
        for head in doc['pages'][0]['score']['notes']: head['articulations'] = 0
        plain = self.export(doc)
        for parent in marked.iter():
            for child in list(parent):
                if child.tag == 'articulations': parent.remove(child)
        for parent in marked.iter():
            for child in list(parent):
                if child.tag == 'notations' and not len(child): parent.remove(child)
        # Ignore indentation whitespace introduced by pretty printing.
        def shape(n): return n.tag, n.attrib, (n.text or '').strip(), [shape(c) for c in n]
        self.assertEqual(shape(plain), shape(marked))
