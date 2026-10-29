# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original source ordinal/pickup exporter cases; no captured sheet content."""
import unittest,tempfile
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml

def page(first=0,pickup=False,empty=False,silent=False):
 return dict(events=[]if empty or silent else[dict(midi=60,startBeat=0,durationBeats=1.5,staffIndex=0),dict(midi=62,startBeat=1.5,durationBeats=1.5,staffIndex=0)],measureBeats=[]if empty else[3,6,6],score=dict(firstMeasureNumber=first,openingPickup=pickup,meterChanges=[dict(measureIndex=0,numerator=12,denominator=8)]))
class PickupXmlOrdinalOriginals(unittest.TestCase):
 def export(self,pages):
  with tempfile.TemporaryDirectory()as d:
   p=Path(d)/'original.musicxml';write_musicxml(dict(pages=pages),p,meter=(12,8));return ET.parse(p).getroot()
 def numbers(self,pages):return[[m.get('number')for m in part.findall('measure')]for part in self.export(pages).findall('part')]
 def test_proved_zero_pickup(self):self.assertEqual([['0','1','2']],self.numbers([page(0,True)]))
 def test_proved_one_sentinel(self):self.assertEqual([['0','1','2']],self.numbers([page(1,True)]))
 def test_unknown_zero_stays_one(self):self.assertEqual([['1','2','3']],self.numbers([page(0,False)]))
 def test_positive_excerpt_starts_thirtyfive(self):self.assertEqual([['35','36','37']],self.numbers([page(35,False)]))
 def test_ordinary_one(self):self.assertEqual([['1','2','3']],self.numbers([page(1,False)]))
 def test_cover_cannot_discard_first_music_pickup(self):self.assertEqual([['0','1','2']],self.numbers([page(empty=True),page(0,True)]))
 def test_later_pickup_cannot_reset_sequence(self):self.assertEqual([list(map(str,range(1,7)))],self.numbers([page(1,False),page(0,True)]))
 def test_later_continuation_does_not_reset_sequence(self):self.assertEqual([list(map(str,range(35,41)))],self.numbers([page(35,False),page(38,False)]))
 def test_negative_unknown_cannot_become_zero(self):self.assertEqual([['1','2','3']],self.numbers([page(-4,False)]))
 def test_false_and_nonintegral_first_cannot_authenticate_zero(self):
  for invalid in [True,False,0.]:
   with self.subTest(first=invalid):self.assertEqual([['1','2','3']],self.numbers([page(invalid,True)]))
  for invalid in [None,'0']:
   with self.subTest(first=invalid),self.assertRaises((ValueError,TypeError)):self.numbers([page(invalid,True)])
 def test_continuation_cannot_be_zero_even_with_inconsistent_flag(self):self.assertEqual([['35','36','37']],self.numbers([page(35,True)]))
 def test_proved_silent_pickup_uses_same_ordinals(self):self.assertEqual([['0','1','2']],self.numbers([page(0,True,silent=True)]))
 def test_all_parts_share_same_zero_start(self):
  p=page(0,True);p['events'].append(dict(midi=43,startBeat=0,durationBeats=3,staffIndex=1));self.assertEqual([['0','1','2'],['0','1','2']],self.numbers([p]))
 def test_short_duration_without_proof_never_renumbers(self):
  root=self.export([page(0,False)]);m=root.find('part/measure');self.assertEqual('yes',m.get('implicit'));self.assertEqual('1',m.get('number'))
if __name__=='__main__':unittest.main()
