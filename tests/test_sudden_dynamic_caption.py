# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy,struct,tempfile,unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml,DIVISIONS

def document(literal='p sub.',position=.86):
    ns=[dict(measureIndex=0,positionInMeasure=p,staffIndex=0,staffCount=1,staffStep=i*2,articulations=0)for i,p in enumerate((.17,.86))]
    es=[dict(measureIndex=0,staffIndex=0,staffCount=1,sourceNoteIndex=i,midi=60+i*4,startBeat=0 if i==0 else 3,durationBeats=3 if i==0 else 1,durationFallback=False)for i in range(2)]
    bits=struct.unpack('>i',struct.pack('>f',position))[0]
    event=dict(eventId='original-sudden',kind='UNRESOLVED_DIRECTION',scope='PART',staffIndex=0,staffCount=1,start=dict(measureIndex=0,quarterBeatOffset=0),end=None,targetEventId=f'expression-column:0:0:1:{bits}',strength='UNSPECIFIED',qualifierText='',evidence=[dict(sourceId='printed-expression-word',pageIndex=0,visualX=position,staffIndex=0,staffCount=1,printedText=literal)])
    d=dict(measureIndex=0,positionInMeasure=.86,staffIndex=0,staffCount=1,endMeasureIndex=0,endPosition=.86,decibels=-8,direction=0)
    return dict(initialMeter=[4,4],initialKeyFifths=0,pages=[dict(measureBeats=[4],events=es,score=dict(notes=ns,dynamicChanges=[d],expressiveEvents=[event]))])

class SuddenDynamicCaptionTests(unittest.TestCase):
    def xml(self,d):
        original=copy.deepcopy(d)
        with tempfile.TemporaryDirectory(prefix='original-sudden-dynamic-')as folder:
            p=Path(folder)/'score.musicxml';write_musicxml(d,p);root=ET.parse(p).getroot()
        self.assertEqual(original,d);return root
    def check(self,literal,before=False,beat=3,d=None):
        root=self.xml(document(literal)if d is None else d);ds=[v for v in root.findall('.//direction')if v.find('direction-type/dynamics/p')is not None];self.assertEqual(1,len(ds));direction=ds[0]
        self.assertEqual(round(beat*DIVISIONS),int(direction.findtext('offset','0')))
        self.assertEqual(['words','dynamics']if before else['dynamics','words'],[list(v)[0].tag for v in direction.findall('direction-type')])
        self.assertEqual(1,len(root.findall('.//words')));self.assertEqual(literal.replace('p','').strip(),direction.findtext('direction-type/words').strip())
    def test_suffix_is_combined_at_its_owned_clock(self):self.check('p sub.')
    def test_prefix_keeps_case_and_order(self):self.check('Sub. p',True)
    def test_full_subito_suffix_is_retained(self):self.check('p subito')
    def test_edited_clock_moves_modifier_with_dynamic(self):
        d=document();es=d['pages'][0]['events'];es[0]['durationBeats']=2;es[1].update(startBeat=2,durationBeats=2);self.check('p sub.',beat=2,d=d)
    def test_another_column_cannot_supply_caption(self):
        root=self.xml(document(position=.17));self.assertEqual('p sub.',root.findtext('.//words'));self.assertTrue(all(d.find('direction-type/words')is None for d in root.findall('.//direction')if d.find('direction-type/dynamics')is not None))
    def test_wrong_level_is_not_consumed(self):
        root=self.xml(document('f sub.'));self.assertEqual('f sub.',root.findtext('.//words'));self.assertEqual(1,len(root.findall('.//direction-type/dynamics/p')))
    def test_unowned_text_is_not_consumed(self):
        d=document();d['pages'][0]['score']['expressiveEvents'][0]['evidence'][0]['sourceId']='manual-note';root=self.xml(d);self.assertEqual('p sub.',root.findtext('.//words'))
    def test_conflicting_captions_remain_separate(self):
        d=document();p=d['pages'][0];p['score']['expressiveEvents'].append(dict(p['score']['expressiveEvents'][0],eventId='other-caption',evidence=[dict(p['score']['expressiveEvents'][0]['evidence'][0],printedText='subito p')]))
        root=self.xml(d);self.assertEqual({'p sub.','subito p'},{v.text for v in root.findall('.//words')})
    def test_shared_dynamic_keeps_modifier_on_each_shared_part(self):
        d=document();p=d['pages'][0]
        for n in p['score']['notes']:n['staffCount']=2
        for e in p['events']:e['staffCount']=2
        p['score']['notes'].append(dict(p['score']['notes'][0],staffIndex=1))
        p['events'].append(dict(p['events'][0],staffIndex=1,sourceNoteIndex=2,midi=48,durationBeats=4))
        p['score']['dynamicChanges'][0].update(staffCount=2,sharedStaffs=True)
        caption=p['score']['expressiveEvents'][0];caption.update(staffCount=2,targetEventId=caption['targetEventId'].replace(':0:1:',':0:2:'));caption['evidence'][0]['staffCount']=2
        root=self.xml(d);ds=[v for v in root.findall('.//direction')if v.find('direction-type/dynamics/p')is not None]
        self.assertEqual(2,len(ds));self.assertEqual(2,len(root.findall('.//words')))
        for v in ds:self.assertEqual('sub.',v.findtext('direction-type/words').strip());self.assertEqual('30240',v.findtext('offset'))
    def test_after_grace_caption_stays_in_the_originating_bar(self):
        from tests.test_musicxml_grace import document as grace_document
        d=grace_document(True);p=d['pages'][0];p['measureBeats']=[4,4]
        for i,(n,e)in enumerate(zip(p['score']['notes'],p['events'])):
            n['positionInMeasure']=(.8,.85,.1)[i];e.update(sourceNoteIndex=i,durationFallback=False)
        original=document()['pages'][0]['score'];caption=copy.deepcopy(original['expressiveEvents'][0]);caption['targetEventId']='expression-column:0:0:1:'+str(struct.unpack('>i',struct.pack('>f',.8))[0])
        p['score']['expressiveEvents']=[caption];p['score']['dynamicChanges']=[dict(original['dynamicChanges'][0],positionInMeasure=.8,endPosition=.8)]
        root=self.xml(d);m=root.find('part/measure');ds=[v for v in m.findall('direction')if v.find('direction-type/dynamics/p')is not None]
        self.assertEqual(1,len(ds));self.assertEqual('40320',ds[0].findtext('offset'));words=ds[0].findtext('direction-type/words');self.assertIsNotNone(words);self.assertEqual('sub.',words.strip())
