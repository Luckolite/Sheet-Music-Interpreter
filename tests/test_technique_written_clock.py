# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original persistent playing-instruction export and source ownership contracts."""
import copy
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml,DIVISIONS

def document(position=.03,kind=1):
    notes=[dict(measureIndex=0,positionInMeasure=p,staffIndex=0,staffCount=1,
                staffStep=i*2,articulations=0)for i,p in enumerate((.12,.82))]
    events=[dict(measureIndex=0,staffIndex=0,staffCount=1,sourceNoteIndex=i,
                 midi=60+i*4,startBeat=i*2,durationBeats=2,durationFallback=False)
            for i in range(2)]
    change=dict(measureIndex=0,positionInMeasure=position,staffIndex=0,staffCount=1,technique=kind)
    return dict(initialMeter=[4,4],initialKeyFifths=0,pages=[dict(measureBeats=[4],events=events,
                score=dict(notes=notes,techniqueChanges=[change]))])

class TechniqueWrittenClockTests(unittest.TestCase):
    def xml(self,doc):
        before=copy.deepcopy(doc)
        with tempfile.TemporaryDirectory(prefix='original-technique-clock-')as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(doc,path);root=ET.parse(path).getroot()
        self.assertEqual(before,doc)
        return [(d.findtext('direction-type/words'),int(d.findtext('offset','0')))
                for d in root.findall('.//direction')if d.find('direction-type/words')is not None]
    def test_unsnapped_pizzicato_before_first_attack_starts_at_zero(self):
        self.assertEqual([('pizz.',0)],self.xml(document()))
    def test_between_uneven_columns_uses_the_first_affected_attack(self):
        self.assertEqual([('arco',2*DIVISIONS)],self.xml(document(.6,0)))
    def test_exact_column_and_expression_share_the_same_note_clock(self):
        self.assertEqual([('marcato',2*DIVISIONS)],self.xml(document(.82,4)))
    def test_mixed_topology_cannot_supply_a_first_column(self):
        d=document();d['pages'][0]['score']['notes'][0]['staffCount']=2
        self.assertEqual([],self.xml(d))
    def test_reordered_source_ids_cannot_borrow_another_note_clock(self):
        d=document();d['pages'][0]['events'][0]['sourceNoteIndex']=1
        self.assertEqual([],self.xml(d))
    def test_disagreeing_owner_clocks_at_one_column_abstain(self):
        d=document();p=d['pages'][0];p['score']['notes'][1]['positionInMeasure']=.12
        p['events'][0]['durationBeats']=4;p['events'][1].update(startBeat=1,durationBeats=3)
        self.assertEqual([],self.xml(d))
    def test_later_written_reminder_is_not_removed_by_persistent_state(self):
        d=document();p=d['pages'][0];p['measureBeats']=[4,4]
        p['score']['notes'] += [dict(n,measureIndex=1)for n in p['score']['notes'][:2]]
        p['events'] += [dict(e,measureIndex=1,sourceNoteIndex=i+2,startBeat=e['startBeat']+4)
                       for i,e in enumerate(p['events'][:2])]
        p['score']['techniqueChanges'].append(dict(p['score']['techniqueChanges'][0],measureIndex=1))
        self.assertEqual([('pizz.',0),('pizz.',0)],self.xml(d))
    def test_duplicate_acquisitions_of_one_affected_column_print_once(self):
        d=document();p=d['pages'][0]
        p['score']['techniqueChanges'].append(dict(p['score']['techniqueChanges'][0],positionInMeasure=.12))
        self.assertEqual([('pizz.',0)],self.xml(d))
    def test_grace_budget_does_not_delay_instruction_on_written_principal(self):
        d=document(.15,0);p=d['pages'][0]
        p['score']['notes'].insert(0,dict(p['score']['notes'][0],positionInMeasure=.1,articulations=32768))
        p['events'].insert(0,dict(p['events'][0],startBeat=0,durationBeats=.25))
        for i,e in enumerate(p['events']):e['sourceNoteIndex']=i
        p['score']['notes'][1]['positionInMeasure']=.2
        p['events'][1].update(startBeat=.25,durationBeats=1.75)
        for i,e in enumerate(p['events']):
            e['writtenPlacement']=dict(onsetBeats=0 if i<2 else 2,durationBeats=.25 if i==0 else 2,
                principalIndex=1 if i==0 else -1,ordinal=0 if i==0 else -1,
                afterGrace=False,stealPercent=12.5 if i==0 else 0)
        self.assertEqual([('arco',0)],self.xml(d))
    def test_estimated_clock_does_not_authorize_a_direction_onset(self):
        d=document();d['pages'][0]['events'][0]['durationFallback']=True
        self.assertEqual([],self.xml(d))
    def test_legacy_event_without_explicit_source_identity_keeps_prior_absence(self):
        d=document();d['pages'][0]['events'][0].pop('sourceNoteIndex')
        self.assertEqual([],self.xml(d))

if __name__=='__main__':unittest.main()
