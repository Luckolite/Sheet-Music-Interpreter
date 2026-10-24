# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original group, ownership and wire/export controls; no published score fixtures."""
import copy
import struct
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as E
from sheet_interpreter import parenthesized_note_group as g, semantic_wire
from sheet_interpreter.musicxml import emit_note, write_musicxml


def document():
    notes=[];events=[]
    for i,(x,step,pitch,start,duration)in enumerate([(.1,2,67,0,1),(.35,5,72,1,1),(.7,4,71,2,2)]):
        notes.append(dict(measureIndex=0,positionInMeasure=x,pageY=.3,staffStep=step,staffIndex=0,staffCount=1,kind='PITCHED',articulations=0))
        events.append(dict(measureIndex=0,staffIndex=0,staffCount=1,sourceNoteIndex=i,startBeat=start,durationBeats=duration,midi=pitch,kind='PITCHED',durationFallback=False,tiedFromPrevious=False))
    pair=g.Pair(tuple(g.member_of(n)for n in notes),tuple(g.f32(v)for v in[-1.2,-.1,1.1,0,1.75,1.75]))
    mark=dict(eventId='original-parenthesis',kind='PARENTHESIZED_NOTE_GROUP',scope='UNRESOLVED',staffIndex=0,staffCount=1,targetEventId=pair.target,start=None,end=None,strength='UNSPECIFIED',qualifierText=pair.qualifier,evidence=[dict(sourceId=g.SOURCE,pageIndex=0,visualX=.25,staffIndex=0,staffCount=1,printedText='( note group )')])
    return dict(pages=[dict(events=events,measureBeats=[4],totalBeats=4,score=dict(firstMeasureNumber=1,notes=notes,measures=[{}],tempoChanges=[],expressiveEvents=[mark]))])


