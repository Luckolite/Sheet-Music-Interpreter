# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import xml.etree.ElementTree as ET
from sheet_interpreter.source_tempo_clock import onset, rhythmic_changes
from sheet_interpreter.musicxml import write_musicxml, DIVISIONS


def document():
    bits=struct.unpack('>i',struct.pack('>f',.46))[0]
    note=dict(measureIndex=0,positionInMeasure=.46,staffIndex=0,staffCount=1,staffStep=0,
              articulations=0,leadingRestBeats=1.5)
    event=dict(measureIndex=0,staffIndex=0,staffCount=1,sourceNoteIndex=0,midi=60,
               startBeat=1.5,durationBeats=1,durationFallback=False)
    tempo=dict(measureIndex=0,positionInMeasure=.374,bpm=96,beatUnit=.5)
    caption=dict(eventId='original-owned-tempo',kind='UNRESOLVED_DIRECTION',scope='SCORE',
        start=dict(measureIndex=0,quarterBeatOffset=1.5),end=None,staffIndex=0,staffCount=1,
        targetEventId=f'expression-column:0:0:1:{bits}',strength='UNSPECIFIED',qualifierText='',
        evidence=[dict(sourceId='printed-expression-word',pageIndex=0,visualX=.4244,
                       staffIndex=0,staffCount=1,printedText='Quietly flowing')])
    return dict(initialMeter=[4,4],initialKeyFifths=0,pages=[dict(measureBeats=[4],totalBeats=4,
        events=[event],score=dict(measures=[dict(left=.2,right=.8,top=.2,bottom=.5)],notes=[note],
                                 tempoChanges=[tempo],expressiveEvents=[caption]))])


class SourceTempoClockTests(unittest.TestCase):
    def setUp(self):self.doc=document();self.page=self.doc['pages'][0];self.tempo=self.page['score']['tempoChanges'][0]
    def test_exact_visual_source_caption_supplies_written_clock(self):self.assertEqual(1.5,onset(self.page,self.tempo))
    def test_another_numeric_record_at_same_position_cannot_borrow_caption(self):
        self.assertIsNone(onset(self.page,dict(self.tempo,bpm=104)))
    def test_raw_unresolved_caption_uses_original_owned_column(self):
        self.page['score']['expressiveEvents'][0].update(start=None,scope='UNRESOLVED')
        self.assertEqual(1.5,onset(self.page,self.tempo))
    def test_unrelated_distant_caption_cannot_move_numeric_tempo(self):
        self.page['score']['expressiveEvents'][0]['start']['quarterBeatOffset']=0
        self.assertIsNone(onset(self.page,self.tempo))
    def test_edited_event_clock_overrides_stale_caption(self):
        self.page['events'][0]['startBeat']=2;self.assertEqual(2,onset(self.page,self.tempo))
    def test_nearby_caption_never_supplies_clock(self):
        self.page['score']['expressiveEvents'][0]['evidence'][0]['visualX']+=.0001
        self.assertIsNone(onset(self.page,self.tempo))
    def test_unowned_target_abstains(self):
        self.page['score']['expressiveEvents'][0]['targetEventId']='unowned';self.assertIsNone(onset(self.page,self.tempo))
    def test_conflicting_caption_claims_abstain(self):
        other=copy.deepcopy(self.page['score']['expressiveEvents'][0]);other['eventId']='other-caption'
        self.page['score']['expressiveEvents'].append(other);self.assertIsNone(onset(self.page,self.tempo))
    def test_multiple_numeric_marks_at_identical_visual_identity_abstain(self):
        self.page['score']['tempoChanges'].append(dict(self.tempo,bpm=104));self.assertIsNone(onset(self.page,self.tempo))
    def test_contradictory_owned_head_clocks_abstain(self):
        self.page['score']['notes'].append(dict(self.page['score']['notes'][0],staffStep=2))
        self.page['events'].append(dict(self.page['events'][0],sourceNoteIndex=1,midi=64,startBeat=2))
        self.assertIsNone(onset(self.page,self.tempo))
    def test_mismatched_source_identity_abstains(self):
        self.page['events'][0]['sourceNoteIndex']=2;self.assertIsNone(onset(self.page,self.tempo))
    def test_estimated_timing_abstains(self):
        self.page['events'][0]['durationFallback']=True;self.assertIsNone(onset(self.page,self.tempo))
    def test_rhythmic_projection_preserves_rate_pulse_and_input(self):
        before=copy.deepcopy(self.page);self.assertEqual(dict(self.tempo,positionInMeasure=.375),rhythmic_changes(self.page)[0]);self.assertEqual(before,self.page)
    def test_unknown_numeric_record_stays_unchanged(self):
        self.page['score']['expressiveEvents']=[];self.assertIs(self.tempo,rhythmic_changes(self.page)[0])

    def test_real_xml_writer_aligns_visible_and_sound_offsets(self):
        before=copy.deepcopy(self.doc)
        with tempfile.TemporaryDirectory(prefix='original-owned-tempo-')as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(self.doc,path);root=ET.parse(path).getroot()
        mark=next(d for d in root.findall('part/measure/direction')if d.find("sound[@tempo='96']")is not None)
        offset=mark.find('offset');self.assertEqual(str(1.5*DIVISIONS).split('.')[0],offset.text)
        self.assertEqual('yes',offset.get('sound'));self.assertEqual(before,self.doc)

    def test_real_performance_bridge_receives_rhythmic_owned_tempo(self):
        from sheet_interpreter.performance import _perform_one
        class Captured(Exception):pass
        before=copy.deepcopy(self.page);seen=[]
        def capture(payload):
            magic,bpm,count=struct.unpack_from('>idi',payload);self.assertEqual(0x45585031,magic)
            index=16+8*count;numeric=struct.unpack_from('>i',payload,index)[0];self.assertEqual(1,numeric)
            m,p,rate,pulse=struct.unpack_from('>ifdd',payload,index+4);seen.append(p)
            self.assertEqual((0,.375,96,.5),(m,p,rate,pulse));raise Captured
        with patch('sheet_interpreter.performance._bridge',side_effect=capture):
            with self.assertRaises(Captured):_perform_one([self.page],120)
        self.assertEqual([.375],seen);self.assertEqual(before,self.page)

    def test_unknown_source_keeps_visible_offset_and_applies_it_to_sound(self):
        self.page['score']['expressiveEvents']=[]
        with tempfile.TemporaryDirectory(prefix='original-unknown-tempo-')as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(self.doc,path);root=ET.parse(path).getroot()
        mark=next(d for d in root.findall('part/measure/direction')if d.find("sound[@tempo='96']")is not None)
        self.assertEqual(str(round(4*DIVISIONS*.374)),mark.findtext('offset'))
        self.assertEqual('yes',mark.find('offset').get('sound'))
