# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest,xml.etree.ElementTree as E
from sheet_interpreter.octave_shift_export import apply
class OctaveDirectionOrderOriginal(unittest.TestCase):
 def run_scene(self,shifts,voices):
  root=E.Element('score-partwise');part=E.SubElement(root,'part');m=E.SubElement(part,'measure');owned={}
  for shift,voice in zip(shifts,voices):
   n=E.SubElement(m,'note');E.SubElement(n,'grace');E.SubElement(n,'voice').text=str(voice);owned[n]=shift
  apply(root,owned);state={};seen=[];active=set()
  for e in m:
   if e.tag=='direction':
    x=e.find('direction-type/octave-shift');v=e.findtext('voice');num=int(x.get('number'));self.assertTrue(1<=num<=16)
    if x.get('type')=='stop':self.assertIn(num,active);active.remove(num);state[v]=0
    else:self.assertNotIn(num,active);active.add(num);state[v]=(1 if x.get('type')=='down' else -1)*(int(x.get('size'))-1)//7
   else:seen.append(state.get(e.findtext('voice'),0))
  self.assertFalse(active);return seen
 def test_unshifted_grace_before_shifted_principal(self):self.assertEqual([0,1],self.run_scene([0,1],[1,1]))
 def test_grace_at_sign_transition(self):self.assertEqual([1,1,-1],self.run_scene([1,1,-1],[1,1,1]))
 def test_zero_shift_after_grace(self):self.assertEqual([1,0],self.run_scene([1,0],[1,1]))
 def test_opposing_voices_global_object_pairing(self):self.assertEqual([1,-1,2],self.run_scene([1,-1,2],[101,5,105]))
 def test_zero_shift_creates_no_objects(self):self.assertEqual([0,0],self.run_scene([0,0],[1,1]))
