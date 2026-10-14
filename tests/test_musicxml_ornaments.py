# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original ornament-bearing heads; no private scans or performed-note expansion."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml


def document(flags, duration=1):
    heads=[dict(measureIndex=0,staffIndex=0,staffCount=1,articulations=f) for f in flags]
    events=[dict(measureIndex=0,staffIndex=0,staffCount=1,midi=60+4*i,
                 startBeat=0,durationBeats=duration) for i in range(len(flags))]
    return dict(pages=[dict(measureBeats=[4]*(2 if duration>4 else 1),events=events,score=dict(notes=heads))])


class MusicXmlOrnamentTests(unittest.TestCase):
    def export(self, doc):
        before=copy.deepcopy(doc)
        with tempfile.TemporaryDirectory(prefix='original-ornament-export-') as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(doc,path);root=ET.parse(path).getroot()
        self.assertEqual(before,doc)
        return root

    def test_five_supported_ornament_types_survive(self):
        for kind,tag in [(1,'trill-mark'),(2,'turn'),(3,'inverted-turn'),(4,'inverted-mordent'),(5,'mordent')]:
            root=self.export(document([kind<<5]))
            self.assertEqual([tag],[n.tag for n in root.findall('.//ornaments/*')])

    def test_delayed_turn_does_not_become_immediate(self):
        for kind,tag in [(2,'delayed-turn'),(3,'delayed-inverted-turn')]:
            root=self.export(document([(kind<<5)|(1<<8)]))
            self.assertEqual([tag],[n.tag for n in root.findall('.//ornaments/*')])

    def test_auxiliary_accidentals_stay_with_ornament_in_source_order(self):
        root=self.export(document([(2<<5)|(1<<9)|(3<<11)|(2<<16)]))
        marks=root.findall('.//accidental-mark')
        self.assertEqual([('above','flat'),('below','sharp')],[(m.get('placement'),m.text) for m in marks])
        self.assertEqual(['turn','accidental-mark','accidental-mark','tremolo'],[n.tag for n in root.find('.//ornaments')])

    def test_explicit_natural_is_not_lost(self):
        root=self.export(document([(1<<5)|(2<<9)]))
        self.assertEqual('natural',root.findtext('.//accidental-mark'))

    def test_single_tremolo_keeps_printed_beam_count(self):
        for beams in (1,2,3,4):
            root=self.export(document([beams<<16],duration=2))
            tremolo=root.find('.//tremolo')
            self.assertEqual('single',tremolo.get('type'))
            self.assertEqual(str(beams),tremolo.text)
            self.assertEqual(1,len(root.findall('.//note[pitch]')))

    def test_unsupported_beam_values_do_not_create_glyph(self):
        for beams in (5,6,7):
            self.assertEqual([],self.export(document([beams<<16])).findall('.//ornaments'))

    def test_articulation_grace_and_guitar_bits_do_not_invent_ornament(self):
        from sheet_interpreter.ornament_export import retained
        self.assertEqual(0,retained(document([1|(1<<15)|(1<<24)])['pages'][0],0))
        root=self.export(document([1|(1<<24)]))
        self.assertEqual([],root.findall('.//ornaments'))
        self.assertEqual(1,len(root.findall('.//accent')))

    def test_differing_chord_ornaments_do_not_merge_encoded_type_bits(self):
        root=self.export(document([1<<5,3<<5]))
        self.assertEqual(['trill-mark','inverted-turn'],[n.tag for n in root.findall('.//ornaments/*')])
        self.assertEqual(1,len(root.findall('.//chord')))

    def test_tied_fragments_do_not_repeat_source_ornament(self):
        root=self.export(document([2<<5],duration=6.5))
        self.assertEqual(1,len(root.findall('.//turn')))
        self.assertEqual(3,len(root.findall('.//note[pitch]')))

    def test_source_context_mismatch_cannot_supply_ornament(self):
        doc=document([1<<5]);doc['pages'][0]['events'][0]['sourceNoteIndex']=7
        self.assertEqual([],self.export(doc).findall('.//ornaments'))

    def test_unpitched_source_cannot_borrow_tonal_ornament_semantics(self):
        doc=document([1<<5]);event=doc['pages'][0]['events'][0]
        event.pop('midi');event.update(kind='UNPITCHED',displayStep='C',displayOctave=5)
        with self.assertRaisesRegex(ValueError,'Unpitched source ornaments'):
            self.export(doc)

    def test_written_heads_ties_and_clocks_remain_exact(self):
        doc=document([(2<<5)|(2<<9)|(2<<16)],duration=6.5)
        marked=self.export(doc);doc['pages'][0]['score']['notes'][0]['articulations']=0;plain=self.export(doc)
        for parent in marked.iter():
            for child in list(parent):
                if child.tag=='ornaments':parent.remove(child)
        for parent in marked.iter():
            for child in list(parent):
                if child.tag=='notations' and not len(child):parent.remove(child)
        def shape(n):return n.tag,n.attrib,(n.text or '').strip(),[shape(c) for c in n]
        self.assertEqual(shape(plain),shape(marked))
