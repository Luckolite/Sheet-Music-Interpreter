# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import tempfile,unittest,copy
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml
from tests.test_musicxml_slurs import document,arc

class CandidateCrossPartExportOriginalTest(unittest.TestCase):
    def score(self):
        value=document(events=[(0,1,60),(1,1,62),(2,1,64)],spans=[],candidates=[arc('candidate-cross-part',0,2)])
        for note in value['pages'][0]['score']['notes']:note['clefBottomDiatonic']=30
        for row in [value['pages'][0]['score']['notes'][2],value['pages'][0]['events'][2]]:row.update(staffIndex=1,staffCount=2)
        return value
    def test_candidate_cross_part_remains_deferred_and_export_succeeds(self):
        value=self.score();before=copy.deepcopy(value)
        with tempfile.TemporaryDirectory(prefix='original-candidate-cross-part-')as folder:
            path=Path(folder)/'out.musicxml';write_musicxml(value,path);root=ET.parse(path).getroot()
        self.assertEqual(before,value);self.assertEqual(2,len(root.findall('part')));self.assertEqual(0,len(root.findall('.//slur')))
    def test_different_original_part_counts_can_still_share_one_actual_exported_part(self):
        value=self.score()
        for row in [value['pages'][0]['score']['notes'][2],value['pages'][0]['events'][2]]:row['staffIndex']=0
        with tempfile.TemporaryDirectory(prefix='original-candidate-same-exported-part-')as folder:
            path=Path(folder)/'out.musicxml';write_musicxml(value,path);root=ET.parse(path).getroot()
        self.assertEqual(1,len(root.findall('part')));self.assertEqual(2,len(root.findall('.//slur')))
    def test_unknown_original_part_does_not_gain_phrase_intent_from_a_default_part(self):
        value=self.score()
        for row in [value['pages'][0]['score']['notes'][2],value['pages'][0]['events'][2]]:
            row['staffIndex']=0;row.pop('staffCount');row['clefBottomDiatonic']=30
        with tempfile.TemporaryDirectory(prefix='original-candidate-unknown-part-')as folder:
            path=Path(folder)/'out.musicxml';write_musicxml(value,path);root=ET.parse(path).getroot()
        self.assertEqual(0,len(root.findall('.//slur')))
