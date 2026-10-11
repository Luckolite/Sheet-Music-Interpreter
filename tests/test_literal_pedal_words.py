# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original four-quarter synthetic columns through actual export and Java policy."""
import copy
import struct
import unittest
import pathlib
import tempfile
import xml.etree.ElementTree as ET
from sheet_interpreter.performance import perform_expressions
from sheet_interpreter.musicxml import expression_direction, write_musicxml
from sheet_interpreter.midi import performance_events

def bits(value):
    return struct.unpack('>i',struct.pack('>f',value))[0]

def word(down=True,position=.2,identity='word'):
    return dict(eventId=identity,kind='PEDAL_DOWN' if down else 'PEDAL_UP',
        start=None,end=None,scope='UNRESOLVED',staffIndex=0,staffCount=1,
        targetEventId=f'expression-column:0:0:1:{bits(position)}',strength='UNSPECIFIED',qualifierText='',
        evidence=[dict(sourceId='printed-expression-word',pageIndex=0,visualX=position,
            staffIndex=0,staffCount=1,printedText='Con pedale' if down else 'Senza pedale')])

def rail(end=.6):
    target='pedal-hooks-v1:'+':'.join(map(str,[0,0,0,0,0,1,bits(end),bits(.001),0,1]))
    return [dict(word(down,identity='rail'+str(down)),targetEventId=target,
        evidence=[dict(sourceId='printed-pedal-bracket',pageIndex=0,visualX=.1,staffIndex=0,staffCount=1,printedText='')]) for down in [True,False]]

def document(marks):
    events=[dict(startBeat=i,midi=60+i,durationBeats=1,durationFallback=False,
        tiedFromPrevious=False,staffIndex=0,staffCount=1) for i in range(4)]
    raw=[dict(measureIndex=0,positionInMeasure=p,articulations=0,staffIndex=0,staffCount=1) for p in [.2,.4,.6,.8]]
    return dict(inputName='Original literal pedal controls',pages=[dict(events=events,measureBeats=[4],totalBeats=4,
        score=dict(firstMeasureNumber=1,notes=raw,rests=[],tempoChanges=[],expressiveEvents=marks))])

def result(doc):
    return perform_expressions(doc,120)['expressivePerformance'][0]