class ParenthesisOriginals(unittest.TestCase):
    def setUp(self):
        self.doc=document();self.page=self.doc['pages'][0];self.notes=self.page['score']['notes'];self.mark=self.page['score']['expressiveEvents'][0];self.pair=g.decode(self.mark['qualifierText'])
    def export(self):
        before=copy.deepcopy(self.doc)
        with tempfile.TemporaryDirectory()as temp:
            path=Path(temp)/'original.musicxml';write_musicxml(self.doc,path);root=E.parse(path).getroot()
        self.assertEqual(before,self.doc);return root
    def test_canonical_pair_keeps_all_members(self):self.assertEqual(self.mark['qualifierText'],self.pair.qualifier);self.assertEqual((0,1,2),g.owners(self.mark,self.notes))
    def test_float32_boundary_geometry_matches_java(self):
        for v in [(g.f32(-.45),g.f32(.65),g.f32(.45),g.f32(-.65),g.f32(1.1),g.f32(2.6)),(-2,0,2,0,2,2)]:self.assertIsNotNone(g.decode(g.Pair(self.pair.members,v).qualifier))
    def test_missing_member_refuses(self):self.assertIsNone(g.owners(self.mark,self.notes[:2]))
    def test_partial_adapter_selection_refuses(self):self.page['events'].pop();self.assertEqual((),g.retained(self.page))
    def test_omitted_interior_member_refuses(self):
        p=g.Pair((self.pair.members[0],self.pair.members[-1]),self.pair.geometry);self.mark.update(qualifierText=p.qualifier,targetEventId=p.target);self.assertIsNone(g.owners(self.mark,self.notes))
    def test_duplicate_physical_slot_refuses(self):self.assertIsNone(g.owners(self.mark,self.notes+[copy.deepcopy(self.notes[1])]))
    def test_endpoint_chord_cannot_hide_an_unlisted_member(self):
        rival=dict(self.notes[0],staffStep=14);self.assertIsNone(g.owners(self.mark,self.notes+[rival]))
    def test_changed_written_step_refuses(self):self.notes[1]['staffStep']+=1;self.assertIsNone(g.owners(self.mark,self.notes))
    def test_grace_member_refuses(self):self.notes[1]['articulations']=g.GRACE;self.assertIsNone(g.owners(self.mark,self.notes))
    def test_foreign_evidence_refuses(self):self.mark['evidence'][0]['sourceId']='foreign';self.assertIsNone(g.owners(self.mark,self.notes))
    def test_wrong_adapter_identity_refuses(self):self.page['events'][1]['sourceNoteIndex']=2;self.assertEqual((),g.retained(self.page))
    def test_fallback_duration_refuses(self):self.page['events'][1]['durationFallback']=True;self.assertEqual((),g.retained(self.page))
    def test_overlapping_member_clocks_refuse(self):self.page['events'][1]['startBeat']=.5;self.assertEqual((),g.retained(self.page))
    def test_stale_resolved_attack_refuses(self):self.mark.update(scope='NOTE',start=dict(measureIndex=0,quarterBeatOffset=1));self.assertEqual((),g.retained(self.page))
    def test_physical_duplicate_refuses_before_stale_clock_dropped(self):
        m=copy.deepcopy(self.mark);m.update(eventId='rival',scope='NOTE',start=dict(measureIndex=0,quarterBeatOffset=1));self.page['score']['expressiveEvents'].append(m);self.assertEqual((),g.retained(self.page))
    def test_bool_is_not_member_integer(self):self.notes[1]['staffIndex']=False;self.assertIsNone(g.member_of(self.notes[1]))
    def test_noncanonical_member_token_refuses(self):self.assertIsNone(g.decode(self.pair.qualifier.replace('0,0,1,','00,0,1,')))
    def test_uppercase_or_overlong_wire_refuses(self):self.assertIsNone(g.decode(self.pair.qualifier.upper()));self.assertIsNone(g.decode('x'*4097))
    def test_guide296_roundtrips_pair(self):
        data=semantic_wire.encode([],[self.mark],1,296);self.assertEqual(([],[self.mark]),semantic_wire.decode(data,1,296));self.assertEqual(22,semantic_wire.KINDS.index('PARENTHESIZED_NOTE_GROUP'))
    def test_guide295_cannot_encode_or_decode_kind22(self):
        with self.assertRaises(ValueError):semantic_wire.encode([],[self.mark],1,295)
        data=semantic_wire.encode([],[self.mark],1,296)
        with self.assertRaises(ValueError):semantic_wire.decode(data,1,295)
    def test_group_outside_header_page_refuses(self):
        with self.assertRaises(ValueError):semantic_wire.encode([],[self.mark],0,296)
    def test_wrong_target_or_strength_refuses_wire(self):
        for change in [dict(targetEventId='unknown'),dict(strength='MOLTO'),dict(scope='PART')]:
            mark=dict(self.mark,**change)
            with self.assertRaises(ValueError):semantic_wire.encode([],[mark],1,296)
    def test_three_notes_export_one_pair(self):
        root=self.export();marks=root.findall('.//other-notation');self.assertEqual(['start','stop'],[m.get('type')for m in marks]);self.assertEqual(marks[0].text,marks[1].text);self.assertEqual([],root.findall('.//notehead'));self.assertEqual([],root.findall('.//words'))
    def test_group_export_does_not_change_written_notes(self):
        root=self.export();self.page['score']['expressiveEvents']=[];old=self.export()
        for tag in ['pitch','duration','type','tie','tied']:
            for tree in [old,root]:
                for n in tree.iter():
                    if n.tail is not None and not n.tail.strip():n.tail=None
                    if n.text is not None and not n.text.strip():n.text=None
            self.assertEqual([E.tostring(x)for x in old.findall('.//'+tag)],[E.tostring(x)for x in root.findall('.//'+tag)])
    def test_invalid_group_never_falls_back_to_words(self):
        self.mark.update(scope='NOTE',start=dict(measureIndex=0,quarterBeatOffset=1));root=self.export();self.assertEqual([],root.findall('.//other-notation'));self.assertEqual([],root.findall('.//words'))
    def test_split_last_value_gets_one_release_endpoint(self):
        self.page['events'][-1]['durationBeats']=2.5;self.page['measureBeats']=[4,4];self.page['totalBeats']=8;self.page['score']['measures']=[{},{}]
        root=self.export();marks=root.findall('.//other-notation');self.assertEqual(2,len(marks));self.assertTrue(root.findall('.//tied'))
    def test_grace_fragment_suppresses_parenthesis_endpoint_without_release_clock(self):
        measure=E.Element('measure')
        endpoint=dict(id='original-grace-suppression',number=1,x=1.1,y=0,height=1.75)
        event=dict(midi=60,retainedParenthesisStop=endpoint)
        before=copy.deepcopy(event)
        emit_note(measure,5040,1,event,grace=dict(afterGrace=False,stealPercent=5))
        self.assertEqual([],measure.findall('.//other-notation'))
        self.assertEqual(before,event)


if __name__=='__main__':unittest.main()
