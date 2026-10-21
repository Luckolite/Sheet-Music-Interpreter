# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original attack-local bow events; no private score data or workspace paths."""
import copy,tempfile,unittest,struct
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter import guide_writer as wire,musicxml
def score():
 n=dict(measureIndex=0,positionInMeasure=.25,staffStep=0,staffIndex=0,staffCount=1,pageY=.4,tiedFromPrevious=False,augmentationDots=0,beamCount=0,writtenAccidental=2,unbeamedDurationBeats=1.,tupletDivisor=1,followingRestBeats=0.,articulations=0,clefBottomDiatonic=30,crossStaffBeam=False,leadingRestBeats=0.,compactOpening=False,octaveShift=0,boundaryTies=0,tupletNormalNotes=1,stemDirection=0,kind='PITCHED')
 return dict(measures=[dict(left=0.,right=1.,top=0.,bottom=1.)],notes=[n],firstMeasureNumber=1,keyChanges=[],tempoChanges=[],meterChanges=[],rests=[],techniqueChanges=[],dynamicChanges=[],playbackDirections=[],expressiveEvents=[])
def bow(s,t=6):
 return dict({k:s['notes'][0][k] for k in ('measureIndex','positionInMeasure','staffIndex','staffCount')},technique=t)
def doc(s,rows=None):
 events=rows or [dict(measureIndex=0,staffIndex=0,staffCount=1,midi=60,startBeat=0.,durationBeats=1.,sourceNoteIndex=0)]
 return dict(pages=[dict(measureBeats=[4,4],events=events,score=s)])
class BowConsumers(unittest.TestCase):
 def xml(self,d):
  before=copy.deepcopy(d)
  with tempfile.TemporaryDirectory() as q:
   p=Path(q)/'x.xml';musicxml.write_musicxml(d,p);tree=ET.parse(p)
  self.assertEqual(before,d);return tree
 def test_exact_attack_technical_not_words(self):
  for t,tag in [(6,'up-bow'),(7,'down-bow')]:
   s=score();s['techniqueChanges']=[bow(s,t)];r=self.xml(doc(s));self.assertEqual(1,len(r.findall('.//notations/technical/'+tag)));self.assertFalse(r.findall('.//words'))
 def test_tied_fragment_not_repeated(self):
  s=score();s['techniqueChanges']=[bow(s)];r=self.xml(doc(s,[dict(measureIndex=0,staffIndex=0,staffCount=1,midi=60,startBeat=3.,durationBeats=2.,sourceNoteIndex=0)]));self.assertEqual(1,len(r.findall('.//up-bow')))
 def test_chord_shares_one_sign(self):
  s=score();s['notes'].append(dict(s['notes'][0],staffStep=2));s['techniqueChanges']=[bow(s)];events=[dict(measureIndex=0,staffIndex=0,staffCount=1,midi=60+4*i,startBeat=0.,durationBeats=1.,sourceNoteIndex=i)for i in range(2)];self.assertEqual(1,len(self.xml(doc(s,events)).findall('.//up-bow')))
 def test_conflicting_directions_abstain(self):
  s=score();s['techniqueChanges']=[bow(s),bow(s,7)];self.assertFalse(self.xml(doc(s)).findall('.//technical'))
 def test_wrong_attack_and_reordered_identity_not_borrowed(self):
  for field,value in [('positionInMeasure',.5),('staffIndex',1),('measureIndex',1)]:
   s=score();b=bow(s);b[field]=value;s['techniqueChanges']=[b];self.assertFalse(self.xml(doc(s)).findall('.//technical'))
 def test_291_wire_fence_and_exact_attack(self):
  s=score();s['techniqueChanges']=[bow(s,7)];self.assertEqual(291,struct.unpack('>i',wire.encode(s)[:4])[0])
  with self.assertRaises(ValueError):wire.encode(s,290)
  s['techniqueChanges'][0]['positionInMeasure']=.5
  with self.assertRaises(ValueError):wire.encode(s)
 def test_released290_still_supported(self):
  s=score();s['techniqueChanges']=[bow(s,1)];self.assertEqual(290,struct.unpack('>i',wire.encode(s,290)[:4])[0])
if __name__=='__main__':unittest.main()
