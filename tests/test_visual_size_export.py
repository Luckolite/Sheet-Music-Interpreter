# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original sounding cue-size and printed cue ownership export contracts."""
import copy
import struct
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as E
from sheet_interpreter.musicxml import write_musicxml
from sheet_interpreter import cue_caption

def document():
    notes = [dict(measureIndex=0, positionInMeasure=p, staffIndex=0, staffCount=1,
                  staffStep=i*2, articulations=0, kind='PITCHED', visualSize='CUE' if i==0 else 'FULL')
             for i,p in enumerate((.12,.82))]
    events = [dict(measureIndex=0, staffIndex=0, staffCount=1, sourceNoteIndex=i,
                   midi=60+i*4, startBeat=i*2, durationBeats=2, durationFallback=False)
              for i in range(2)]
    return dict(initialMeter=[4,4],initialKeyFifths=0,pages=[dict(measureBeats=[4],events=events,
                score=dict(notes=notes,expressiveEvents=[]))])

def caption(doc,index,text):
    n=doc['pages'][0]['score']['notes'][index]
    bits=struct.unpack('>i',struct.pack('>f',n['positionInMeasure']))[0]
    return dict(eventId='original-cue-'+str(index),kind='UNRESOLVED_DIRECTION',scope='PART',
                staffIndex=0,staffCount=1,start=dict(measureIndex=0,quarterBeatOffset=index*2),end=None,
                targetEventId=f'expression-column:0:0:1:{bits}',qualifierText='',strength='UNSPECIFIED',
                evidence=[dict(sourceId=cue_caption.SOURCE,pageIndex=0,visualX=.2+index*.5,
                               staffIndex=0,staffCount=1,printedText=text)])

class VisualSizeExportTests(unittest.TestCase):
    def xml(self,d):
        before=copy.deepcopy(d)
        with tempfile.TemporaryDirectory(prefix='original-visual-size-')as directory:
            p=Path(directory)/'score.musicxml';write_musicxml(d,p);root=E.parse(p).getroot()
        self.assertEqual(before,d)
        return root
    def test_small_note_is_graphic_size_with_ordinary_sound_and_clock(self):
        root=self.xml(document());notes=root.findall('.//note[pitch]')
        self.assertEqual('cue',notes[0].find('type').get('size'))
        self.assertEqual('20160',notes[0].findtext('duration'))
        self.assertEqual([],root.findall('.//cue'));self.assertEqual([],root.findall('.//grace'))
        self.assertEqual(2,len(notes))
    def test_full_size_keeps_implicit_default(self):
        d=document()
        for n in d['pages'][0]['score']['notes']:n['visualSize']='FULL'
        self.assertTrue(all(t.get('size')is None for t in self.xml(d).findall('.//type')))
    def test_mixed_chord_retains_per_head_size(self):
        d=document();p=d['pages'][0];p['score']['notes'][1]['positionInMeasure']=.12;p['events'][1]['startBeat']=0
        notes=self.xml(d).findall('.//note[pitch]')
        self.assertEqual(['cue',None],[n.find('type').get('size')for n in notes])
    def test_reordered_source_identity_does_not_borrow_visual_style(self):
        d=document();d['pages'][0]['events'][0]['sourceNoteIndex']=1
        self.assertTrue(all(t.get('size')is None for t in self.xml(d).findall('.//type')))
    def test_foreign_staff_does_not_borrow_visual_style(self):
        d=document();d['pages'][0]['events'][0]['staffCount']=2
        self.assertTrue(all(t.get('size')is None for t in self.xml(d).findall('.//type')))
    def test_unknown_visual_classification_is_rejected(self):
        d=document();d['pages'][0]['score']['notes'][0]['visualSize']='GRACE'
        with self.assertRaisesRegex(ValueError,'visual size'):self.xml(d)
    def test_cue_captions_follow_edited_written_owner_clocks(self):
        d=document();p=d['pages'][0];p['score']['expressiveEvents']=[caption(d,0,'Guitar'),caption(d,1,'Play')]
        p['events'][0]['durationBeats']=1;p['events'][1].update(startBeat=1,durationBeats=3)
        words=[(v.findtext('direction-type/words'),int(v.findtext('offset','0'))/10080)
               for v in self.xml(d).findall('.//direction')if v.find('direction-type/words')is not None]
        self.assertEqual([('Guitar',0),('Play',1)],words)
    def test_missing_source_owner_cannot_reuse_stale_resolved_caption(self):
        d=document();p=d['pages'][0];p['score']['expressiveEvents']=[caption(d,1,'Play')]
        p['events'][1]['sourceNoteIndex']=99
        self.assertEqual([],self.xml(d).findall('.//words'))
    def test_nearby_column_cannot_steal_exact_cue_caption_owner(self):
        d=document();p=d['pages'][0];p['score']['notes'][1]['positionInMeasure']=.13
        p['score']['expressiveEvents']=[caption(d,0,'Guitar')]
        words=[(v.findtext('direction-type/words'),int(v.findtext('offset','0')))
               for v in self.xml(d).findall('.//direction')if v.find('direction-type/words')is not None]
        self.assertEqual([('Guitar',0)],words)
    def test_generic_word_evidence_is_not_typed_cue_evidence(self):
        d=document();e=caption(d,0,'Guitar');self.assertTrue(cue_caption.owns(e))
        e['evidence'][0]['sourceId']='printed-expression-word';self.assertFalse(cue_caption.owns(e))

if __name__=='__main__':unittest.main()