class LiteralPedalWordTests(unittest.TestCase):
    def test_literal_down_exports_words_without_inventing_bracket(self):
        root=ET.Element('measure');expression_direction(root,word(),0)
        self.assertEqual(['Con pedale'],[e.text for e in root.findall('.//words')]);self.assertEqual([],root.findall('.//pedal'))
        self.assertEqual('below',root.find('direction').get('placement'))

    def test_literal_release_preserves_full_phrase_and_offset(self):
        root=ET.Element('measure');expression_direction(root,word(False,.6),20160)
        self.assertEqual('Senza pedale',root.find('.//words').text);self.assertEqual('20160',root.find('.//offset').text)
        self.assertEqual([],root.findall('.//pedal'))

    def test_existing_bracket_keeps_bracket_notation(self):
        root=ET.Element('measure')
        for mark in rail():expression_direction(root,mark,0)
        self.assertEqual(['start','stop'],[e.get('type') for e in root.findall('.//pedal')]);self.assertEqual([],root.findall('.//words'))

    def test_literal_pair_has_finite_part_pedal_span(self):
        spans=result(document([word(),word(False,.6,'release')]))['pedalSpans']
        self.assertEqual(1,len(spans));self.assertEqual(0,spans[0]['startBeat']);self.assertEqual(2,spans[0]['endBeat'])

    def test_literal_down_cannot_guess_missing_release(self):
        proof=result(document([word()]));self.assertEqual([],proof['pedalSpans'])
        self.assertTrue(any('missing pedal release' in s for s in proof['diagnostics']))

    def test_literal_word_corroborates_finite_bracket_in_both_orders(self):
        for marks in [[word(),*rail()],[*rail(),word()]]:
            spans=result(document(marks))['pedalSpans'];self.assertEqual(1,len(spans));self.assertEqual(2,spans[0]['endBeat'])
            self.assertIn('railTrue',spans[0]['sourceId'])

    def test_later_down_does_not_extend_bracket(self):
        self.assertEqual([],result(document([*rail(),word(True,.4,'later')]))['pedalSpans'])

    def test_cached_anchor_does_not_survive_missing_column(self):
        cue=word(True,.3);cue.update(start=dict(measureIndex=0,quarterBeatOffset=0),scope='PART')
        self.assertEqual([],result(document([cue,word(False,.6,'release')]))['pedalSpans'])

    def test_cached_anchor_does_not_survive_duration_fallback(self):
        cue=word();cue.update(start=dict(measureIndex=0,quarterBeatOffset=0),scope='PART')
        doc=document([cue,word(False,.6,'release')]);doc['pages'][0]['events'][0]['durationFallback']=True
        self.assertEqual([],result(doc)['pedalSpans'])

    def test_realized_document_does_not_mutate_written_input(self):
        doc=document([word(),word(False,.6,'release')]);before=copy.deepcopy(doc)
        played=perform_expressions(doc,120);self.assertEqual(before,doc)
        self.assertEqual([60,61,62,63],[n['midi'] for n in played['pages'][0]['events']])

    def test_midi_has_finite_pedal_controls_without_note_clock_change(self):
        doc=document([word(),word(False,.6,'release')]);_,messages,_=performance_events(doc,120)
        controls=[(tick,m[2]) for tick,_,m in messages if m[0]&0xf0==0xb0 and m[1]==64]
        self.assertEqual([(0,127),(960,0)],controls)
        notes=lambda rows:[(tick,m) for tick,_,m in rows if m[0]&0xf0 in (0x80,0x90)]
        self.assertEqual(notes(performance_events(document([]),120)[1]),notes(messages))

    def xml(self,doc):
        with tempfile.TemporaryDirectory() as tmp:
            path=pathlib.Path(tmp)/'score.musicxml';write_musicxml(doc,path);return ET.parse(path)

    def test_full_musicxml_retains_both_literal_cues_and_all_four_notes(self):
        root=self.xml(document([word(),word(False,.6,'release')]))
        self.assertEqual(['Con pedale','Senza pedale'],[e.text for e in root.findall('.//words')])
        self.assertEqual([],root.findall('.//pedal'));self.assertEqual(4,len(root.findall('.//note/pitch')))

    def test_full_musicxml_drops_cached_cue_without_written_column(self):
        cue=word(True,.3);cue.update(start=dict(measureIndex=0,quarterBeatOffset=0),scope='PART')
        root=self.xml(document([cue]));self.assertEqual([],root.findall('.//words'));self.assertEqual([],root.findall('.//pedal'))

    def test_full_musicxml_drops_cached_cue_when_duration_is_unproved(self):
        cue=word();cue.update(start=dict(measureIndex=0,quarterBeatOffset=0),scope='PART')
        doc=document([cue]);doc['pages'][0]['events'][0]['durationFallback']=True
        self.assertEqual([],self.xml(doc).findall('.//words'))

    def test_cue_cannot_capture_a_different_staff_lane_from_its_target(self):
        cues=[word(),word(False,.6,'release')]
        for cue in cues:
            cue.update(staffIndex=1,staffCount=2)
            cue['evidence'][0].update(staffIndex=1,staffCount=2)
        self.assertEqual([],result(document(cues))['pedalSpans'])
        self.assertEqual([],self.xml(document(cues)).findall('.//words'))

    def test_overflowed_column_bits_cannot_wrap_onto_a_real_attack(self):
        cues=[word(),word(False,.6,'release')]
        for cue in cues:
            fields=cue['targetEventId'].split(':');fields[-1]=str(int(fields[-1])+(1<<32));cue['targetEventId']=':'.join(fields)
        self.assertEqual([],result(document(cues))['pedalSpans'])

    def test_nonfinite_column_does_not_retain_cached_anchor(self):
        cue=word();cue.update(targetEventId=f'expression-column:0:0:1:{bits(float("nan"))}',start=dict(measureIndex=0,quarterBeatOffset=0),scope='PART')
        self.assertEqual([],result(document([cue,word(False,.6,'release')]))['pedalSpans'])

    def test_malformed_column_does_not_retain_cached_anchor(self):
        cue=word();cue.update(targetEventId='expression-column:not:a:column',start=dict(measureIndex=0,quarterBeatOffset=0),scope='PART')
        self.assertEqual([],result(document([cue,word(False,.6,'release')]))['pedalSpans'])

if __name__=='__main__':unittest.main()
