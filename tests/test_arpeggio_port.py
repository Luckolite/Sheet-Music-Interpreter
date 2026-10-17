# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original procedural source columns and timelines; no private score fixtures."""
import copy
import math
from pathlib import Path
import struct
import tempfile
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter.arpeggio_notation import target_indices, plan, _float32
from sheet_interpreter.performance import _column_members, SUPPORTED
from sheet_interpreter.musicxml import write_musicxml, emit_note


def mark(position=.35):
    return dict(eventId='original-arpeggio',kind='ARPEGGIO',start=None,end=None,scope='UNRESOLVED',
                staffIndex=0,staffCount=2,targetEventId='arpeggio-column-v1:0:0:2:'+_float32(position).hex()+':0,2,4',
                strength='UNSPECIFIED',qualifierText='',evidence=[dict(sourceId='printed-arpeggio-wave',pageIndex=0,
                visualX=.2,staffIndex=0,staffCount=2,printedText='arpeggio')])


def document():
    raw=[dict(kind='PITCHED',measureIndex=0,positionInMeasure=.35,staffIndex=0,staffCount=2,
              staffStep=step,articulations=0,beamCount=0,augmentationDots=0,unbeamedDurationBeats=2,
              tupletDivisor=1,tupletNormalNotes=1)for step in [0,2,4]]
    raw.append(dict(raw[0],positionInMeasure=.8,staffStep=1))
    events=[dict(kind='PITCHED',measureIndex=0,startBeat=0 if i<3 else 2,durationBeats=2,
                 durationFallback=False,tiedFromPrevious=False,staffIndex=0,staffCount=2,midi=pitch,
                 sourceNoteIndex=i)for i,pitch in enumerate([60,64,67,62])]
    return dict(inputName='Original procedural arpeggio',pages=[dict(events=events,measureBeats=[4],totalBeats=4,
                score=dict(measures=[dict(left=.05,right=.95,top=.1,bottom=.9)],firstMeasureNumber=1,
                           notes=raw,meterChanges=[dict(measureIndex=0,numerator=4,denominator=4)],
                           tempoChanges=[],expressiveEvents=[mark()]))])


class ArpeggioIdentityTests(unittest.TestCase):
    def setUp(self):self.page=document()['pages'][0];self.expression=self.page['score']['expressiveEvents'][0]
    def test_canonical_half_note_three_head_column(self):self.assertEqual([0,1,2],plan(self.expression,self.page))
    def test_performance_dispatch_keeps_notation_only(self):
        self.assertEqual([0,1,2],_column_members(self.expression,self.page));self.assertNotIn('ARPEGGIO',SUPPORTED)
    def test_reordered_source_identity(self):
        for key in ['notes']:
            a=self.page['score'][key];a[0],a[2]=a[2],a[0]
        self.page['events'][0],self.page['events'][2]=self.page['events'][2],self.page['events'][0]
        for i,e in enumerate(self.page['events']):e['sourceNoteIndex']=i
        self.assertEqual([0,1,2],plan(self.expression,self.page))
    def test_unsorted_unique_steps_follow_java_parser(self):
        self.expression['targetEventId']=self.expression['targetEventId'].rsplit(':',1)[0]+':4,0,2'
        self.assertEqual([0,1,2],target_indices(self.expression,self.page))
    def test_float32_threshold_and_jitter(self):
        position=_float32(.35);limit=_float32(.018)
        # Adjacent float32 positions bracket the exact Java subtraction threshold.
        bits=struct.unpack('>I',struct.pack('>f',position+limit))[0]
        found=[]
        for b in range(bits-2,bits+3):
            candidate=struct.unpack('>f',struct.pack('>I',b))[0]
            expected=abs(_float32(candidate-position))<=limit
            self.page['score']['notes'][2]['positionInMeasure']=candidate
            self.assertEqual(expected,bool(target_indices(self.expression,self.page)))
            found.append(expected)
        self.assertIn(True,found);self.assertIn(False,found)
    def test_malformed_targets_fail_closed(self):
        original=self.expression['targetEventId']
        for target in ['arpeggio-column-v1:0:0:2:0.35:0,2,4','arpeggio-column-v1:0:0:2:NaN:0,2,4',
                       'arpeggio-column-v1:0:0:2:0x1p9999:0,2,4',original+':extra',original.replace(':0:0:2:',':2147483648:0:2:'),
                       original.rsplit(':',1)[0]+':0,2,2',original.rsplit(':',1)[0]+':0']:
            self.expression['targetEventId']=target;self.assertEqual([],target_indices(self.expression,self.page),target)
    def test_wrong_kind_source_or_physical_staff(self):
        for path,value in [('kind','FERMATA'),('staffIndex',1),('staffCount',1)]:
            x=copy.deepcopy(self.expression);x[path]=value;self.assertEqual([],target_indices(x,self.page))
        for key,value in [('sourceId','printed-expression-word'),('staffIndex',1),('staffCount',1)]:
            x=copy.deepcopy(self.expression);x['evidence'][0][key]=value;self.assertEqual([],target_indices(x,self.page))
    def test_extra_missing_duplicate_head_invalidates_complete_column(self):
        for op in ['extra','missing','duplicate']:
            p=copy.deepcopy(self.page)
            if op=='extra':p['score']['notes'].append(dict(p['score']['notes'][0],staffStep=6))
            elif op=='missing':p['score']['notes'].pop(1)
            else:p['score']['notes'].append(dict(p['score']['notes'][0]))
            self.assertEqual([],target_indices(self.expression,p),op)
    def test_grace_and_unpitched_cannot_complete_chord(self):
        for key,value in [('articulations',32768),('kind','UNPITCHED')]:
            p=copy.deepcopy(self.page);p['score']['notes'][0][key]=value;self.assertEqual([],plan(self.expression,p))
    def test_partial_available_chord_abstains(self):
        self.assertEqual([],plan(self.expression,self.page,[0,1,3]));self.assertEqual([0,1,2],plan(self.expression,self.page,[0,1,2]))
    def test_unknown_mismatched_or_fallback_clock_abstains(self):
        for key,value in [('durationFallback',True),('durationBeats',1),('startBeat',.1),('sourceNoteIndex',8),('measureIndex',1),('staffCount',1)]:
            p=copy.deepcopy(self.page);p['events'][1][key]=value;self.assertEqual([],plan(self.expression,p),key)
        p=copy.deepcopy(self.page);p['measureBeats']=[];self.assertEqual([],plan(self.expression,p))
    def test_source_event_count_mismatch_abstains(self):
        self.page['events'].pop();self.assertEqual([],plan(self.expression,self.page))
    def test_canonical_nonfallback_decoder_clock_needs_no_raw_rhythm(self):
        self.page['score']['notes'][0].pop('unbeamedDurationBeats')
        for event in self.page['events']:event.pop('sourceNoteIndex')
        self.assertEqual([0,1,2],plan(self.expression,self.page))
    def test_valid_tuplet_written_clock(self):
        for i in [0,1,2]:
            self.page['score']['notes'][i].update(beamCount=1,tupletDivisor=3,tupletNormalNotes=2)
            self.page['events'][i]['durationBeats']=1/3
        self.assertEqual([0,1,2],plan(self.expression,self.page))


