# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_music_recognition.musicxml import write_musicxml, DIVISIONS
from tests.test_expressive_performance import document, expression, fermata, rest_fermata_document


class MusicXmlExpressionTests(unittest.TestCase):
    def export(self, doc):
        original=copy.deepcopy(doc)
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'synthetic.musicxml'
            write_musicxml(doc,path)
            root=ET.parse(path).getroot()
        self.assertEqual(original,doc)
        return root

    def test_metric_dots_are_structured_without_absolute_bpm(self):
        mark=expression('METRIC_MODULATION',2);mark['qualifierText']='metric-pulse-v1:0.75:1.75'
        root=self.export(document([mark]));relation=[m for m in root.findall('.//metronome') if len(m.findall('beat-unit'))==2][0]
        self.assertEqual(['eighth','quarter'],[n.text for n in relation.findall('beat-unit')])
        self.assertEqual(3,len(relation.findall('beat-unit-dot')))
        self.assertIsNone(relation.find('per-minute'))
        self.assertIsNone(relation.find('../sound'))
        self.assertEqual([],root.findall('.//words'))
        self.assertEqual(['20160','20160'],[n.text for n in root.findall('.//note/duration')])

    def test_sf_sfz_sfp_are_dynamics_with_written_offsets(self):
        for kind,name in [('SFORZANDO','sf'),('SFORZATO','sfz'),('SFORZANDO_PIANO','sfp')]:
            root=self.export(document([expression(kind,2,scope='NOTE')]))
            direction=[d for d in root.findall('.//direction') if d.find(f'direction-type/dynamics/{name}') is not None][0]
            self.assertEqual(str(2*DIVISIONS),direction.findtext('offset'))
            self.assertEqual([],root.findall('.//words'))

    def test_breath_and_fermata_are_on_written_release(self):
        for mark,tag in [(expression('BREATH',2,scope='PART'),'breath-mark'),(fermata(),'fermata')]:
            root=self.export(document([mark]));notes=root.findall('.//note')
            self.assertIsNotNone(notes[0].find('.//'+tag));self.assertIsNone(notes[1].find('.//'+tag))
            self.assertEqual(2,len(notes));self.assertEqual([],root.findall('.//words'))

    def test_cross_bar_breath_is_only_on_final_tied_slice(self):
        doc=document([expression('BREATH',6,scope='PART')]);page=doc['pages'][0]
        page['measureBeats']=[4,4];page['totalBeats']=8;page['events']=page['events'][:1]
        page['events'][0].update(startBeat=2,durationBeats=4)
        root=self.export(doc);pitched=[n for n in root.findall('.//note') if n.find('pitch') is not None]
        self.assertIsNone(pitched[0].find('.//breath-mark'));self.assertIsNotNone(pitched[1].find('.//breath-mark'))
        self.assertEqual(['20160','20160'],[n.findtext('duration') for n in pitched])

    def test_fermata_rest_remains_silent_and_exact(self):
        doc=document([expression('FERMATA',0,2,scope='REST')]);doc['pages'][0]['events']=[]
        root=self.export(doc);notes=root.findall('.//note')
        self.assertIsNotNone(notes[0].find('rest'));self.assertIsNotNone(notes[0].find('.//fermata'))
        self.assertEqual(str(2*DIVISIONS),notes[0].findtext('duration'))
        self.assertEqual(4*DIVISIONS,sum(int(n.findtext('duration')) for n in notes));self.assertEqual([],root.findall('.//words'))

    def test_detector_owned_rest_fermata_is_written_on_rest(self):
        root=self.export(rest_fermata_document());note=root.find('part/measure/note')
        self.assertIsNotNone(note.find('rest'));self.assertIsNotNone(note.find('notations/fermata'))
        self.assertEqual(str(4*DIVISIONS),note.findtext('duration'));self.assertEqual([],root.findall('.//words'))
        self.assertEqual([],self.export(rest_fermata_document(1)).findall('.//fermata'))

    def test_invalid_internal_pulse_and_unresolved_evidence_cannot_be_printed(self):
        invalid=expression('METRIC_MODULATION');invalid['qualifierText']='metric-pulse-v1:0.73:1'
        with self.assertRaises(ValueError):self.export(document([invalid]))
        mark=expression('BREATH',scope='UNRESOLVED');mark['start']=None
        root=self.export(document([mark]));self.assertEqual([],root.findall('.//breath-mark'))

    def test_printed_slowing_words_preserve_original_text(self):
        for kind,printed in [('RITARDANDO','rit.'),('RALLENTANDO','rall.'),('RITENUTO','rite')]:
            mark=expression(kind);mark['evidence'][0]['printedText']=printed
            root=self.export(document([mark]));self.assertEqual([printed],[w.text for w in root.findall('.//words')])
