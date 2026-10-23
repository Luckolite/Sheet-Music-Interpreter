# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy,tempfile,unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml
from tests.test_musicxml_slurs import document as legacy_document
from tests.test_full_measure_rests import document as rest_document, rest

class PrintedMultiRestExportOriginalTest(unittest.TestCase):
    def export(self,doc):
        before=copy.deepcopy(doc)
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'original.musicxml';write_musicxml(doc,path);root=ET.parse(path).getroot()
        self.assertEqual(before,doc)
        return root
    def test_legacy_minimal_slur_document_needs_no_geometry_for_absent_groups(self):
        doc=legacy_document();self.assertNotIn('measures',doc['pages'][0]['score'])
        root=self.export(doc);self.assertEqual(4,len(root.findall('.//note[pitch]')));self.assertEqual([],root.findall('.//multiple-rest'))
    def test_explicit_empty_groups_need_no_geometry_or_rests(self):
        doc=legacy_document();doc['pages'][0]['score']['multiRestGroups']=[]
        self.assertEqual(4,len(self.export(doc).findall('.//note[pitch]')))
    def group_document(self):
        doc=rest_document(beats=(4,4,4,4),meter=(4,4),rests=[rest(m=i)for i in range(4)])
        score=doc['pages'][0]['score'];score['measures']=[dict(left=.1,right=.9,top=.2,bottom=.4)]*4
        score['multiRestGroups']=[dict(firstMeasureIndex=0,measureCount=4,staffIndex=0,staffCount=1)]
        return doc
    def test_explicit_four_bar_hrest_exports_one_group_and_four_full_rests(self):
        root=self.export(self.group_document());self.assertEqual(['4'],[x.text for x in root.findall('.//multiple-rest')]);self.assertEqual(4,len(root.findall('.//rest')))
        self.assertEqual(['40320']*4,[x.text for x in root.findall('.//note/duration')])
    def test_ordinary_four_rest_bars_are_not_collapsed(self):
        doc=self.group_document();del doc['pages'][0]['score']['multiRestGroups'];root=self.export(doc)
        self.assertEqual([],root.findall('.//multiple-rest'));self.assertEqual(4,len(root.findall('.//rest')))
    def test_false_group_with_literal_rest_abstains_by_error(self):
        doc=self.group_document();doc['pages'][0]['score']['rests'][2]['kind']='LITERAL'
        with self.assertRaises(ValueError):self.export(doc)
if __name__=='__main__':unittest.main()