class ArpeggioXmlTests(unittest.TestCase):
    def export(self,doc):
        old=copy.deepcopy(doc)
        with tempfile.TemporaryDirectory()as tmp:
            p=Path(tmp)/'original.musicxml';write_musicxml(doc,p);text=p.read_text(encoding='utf-8')
        self.assertEqual(old,doc);return ET.fromstring(text)
    def test_all_three_exact_heads_and_no_generic_words_or_roll(self):
        doc=document();plain=copy.deepcopy(doc);plain['pages'][0]['score']['expressiveEvents']=[]
        a=self.export(plain);b=self.export(doc);marks=b.findall('.//arpeggiate');self.assertEqual(3,len(marks))
        self.assertTrue(all(not m.attrib for m in marks));self.assertEqual([],b.findall('.//words'))
        for note in b.findall('.//note'):
            notation=note.find('notations')
            if notation is not None and notation.find('arpeggiate')is not None:
                notation.remove(notation.find('arpeggiate'))
                if not len(notation):note.remove(notation)
        def exact_tree(e):
            return (e.tag,tuple(sorted(e.attrib.items())),e.text if e.text and not e.text.isspace() else None,tuple(exact_tree(c)for c in e))
        self.assertEqual(exact_tree(a),exact_tree(b))
    def test_forged_event_flag_without_source_evidence_cannot_emit(self):
        doc=document();doc['pages'][0]['score']['expressiveEvents']=[]
        for e in doc['pages'][0]['events']:e['retainedArpeggio']=True
        self.assertEqual([],self.export(doc).findall('.//arpeggiate'))
    def test_wrong_staff_and_partial_filter_emit_nothing(self):
        for change in ['staff','partial','unknown']:
            doc=document();p=doc['pages'][0]
            if change=='staff':p['score']['expressiveEvents'][0]['staffCount']=1
            elif change=='partial':p['events'].pop(1)
            else:p['events'][0]['durationFallback']=True
            self.assertEqual([],self.export(doc).findall('.//arpeggiate'),change)
    def test_duplicate_event_is_idempotent_in_export(self):
        doc=document();p=doc['pages'][0];p['score']['expressiveEvents']*=2
        self.assertEqual(3,len(self.export(doc).findall('.//arpeggiate')))
    def test_split_note_only_original_attack_fragment(self):
        # Exercise exact exporter fragment contract without inventing source timing.
        measure=ET.Element('measure');event=dict(midi=60,retainedArpeggio=True)
        emit_note(measure,10080,1,event,fragment_end=10080,attack_fragment=True,start=True)
        emit_note(measure,2520,1,event,fragment_end=12600,attack_fragment=False,stop=True)
        self.assertIsNotNone(measure.findall('note')[0].find('notations/arpeggiate'))
        self.assertIsNone(measure.findall('note')[1].find('notations/arpeggiate'))
    def test_grace_fragment_does_not_emit_even_with_internal_flag(self):
        m=ET.Element('measure');emit_note(m,5040,1,dict(midi=60,retainedArpeggio=True),grace=dict(afterGrace=False,stealPercent=5))
        self.assertEqual([],m.findall('.//arpeggiate'))
    def test_other_expression_identity_unchanged(self):
        doc=document();mark=dict(doc['pages'][0]['score']['expressiveEvents'][0]);mark.update(kind='FERMATA',targetEventId='printed-attack:0:0:2:1051931443')
        mark['evidence'][0]['sourceId']='fermata-raw-ink'
        self.assertEqual([0,1,2],_column_members(mark,doc['pages'][0]))


if __name__=='__main__':unittest.main()
