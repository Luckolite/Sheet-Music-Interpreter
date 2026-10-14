# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original scalar scores exercise actual exporter ownership, fragments and numbering."""
import copy
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml


def arc(identity, start, end, page=9, placement='ABOVE', line='SOLID'):
    return dict(id=identity,start=dict(pageIndex=page,noteIndex=start),
                end=dict(pageIndex=page,noteIndex=end),placement=placement,lineType=line,
                evidence=[dict(pageIndex=page,startX=.1,startY=.2,endX=.8,endY=.3)])


def document(events=None, spans=None, candidates=None):
    events = events if events is not None else [(0,1,60),(1,1,62),(2,1,64),(3,1,65)]
    heads=[dict(measureIndex=int(start//4),staffIndex=0,staffCount=1) for start,_,_ in events]
    source=dict(spans=spans if spans is not None else [arc('phrase',0,3)],
                candidates=candidates or [],
                noteOrigins=[dict(pageIndex=9,noteIndex=i) for i in range(len(heads))])
    page=dict(measureBeats=[4,4] if any(start+length>4 for start,length,_ in events) else [4],
              events=[dict(head,startBeat=start,durationBeats=length,midi=pitch,
                           tiedFromPrevious=False,durationFallback=False)
                      for head,(start,length,pitch) in zip(heads,events)],
              score=dict(notes=heads,slurSource=source))
    return dict(pages=[page],initialMeter=[4,4],initialKeyFifths=0)


class MusicXmlSlurTests(unittest.TestCase):
    def export(self, value):
        before=copy.deepcopy(value)
        with tempfile.TemporaryDirectory(prefix='original-slur-export-') as folder:
            path=Path(folder)/'score.musicxml'
            write_musicxml(value,path)
            result=ET.parse(path).getroot()
        self.assertEqual(before,value)
        return result

    def test_confirmed_phrase_preserves_exact_attack_endpoints_and_style(self):
        value=document(spans=[arc('phrase',0,3,placement='BELOW',line='DOTTED')])
        notes=self.export(value).findall('.//note[pitch]')
        self.assertEqual({'type':'start','number':'1','line-type':'dotted','placement':'below','orientation':'under'},
                         notes[0].find('notations/slur').attrib)
        self.assertIsNone(notes[1].find('notations/slur'))
        self.assertEqual({'type':'stop','number':'1'},notes[3].find('notations/slur').attrib)

    def test_pending_geometry_is_not_engraved(self):
        self.assertEqual([],self.export(document(spans=[],candidates=[arc('pending',0,3)])).findall('.//slur'))

    def test_split_duration_anchors_only_first_attack_fragment(self):
        notes=self.export(document(events=[(0,5,60),(5,1,62)],spans=[arc('split',0,1)])).findall('.//note[pitch]')
        self.assertEqual(3,len(notes))
        self.assertEqual('start',notes[0].find('notations/slur').get('type'))
        self.assertIsNone(notes[1].find('notations/slur'))
        self.assertEqual('stop',notes[2].find('notations/slur').get('type'))

    def test_overlapping_phrases_get_distinct_numbers(self):
        root=self.export(document(spans=[arc('outer',0,3),arc('inner',1,2)]))
        self.assertEqual(['1','2'],[n.get('number') for n in root.findall('.//slur') if n.get('type')=='start'])

    def test_shared_forward_endpoint_reuses_number_with_stop_first(self):
        notes=self.export(document(spans=[arc('first',0,1),arc('second',1,3)])).findall('.//note[pitch]')
        self.assertEqual([('stop','1'),('start','1')],[(n.get('type'),n.get('number')) for n in notes[1].findall('notations/slur')])

    def test_reverse_document_order_uses_full_endpoint_interval(self):
        value=document(events=[(0,1,60),(.5,3,62),(1,3,64),(3.5,.5,65)],
                       spans=[arc('reverse',1,2),arc('forward',0,3)])
        root=self.export(value)
        pairs={}
        for n in root.findall('.//slur'):
            pairs.setdefault(n.get('number'),[]).append(n.get('type'))
        self.assertEqual(2,len(pairs))
        self.assertTrue(all(sorted(types)==['start','stop'] for types in pairs.values()))
        self.assertEqual(['stop','start'],pairs['2'])

    def test_deleted_owner_cannot_borrow_replacement_note(self):
        value=document();value['pages'][0]['score']['slurSource']['noteOrigins'][0]=None
        self.assertEqual([],self.export(value).findall('.//slur'))

    def test_final_reversed_clock_is_rejected(self):
        value=document(spans=[arc('reverse',3,0)])
        with self.assertRaisesRegex(ValueError,'musical order'):
            self.export(value)

    def test_source_note_mismatch_is_rejected(self):
        value=document();value['pages'][0]['events'][0]['sourceNoteIndex']=1
        with self.assertRaisesRegex(ValueError,'ownership disagree'):
            self.export(value)

    def test_missing_event_cannot_shift_original_owners(self):
        value=document();value['pages'][0]['events'].pop(0)
        with self.assertRaisesRegex(ValueError,'one event'):
            self.export(value)

    def test_cross_part_span_requires_explicit_mapping(self):
        value=document()
        for obj in (value['pages'][0]['score']['notes'][3],value['pages'][0]['events'][3]):
            obj['staffIndex']=1;obj['staffCount']=2
        with self.assertRaisesRegex(ValueError,'Cross-part'):
            self.export(value)

    def test_unselected_page_endpoint_stays_unresolved(self):
        value=document();value['pages'][0]['score']['slurSource']['spans'][0]['end']['pageIndex']=10
        self.assertEqual([],self.export(value).findall('.//slur'))

    def test_cross_page_endpoint_binds_when_both_original_pages_are_selected(self):
        value=document()
        second=copy.deepcopy(value['pages'][0])
        second['score']['slurSource']['spans']=[]
        for origin in second['score']['slurSource']['noteOrigins']:
            origin['pageIndex']=10
        value['pages'][0]['score']['slurSource']['spans'][0]['end']=dict(pageIndex=10,noteIndex=0)
        value['pages'].append(second)
        notes=self.export(value).findall('.//note[pitch]')
        self.assertEqual('start',notes[0].find('notations/slur').get('type'))
        self.assertEqual('stop',notes[4].find('notations/slur').get('type'))

    def test_seventeen_overlapping_slurs_reject_before_writing(self):
        value=document(spans=[arc('phrase-'+str(i),0,3) for i in range(17)])
        with tempfile.TemporaryDirectory(prefix='original-slur-export-limit-') as folder:
            path=Path(folder)/'existing.musicxml';path.write_text('existing-output')
            with self.assertRaisesRegex(ValueError,'sixteen'):
                write_musicxml(value,path)
            self.assertEqual('existing-output',path.read_text())
