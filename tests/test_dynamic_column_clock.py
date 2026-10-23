# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy,tempfile,unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml,DIVISIONS

def document(pos=.17):
    ns=[dict(measureIndex=0,positionInMeasure=p,staffIndex=0,staffCount=1,staffStep=i*2,articulations=0)for i,p in enumerate((.17,.86))]
    es=[dict(measureIndex=0,staffIndex=0,staffCount=1,sourceNoteIndex=i,midi=60+i*4,startBeat=0 if i==0 else 3,durationBeats=3 if i==0 else 1,durationFallback=False)for i in range(2)]
    d=dict(measureIndex=0,positionInMeasure=pos,staffIndex=0,staffCount=1,endMeasureIndex=0,endPosition=pos,decibels=6,direction=0)
    return dict(initialMeter=[4,4],initialKeyFifths=0,pages=[dict(measureBeats=[4],events=es,score=dict(notes=ns,dynamicChanges=[d]))])

class DynamicColumnClockTests(unittest.TestCase):
    def xml(self,d):
        before=copy.deepcopy(d)
        with tempfile.TemporaryDirectory(prefix='original-dynamic-column-')as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(d,path);root=ET.parse(path).getroot()
        self.assertEqual(before,d)
        return [(part.get('id'),int(m.get('number')),list(dy)[0].tag,int(v.findtext('offset','0')))
                for part in root.findall('part')for m in part.findall('measure')for v in m.findall('direction')
                if(dy:=v.find('direction-type/dynamics'))is not None and len(dy)]
    def test_first_column_at_zero(self):self.assertEqual([('P1',1,'ff',0)],self.xml(document()))
    def test_later_column_uses_written_three(self):self.assertEqual([('P1',1,'ff',3*DIVISIONS)],self.xml(document(.86)))
    def test_nearby_unsnapped_geometry_does_not_supply_clock(self):self.assertEqual([],self.xml(document(.8601)))
    def test_edited_export_clock_is_used(self):
        d=document(.86);es=d['pages'][0]['events'];es[0]['durationBeats']=2;es[1].update(startBeat=2,durationBeats=2)
        self.assertEqual([('P1',1,'ff',2*DIVISIONS)],self.xml(d))
    def test_wrong_source_identity_abstains(self):
        d=document();d['pages'][0]['events'][0]['sourceNoteIndex']=1;self.assertEqual([],self.xml(d))
    def test_missing_explicit_identity_abstains(self):
        d=document();d['pages'][0]['events'][0].pop('sourceNoteIndex');self.assertEqual([],self.xml(d))
    def test_estimated_clock_abstains(self):
        d=document();d['pages'][0]['events'][0]['durationFallback']=True;self.assertEqual([],self.xml(d))
    def test_conflicting_chord_owner_clocks_abstain(self):
        d=document();p=d['pages'][0];p['score']['notes'][1]['positionInMeasure']=.17;self.assertEqual([],self.xml(d))
    def test_hairpin_is_not_emitted_as_absolute_dynamic(self):
        d=document();d['pages'][0]['score']['dynamicChanges'][0]['direction']=1;self.assertEqual([],self.xml(d))
    def test_repeated_level_in_later_bar_is_preserved(self):
        d=document();p=d['pages'][0];p['measureBeats']=[4,4];p['score']['notes'] += [dict(n,measureIndex=1)for n in p['score']['notes'][:2]]
        p['events'] += [dict(e,measureIndex=1,sourceNoteIndex=i+2,startBeat=e['startBeat']+4)for i,e in enumerate(p['events'][:2])]
        p['score']['dynamicChanges'].append(dict(p['score']['dynamicChanges'][0],measureIndex=1,endMeasureIndex=1))
        self.assertEqual([('P1',1,'ff',0),('P1',2,'ff',0)],self.xml(d))
    def test_duplicate_same_column_level_emits_once(self):
        d=document();p=d['pages'][0];p['score']['dynamicChanges']*=2;self.assertEqual([('P1',1,'ff',0)],self.xml(d))
    def test_other_staff_cannot_supply_dynamic_clock(self):
        d=document();d['pages'][0]['score']['dynamicChanges'][0].update(staffIndex=1,staffCount=2);self.assertEqual([],self.xml(d))
    def shared(self,lower_position=.17,lower_beat=0):
        d=document();p=d['pages'][0]
        for n in p['score']['notes']:n['staffCount']=2
        for e in p['events']:e['staffCount']=2
        p['score']['notes'].append(dict(p['score']['notes'][0],staffIndex=1,positionInMeasure=lower_position))
        p['events'].append(dict(p['events'][0],staffIndex=1,sourceNoteIndex=2,midi=48,startBeat=lower_beat,durationBeats=4-lower_beat))
        p['score']['dynamicChanges'][0].update(staffCount=2,sharedStaffs=True)
        return d
    def test_shared_dynamic_is_printed_on_both_existing_parts(self):
        self.assertEqual([('P1',1,'ff',0),('P2',1,'ff',0)],self.xml(self.shared()))
    def test_shared_dynamic_does_not_need_a_simultaneous_attack_on_each_part(self):
        self.assertEqual([('P1',1,'ff',0),('P2',1,'ff',0)],self.xml(self.shared(.21,1)))
    def test_shared_timing_disagreement_abstains_on_both_parts(self):
        self.assertEqual([],self.xml(self.shared(.17,1)))
    def test_terminal_after_grace_stays_in_its_written_bar(self):
        from tests.test_musicxml_grace import document as grace_document
        d=grace_document(True);p=d['pages'][0];p['measureBeats']=[4,4]
        for i,(n,e)in enumerate(zip(p['score']['notes'],p['events'])):
            n.update(measureIndex=0,staffIndex=0,staffCount=1,positionInMeasure=(.8,.85,.1)[i])
            e.update(measureIndex=0,staffIndex=0,staffCount=1,sourceNoteIndex=i,durationFallback=False)
        n=p['score']['notes'][0]
        p['score']['dynamicChanges']=[dict(measureIndex=0,positionInMeasure=n['positionInMeasure'],staffIndex=0,staffCount=1,endMeasureIndex=0,endPosition=n['positionInMeasure'],decibels=6,direction=0)]
        self.assertEqual([('P1',1,'ff',4*DIVISIONS)],self.xml(d))
