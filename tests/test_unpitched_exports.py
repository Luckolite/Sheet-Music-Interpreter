# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original procedural unpitched exports; no score fixtures, model, or device dependency."""
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml, DIVISIONS
from sheet_interpreter.midi import write_midi, performance_events, UnpitchedMidiPreview

def unpitched(start=0, duration=1, **extra):
    return dict(kind='UNPITCHED',displayStep='E',displayOctave=4,clefBottomDiatonic=30,
        startBeat=start,durationBeats=duration,staffIndex=0,staffCount=1,tiedFromPrevious=False,**extra)


def document(notes, beats=(4,), **score):
    return dict(inputName='Original synthetic typed export',pages=[dict(events=notes,
        measureBeats=list(beats),totalBeats=sum(beats),score=dict(tempoChanges=[],**score))])


class UnpitchedExportTests(unittest.TestCase):
    def test_four_written_quarters_keep_four_finite_percussion_attacks_and_tail(self):
        value=document([unpitched(i) for i in range(4)])
        _,events,end=performance_events(value,unpitched_preview=UnpitchedMidiPreview(37,73,.05))
        self.assertEqual([i*480 for i in range(4)], [tick for tick,_,message in events if message[0]==0x99])
        self.assertEqual([i*480+24 for i in range(4)], [tick for tick,_,message in events if message[0]==0x89])
        self.assertEqual(1920,end)

    def xml(self, value):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'out.musicxml'; write_musicxml(value,path)
            return ET.parse(path).getroot()


    def test_unmapped_unpitched_midi_fails_before_creating_output(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'unmapped.mid'
            with self.assertRaisesRegex(ValueError,'explicit UnpitchedMidiPreview'):
                write_midi(document([unpitched()]),path)
            self.assertFalse(path.exists())


    def test_gate_is_clipped_to_written_end_and_releases_precede_next_attack(self):
        value=document([unpitched(0,.125),unpitched(.125,.125)])
        ppq,events,end=performance_events(value,unpitched_preview=UnpitchedMidiPreview(9,80,1))
        boundary=[m[0] for t,_,m in events if t==60]
        self.assertEqual([0x89,0x99],boundary)
        self.assertEqual(1920,end)


    def test_mixed_coincident_sources_are_all_written_and_routing_gap_is_explicit(self):
        pitched=dict(midi=64,startBeat=0,durationBeats=1,staffIndex=0,staffCount=1,tiedFromPrevious=False)
        value=document([pitched,unpitched(),unpitched()]); root=self.xml(value)
        self.assertEqual(1,len(root.findall('.//pitch')))
        self.assertEqual(2,len(root.findall('.//unpitched')))
        self.assertEqual(2,len(root.findall('.//chord')))
        with self.assertRaisesRegex(ValueError,'multi-address percussion routing'):
            performance_events(value,unpitched_preview=UnpitchedMidiPreview(37,80,.05))


    def test_pitched_and_unpitched_at_same_position_keep_independent_midi_channels(self):
        pitched=dict(midi=37,startBeat=0,durationBeats=1,staffIndex=0,staffCount=1,tiedFromPrevious=False)
        _,events,_=performance_events(document([pitched,unpitched()]),
            unpitched_preview=UnpitchedMidiPreview(37,80,.05))
        self.assertEqual({0x90,0x99},{m[0] for _,_,m in events if m[0]&0xf0==0x90})


    def test_explicit_unpitched_five_in_three_is_not_a_pitch_or_estimated_duration(self):
        root=self.xml(document([unpitched(0,.15,tupletActualNotes=5,tupletNormalNotes=3)],beats=(1,)))
        node=root.find('.//note')
        self.assertEqual('16th',node.findtext('type'))
        self.assertEqual('5',node.findtext('time-modification/actual-notes'))
        self.assertEqual('3',node.findtext('time-modification/normal-notes'))
        self.assertEqual('x',node.findtext('notehead'))
        self.assertEqual(str(round(.15*DIVISIONS)),node.findtext('duration'))


    def test_written_cross_bar_continuation_preserves_duration_without_midi_pitch(self):
        root=self.xml(document([unpitched(3,2)],beats=(4,4)))
        self.assertEqual(['start','stop'],[n.attrib['type'] for n in root.findall('.//tie')])
        self.assertEqual(2*DIVISIONS,sum(int(n.findtext('duration')) for n in root.findall('.//note') if n.find('unpitched') is not None))
        self.assertEqual([],root.findall('.//pitch'))


    def test_unpitched_only_part_retains_supported_display_clef(self):
        for bottom,sign,line in ((18,'F','4'),(22,'C','4'),(24,'C','3'),(30,'G','2'),(37,'G','2')):
            root=self.xml(document([dict(unpitched(),clefBottomDiatonic=bottom)]))
            self.assertEqual(sign,root.findtext('.//clef/sign'))
            self.assertEqual(line,root.findtext('.//clef/line'))
            self.assertEqual('1' if bottom==37 else None,root.findtext('.//clef/clef-octave-change'))


    def test_unknown_or_malformed_kind_and_tonal_identity_reject(self):
        cases=[dict(unpitched(),kind='OTHER'),dict(unpitched(),kind=None),
            dict(unpitched(),midi=64),dict(unpitched(),displayStep='H'),
            dict(unpitched(),displayOctave=True),dict(unpitched(),displayOctave=10),
            dict(unpitched(),boundaryTies=1),dict(unpitched(),boundaryPitch=30),
            dict(unpitched(),tiedFromPrevious=True),dict(unpitched(),glissando={'style':'white_keys','targetMidi':66})]
        for event in cases:
            with self.subTest(event=event):
                with self.assertRaises(ValueError): self.xml(document([event]))
                with self.assertRaises(ValueError): performance_events(document([event]),unpitched_preview=UnpitchedMidiPreview(37,80,.05))


    def test_finite_duration_and_explicit_policy_validation(self):
        for duration in (0,-1,float('nan'),float('inf'),5):
            with self.subTest(duration=duration):
                with self.assertRaises(ValueError): self.xml(document([unpitched(0,duration)]))
                with self.assertRaises(ValueError): performance_events(document([unpitched(0,duration)]),unpitched_preview=UnpitchedMidiPreview(37,80,.05))
        for address,velocity,gate in ((-1,80,.05),(128,80,.05),(True,80,.05),(37,0,.05),(37,128,.05),
                (37,80,0),(37,80,float('nan')),(37,80,float('inf')),(37,80,129),(37,80,True)):
            with self.assertRaises(ValueError): UnpitchedMidiPreview(address,velocity,gate)
        inconsistent=document([unpitched()]); inconsistent['pages'][0]['totalBeats']=5
        with self.assertRaisesRegex(ValueError,'extent must agree'):
            performance_events(inconsistent,unpitched_preview=UnpitchedMidiPreview(37,80,.05))




    def test_unpitched_endpoint_does_not_create_or_unblock_a_tonal_gliss(self):
        source=dict(midi=60,startBeat=0,durationBeats=1,staffIndex=0,staffCount=1,
            tiedFromPrevious=False,glissando=dict(style='white_keys',targetMidi=64))
        target=dict(midi=64,startBeat=1,durationBeats=1,staffIndex=0,staffCount=1,tiedFromPrevious=False)
        for targets in ([unpitched(1)], [unpitched(1),target]):
            root=self.xml(document([source]+targets))
            self.assertEqual([],root.findall('.//glissando'))


if __name__ == '__main__': unittest.main()
