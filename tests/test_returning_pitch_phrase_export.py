# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original scalar phrases exercise the standalone consumer, without source score fixtures."""
import copy,tempfile,unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml
from tests.test_musicxml_slurs import document,arc

class ReturningPitchPhraseExportTest(unittest.TestCase):
    def score(self, events=None):
        value=document(events=events or [(0,1,60),(1,1,62),(2,1,60)],spans=[],candidates=[arc('returning',0,2)])
        for n in value['pages'][0]['score']['notes']:n['clefBottomDiatonic']=30
        return value
    def export(self,value):
        before=copy.deepcopy(value)
        with tempfile.TemporaryDirectory(prefix='original-returning-phrase-')as folder:
            path=Path(folder)/'out.musicxml';write_musicxml(value,path);result=ET.parse(path).getroot()
        self.assertEqual(before,value);return result
    def count(self,value):return len(self.export(value).findall('.//slur'))
    def test_same_pitch_phrase_gets_exact_attack_endpoints(self):
        xml=self.export(self.score());notes=xml.findall('.//note[pitch]');self.assertEqual('start',notes[0].find('notations/slur').get('type'));self.assertIsNone(notes[1].find('notations/slur'));self.assertEqual('stop',notes[2].find('notations/slur').get('type'));self.assertEqual(0,len(xml.findall('.//tie')))
    def test_known_different_pitch_candidate_uses_the_same_classification_path(self):
        self.assertEqual(2,self.count(self.score([(0,1,60),(1,1,62),(2,1,64)])))
    def test_unknown_clef_remains_deferred(self):
        value=self.score();value['pages'][0]['score']['notes'][0].pop('clefBottomDiatonic');self.assertEqual(0,self.count(value))
    def test_unknown_meter_remains_deferred(self):
        value=self.score();value.pop('initialMeter');self.assertEqual(0,self.count(value))
    def test_unknown_key_remains_deferred(self):
        value=self.score();value.pop('initialKeyFifths');self.assertEqual(0,self.count(value))
    def test_fallback_clock_cannot_prove_phrase(self):
        value=self.score();value['pages'][0]['events'][1]['durationFallback']=True;self.assertEqual(0,self.count(value))
    def test_other_part_middle_attack_cannot_prove_same_pitch_phrase(self):
        value=self.score()
        for row in [value['pages'][0]['score']['notes'][1],value['pages'][0]['events'][1]]:row.update(staffIndex=1,staffCount=2)
        self.assertEqual(0,self.count(value))
    def test_held_opening_voice_cannot_become_a_phrase(self):
        self.assertEqual(0,self.count(self.score([(0,2.5,60),(1,1,62),(2,1,60)])))
    def test_overlapping_middle_voice_cannot_prove_a_phrase(self):
        self.assertEqual(0,self.count(self.score([(0,1,60),(1,1.5,62),(2,1,60)])))
    def test_same_pitch_intervening_attack_is_still_ambiguous(self):
        self.assertEqual(0,self.count(self.score([(0,1,60),(1,1,60),(2,1,60)])))
    def test_exact_sounding_tie_survives_without_duplicate_slur(self):
        value=self.score([(0,2,60),(1,1,62),(2,1,60)]);value['pages'][0]['events'][2]['tiedFromPrevious']=True
        xml=self.export(value);self.assertEqual(0,len(xml.findall('.//slur')));self.assertGreater(len(xml.findall('.//tie')),0)
    def test_selected_owner_deletion_never_borrows_another_head(self):
        value=self.score();value['pages'][0]['score']['slurSource']['noteOrigins'][0]=None;self.assertEqual(0,self.count(value))
    def test_pending_candidate_round_trip_does_not_become_a_source_edit(self):
        from sheet_interpreter.slur_wire import encode_source,decode_source
        value=self.score();source=value['pages'][0]['score']['slurSource'];wire=encode_source(source,3);self.assertEqual(wire,encode_source(decode_source(wire,3),3));self.assertEqual(2,self.count(value))
    def test_scores_without_slur_metadata_keep_their_existing_export(self):
        value=self.score();value['pages'][0]['score']['slurSource']=None;self.assertEqual(0,self.count(value))
    def test_later_page_key_does_not_prove_an_earlier_unknown_context(self):
        value=self.score();value.pop('initialKeyFifths');value['pages'].append(dict(measureBeats=[4],events=[],score=dict(notes=[],slurSource=None,keyChanges=[dict(measureIndex=0,fifths=0)])));self.assertEqual(0,self.count(value))
