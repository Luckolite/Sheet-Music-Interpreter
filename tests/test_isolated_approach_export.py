# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original single-head approaches; connected pitch pairs remain separate metadata."""
import copy,tempfile,unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml
from tests.test_musicxml_ornaments import document

class IsolatedApproachExportTest(unittest.TestCase):
    def export(self,value):
        before=copy.deepcopy(value)
        with tempfile.TemporaryDirectory(prefix='original-isolated-approach-')as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(value,path);root=ET.parse(path).getroot()
        self.assertEqual(before,value);return root
    def test_below_approach_writes_scoop_without_invented_pitch_pair(self):
        root=self.export(document([6<<5]));self.assertEqual(1,len(root.findall('.//articulations/scoop')));self.assertEqual([],root.findall('.//slide'));self.assertEqual([],root.findall('.//accidental-mark'))
    def test_above_approach_writes_plop(self):
        root=self.export(document([(6<<5)|(1<<13)]));self.assertEqual(1,len(root.findall('.//articulations/plop')));self.assertEqual([],root.findall('.//scoop'))
    def test_connected_slide_is_not_relabelled_as_an_isolated_approach(self):
        root=self.export(document([(6<<5)|(1<<14)]));self.assertEqual([],root.findall('.//scoop'));self.assertEqual([],root.findall('.//plop'))
    def test_existing_accent_and_approach_keep_one_articulation_container(self):
        root=self.export(document([(6<<5)|1]));self.assertEqual(1,len(root.findall('.//articulations')));self.assertEqual(1,len(root.findall('.//accent')));self.assertEqual(1,len(root.findall('.//scoop')))
    def test_split_note_anchors_approach_only_at_the_attack(self):
        root=self.export(document([6<<5],duration=5));self.assertEqual(1,len(root.findall('.//scoop')));self.assertGreater(len(root.findall('.//tie')),0)
    def test_written_pitch_and_clock_are_preserved(self):
        before=self.export(document([0]));after=self.export(document([6<<5]));self.assertEqual([ET.tostring(n)for n in before.findall('.//pitch')],[ET.tostring(n)for n in after.findall('.//pitch')]);self.assertEqual([n.text for n in before.findall('.//duration')],[n.text for n in after.findall('.//duration')])
