# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy,tempfile,unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml
from tests.test_musicxml_grace import document
from tests.test_musicxml_slurs import arc

class GracePairSlurExportTests(unittest.TestCase):
    def check(self,after):
        d=document(after);p=d['pages'][0];p['score']['slurSource']=dict(spans=[],
            candidates=[arc('original-cue-pair',0,1,placement='BELOW')],
            noteOrigins=[dict(pageIndex=9,noteIndex=i)for i in range(3)])
        for i,(n,e)in enumerate(zip(p['score']['notes'],p['events'])):
            n['clefBottomDiatonic']=30;e['sourceNoteIndex']=i;e['clefInferred']=False;e['durationFallback']=False
        d.update(initialMeter=[4,4],initialKeyFifths=0);before=copy.deepcopy(d)
        with tempfile.TemporaryDirectory(prefix='original-cue-slur-')as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(d,path);root=ET.parse(path).getroot()
        self.assertEqual(before,d);notes=root.findall('.//note[pitch]');graces=[n for n in notes if n.find('grace')is not None]
        self.assertEqual(2,len(graces));self.assertEqual(3,len(notes))
        self.assertEqual('start',graces[0].find('notations/slur').get('type'))
        self.assertEqual('below',graces[0].find('notations/slur').get('placement'))
        self.assertEqual('stop',graces[1].find('notations/slur').get('type'))
        principal=[n for n in notes if n.find('grace')is None][0]
        self.assertIsNone(principal.find('notations/slur'));self.assertEqual('40320',principal.findtext('duration'))
    def test_prefix_grace_pair_keeps_its_own_slur_and_principal_clock(self):self.check(False)
    def test_after_grace_pair_keeps_its_own_written_order(self):self.check(True)
