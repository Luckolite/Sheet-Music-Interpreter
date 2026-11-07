# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original decoded examples through the real Java policy, MIDI and PCM renderer."""
import copy
import math
import struct
import unittest
import numpy as np
from sheet_music_recognition.performance import perform_expressions
from sheet_music_recognition.midi import performance_events
from sheet_music_recognition.audio import _sample_events, _pcm_blocks, SAMPLE_RATE


def expression(kind, start=0, end=None, scope='SCORE', target=None, source='original-synthetic', staff=0):
    anchor = lambda value: dict(measureIndex=int(value//4), quarterBeatOffset=value%4)
    return dict(eventId=kind+str(staff), kind=kind, start=anchor(start), end=None if end is None else anchor(end),
                scope=scope, staffIndex=staff, staffCount=1 if staff == 0 else 2, targetEventId=target,
                strength='UNSPECIFIED', qualifierText='', evidence=[dict(sourceId=source,pageIndex=0,visualX=.1,
                staffIndex=staff,staffCount=1 if staff == 0 else 2,printedText=kind)])


def document(expressions=()):
    notes = [dict(startBeat=start, midi=pitch, durationBeats=2, durationFallback=False,
                  tiedFromPrevious=False,staffIndex=0,staffCount=1) for start,pitch in [(0,69),(2,71)]]
    raw = [dict(measureIndex=0, positionInMeasure=position, staffIndex=0, staffCount=1) for position in [.1,.9]]
    return dict(pages=[dict(events=notes,measureBeats=[4],totalBeats=4,
                score=dict(firstMeasureNumber=1,notes=raw,tempoChanges=[],expressiveEvents=list(expressions)))])


def fermata():
    bits=struct.unpack('>i',struct.pack('>f',.1))[0]
    return expression('FERMATA',0,2,'NOTE',f'printed-attack:0:0:1:{bits}','fermata-raw-ink')


def rest_fermata_document(duration=4, supplied_scope='UNRESOLVED'):
    bits=struct.unpack('>i',struct.pack('>f',.5))[0]
    mark=expression('FERMATA',scope=supplied_scope,target=f'printed-rest:0:0:1:{bits}',source='fermata-rest-raw-ink')
    mark['start']=mark['end']=None
    doc=document([mark]);page=doc['pages'][0];page['events']=[];page['score']['notes']=[]
    page['score']['rests']=[dict(measureIndex=0,positionInMeasure=.5,staffIndex=0,staffCount=1,durationBeats=duration)]
    return doc


class ExpressivePerformanceTests(unittest.TestCase):
    def test_raw_rest_fermata_resolves_only_exact_silent_slot(self):
        doc=rest_fermata_document();original=copy.deepcopy(doc);events,end=_sample_events(doc,120)
        self.assertFalse(any(msg[0]&0xf0==0x90 for _,msg in events));self.assertEqual(4*SAMPLE_RATE,end)
        self.assertEqual(original,doc)
        unresolved=rest_fermata_document(1);events,end=_sample_events(unresolved,120)
        self.assertEqual(2*SAMPLE_RATE,end)
        self.assertIn('unresolved',perform_expressions(unresolved,120)['expressivePerformance'][0]['diagnostics'][0])
    def test_no_expression_keeps_legacy_export(self):
        self.assertIsNone(perform_expressions(document(),120))
        self.assertEqual(1920,performance_events(document(),120)[2])

    def test_ritardando_uses_real_shared_clock_without_mutating_source(self):
        doc=document([expression('RITARDANDO',0,4)]); original=copy.deepcopy(doc)
        events,end=_sample_events(doc,120)
        self.assertAlmostEqual(10*math.log(1.25)*SAMPLE_RATE,end,delta=30)
        second=[at for at,msg in events if msg[0]&0xf0==0x90][1]
        self.assertAlmostEqual(10*math.log(1/.9)*SAMPLE_RATE,second,delta=30)
        self.assertEqual(original,doc)

    def test_rite_is_immediate(self):
        events,end=_sample_events(document([expression('RITENUTO',0)]),120)
        self.assertAlmostEqual(2.5*SAMPLE_RATE,end,delta=30)

    def test_fermata_holds_owned_note_and_delays_following_attack(self):
        doc=document([fermata()]); events,end=_sample_events(doc,120)
        starts=[at for at,msg in events if msg[0]&0xf0==0x90]
        stops=[at for at,msg in events if msg[0]&0xf0==0x80]
        self.assertEqual([0,2*SAMPLE_RATE],starts)
        self.assertEqual([2*SAMPLE_RATE,3*SAMPLE_RATE],stops)
        self.assertEqual(3*SAMPLE_RATE,end)

    def test_breath_releases_note_before_pause(self):
        events,end=_sample_events(document([expression('BREATH',2,scope='PART')]),120)
        starts=[at for at,msg in events if msg[0]&0xf0==0x90]
        stops=[at for at,msg in events if msg[0]&0xf0==0x80]
        self.assertEqual(SAMPLE_RATE,stops[0])
        self.assertAlmostEqual(1.125*SAMPLE_RATE,starts[1],delta=30)
        self.assertAlmostEqual(2.125*SAMPLE_RATE,end,delta=30)

    def test_raster_symbol_column_binds_to_original_sound(self):
        bits=struct.unpack('>i',struct.pack('>f',.1))[0]
        mark=expression('BREATH',2,scope='PART',target=f'expression-column:0:0:1:{bits}',
                        source='printed-expression-symbol')
        doc=document([mark]);original=copy.deepcopy(doc)
        events,end=_sample_events(doc,120)
        self.assertEqual(SAMPLE_RATE,[at for at,msg in events if msg[0]&0xf0==0x80][0])
        self.assertAlmostEqual(1.125*SAMPLE_RATE,[at for at,msg in events if msg[0]&0xf0==0x90][1],delta=30)
        self.assertEqual(original,doc)

    def test_tremolo_attacks_follow_written_subdivisions_under_tempo_curve(self):
        doc=document([expression('RITARDANDO',0,4)])
        doc['pages'][0]['events'][0]['tremoloBeats']=.5
        original=copy.deepcopy(doc);events,end=_sample_events(doc,120)
        starts=[at for at,msg in events if msg[0]&0xf0==0x90]
        self.assertEqual(5,len(starts))
        for actual,beat in zip(starts,[0,.5,1,1.5,2]):
            self.assertAlmostEqual(10*math.log(1/(1-.05*beat))*SAMPLE_RATE,actual,delta=30)
        self.assertEqual(original,doc)

    def test_tremolo_final_strike_sustains_fermata_without_extra_attacks(self):
        doc=document([fermata()]);doc['pages'][0]['events'][0]['tremoloBeats']=.5
        events,end=_sample_events(doc,120)
        starts=[at for at,msg in events if msg[0]&0xf0==0x90]
        stops=[at for at,msg in events if msg[0]&0xf0==0x80]
        self.assertEqual([0,SAMPLE_RATE//4,SAMPLE_RATE//2,3*SAMPLE_RATE//4,2*SAMPLE_RATE],starts)
        self.assertEqual(2*SAMPLE_RATE,stops[3]);self.assertEqual(3*SAMPLE_RATE,end)

    def test_tremolo_breath_release_and_sforzando_are_single_owned_events(self):
        doc=document([expression('BREATH',2,scope='PART'),expression('SFORZATO',0,scope='NOTE')])
        doc['pages'][0]['events'][0]['tremoloBeats']=.5
        events,end=_sample_events(doc,120)
        starts=[(at,msg[2]) for at,msg in events if msg[0]&0xf0==0x90]
        stops=[at for at,msg in events if msg[0]&0xf0==0x80]
        self.assertEqual([127,80,80,80,80],[velocity for _,velocity in starts])
        self.assertEqual(SAMPLE_RATE,stops[3]);self.assertAlmostEqual(1.125*SAMPLE_RATE,starts[4][0],delta=30)

    def test_repeat_replays_hold_once_per_visit(self):
        doc=document([fermata()]); doc['pages'][0]['score']['playbackDirections']=[
            dict(measureBoundary=0,kind=4),dict(measureBoundary=1,kind=5)]
        events,end=_sample_events(doc,120)
        self.assertEqual([0,2*SAMPLE_RATE,3*SAMPLE_RATE,5*SAMPLE_RATE],
                         [at for at,msg in events if msg[0]&0xf0==0x90])
        self.assertEqual(6*SAMPLE_RATE,end)

    def test_sforzando_reaches_midi_expression_and_pcm_without_changing_next_note(self):
        base=document(); emphasized=document([expression('SFORZATO',scope='NOTE')])
        _,messages,_=performance_events(emphasized,120)
        controllers=[msg[2] for _,_,msg in messages if msg[0]&0xf0==0xb0 and msg[1]==11]
        self.assertIn(127,controllers);self.assertIn(79,controllers)
        self.assertEqual([127,80],[msg[2] for _,_,msg in messages if msg[0]&0xf0==0x90])
        def pcm(doc):
            events,end=_sample_events(doc,120)
            return np.frombuffer(b''.join(_pcm_blocks(events,end)),dtype='<f4')
        a,b=pcm(base),pcm(emphasized)
        rms=lambda value:np.sqrt(np.mean(value.astype(float)**2))
        self.assertGreater(rms(b[500:1500]),rms(a[500:1500])*1.25)
        self.assertAlmostEqual(rms(b[26000:28000])/rms(a[26000:28000]),1,delta=.02)

    def test_unresolved_fermata_does_not_invent_a_hold(self):
        doc=document([fermata()]);doc['pages'][0]['events'][0]['durationFallback']=True
        # Resolved-looking external anchors alone cannot replace the owned sound binding.
        performed=perform_expressions(doc,120)
        self.assertTrue(performed['expressivePerformance'][0]['diagnostics'])
        self.assertEqual(2,performed['expressivePerformance'][0]['durationSeconds'])

    def test_rest_fermata_is_silent(self):
        doc=document([expression('FERMATA',2,4,'REST','rest-original')]);doc['pages'][0]['events']=doc['pages'][0]['events'][:1]
        doc['pages'][0]['score']['notes']=doc['pages'][0]['score']['notes'][:1]
        events,end=_sample_events(doc,120)
        self.assertEqual([SAMPLE_RATE],[at for at,msg in events if msg[0]&0xf0==0x80])
        self.assertEqual(3*SAMPLE_RATE,end)

    def test_metric_modulation_uses_previous_tempo(self):
        mark=expression('METRIC_MODULATION',2)
        mark['qualifierText']='metric-pulse-v1:1.5:1.0'
        events,end=_sample_events(document([mark]),120)
        self.assertAlmostEqual(2.5*SAMPLE_RATE,end,delta=30)
        self.assertEqual([0,SAMPLE_RATE],[at for at,msg in events if msg[0]&0xf0==0x90])

    def test_metric_modulation_is_performed_each_repeat_without_compounding(self):
        mark=expression('METRIC_MODULATION',0);mark['qualifierText']='metric-pulse-v1:1.5:1.0'
        doc=document([mark]);doc['pages'][0]['score']['playbackDirections']=[dict(measureBoundary=0,kind=4),dict(measureBoundary=1,kind=5)]
        events,end=_sample_events(doc,120)
        self.assertEqual(6*SAMPLE_RATE,end)
