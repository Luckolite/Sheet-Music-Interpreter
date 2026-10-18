# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original semantic records, no private source pixels or score geometry."""
import copy,tempfile,unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml
from sheet_interpreter.midi import performance_events

def document():
    notes=[dict(measureIndex=0,positionInMeasure=x,staffStep=step,staffIndex=0,staffCount=1,pageY=.4,
                articulations=flags,clefBottomDiatonic=30,writtenAccidental=2,unbeamedDurationBeats=duration,
                beamCount=0,augmentationDots=0,followingRestBeats=0.,leadingRestBeats=0.,tiedFromPrevious=False)
           for x,step,flags,duration in ((.2,5,0,2.),(.8,6,16576,1.))]
    events=[dict(measureIndex=0,staffIndex=0,staffCount=1,midi=midi,startBeat=start,durationBeats=duration,
                 durationFallback=False,tiedFromPrevious=False,clefBottomDiatonic=30)
            for midi,start,duration in ((72,0.,2.),(74,2.,1.))]
    events[1]['slide']=dict(style='continuous',sourceNoteIndex=0,sourceMidi=72)
    return dict(inputName='Original straight-line control',pages=[dict(events=events,measureBeats=[3.],totalBeats=3.,
              score=dict(notes=notes,measures=[dict(left=.1,right=.9,top=.3,bottom=.5)],rests=[],tempoChanges=[],
                         meterChanges=[dict(measureIndex=0,numerator=3,denominator=4)],keyChanges=[dict(measureIndex=0,fifths=0)]))])
def export(d):
    with tempfile.TemporaryDirectory()as t:
        p=Path(t)/'test.musicxml';write_musicxml(d,p,meter=(3,4),key_fifths=0,bpm=80)
        return ET.parse(p).getroot()
class StraightSlideConsumerTests(unittest.TestCase):
    def test_paired_solid_endpoints_no_other_ornament_and_no_clock_mutation(self):
        d=document();before=copy.deepcopy(d);r=export(d);self.assertEqual(d,before)
        self.assertEqual(['start','stop'],[n.get('type')for n in r.findall('.//slide')])
        self.assertEqual({'solid'},{n.get('line-type')for n in r.findall('.//slide')})
        self.assertEqual(0,len(r.findall('.//glissando')));self.assertEqual(0,len(r.findall('.//other-ornament')))
        self.assertEqual(['C','D'],[n.findtext('pitch/step')for n in r.findall('.//note')if n.find('rest')is None])
    def test_no_metadata_raw_flag_does_not_guess_pair(self):
        d=document();del d['pages'][0]['events'][1]['slide'];self.assertEqual(0,len(export(d).findall('.//slide')))
    def test_wavy_gliss_remains_wavy(self):
        d=document();p=d['pages'][0];del p['events'][1]['slide'];p['score']['notes'][1]['articulations']=0;p['score']['notes'][0]['articulations']=224
        p['events'][0]['glissando']=dict(style='white_keys',targetMidi=74)
        self.assertEqual(2,len(export(d).findall('.//glissando')))
    def invalid(self,change):
        d=document();change(d['pages'][0]);
        with self.assertRaises(ValueError):export(d)
    def test_bad_source_midi_rejected(self):self.invalid(lambda p:p['events'][1]['slide'].update(sourceMidi=71))
    def test_boolean_source_index_rejected(self):self.invalid(lambda p:p['events'][1]['slide'].update(sourceNoteIndex=False))
    def test_self_reference_rejected(self):self.invalid(lambda p:p['events'][1]['slide'].update(sourceNoteIndex=1))
    def test_partial_document_rejected(self):self.invalid(lambda p:p['score']['notes'].pop())
    def test_stale_source_index_rejected(self):self.invalid(lambda p:p['events'][1].update(sourceNoteIndex=0))
    def test_wrong_staff_rejected(self):self.invalid(lambda p:p['events'][1].update(staffIndex=1))
    def test_unpitched_source_rejected(self):self.invalid(lambda p:p['events'][0].update(kind='UNPITCHED'))
    def test_unknown_clock_rejected(self):self.invalid(lambda p:p['events'][0].update(durationFallback=True))
    def test_rest_gap_rejected(self):self.invalid(lambda p:p['events'][0].update(durationBeats=1.5))
    def test_grace_source_rejected(self):self.invalid(lambda p:p['score']['notes'][0].update(articulations=32768))
    def test_unconnected_metadata_rejected(self):self.invalid(lambda p:p['score']['notes'][1].update(articulations=192))
