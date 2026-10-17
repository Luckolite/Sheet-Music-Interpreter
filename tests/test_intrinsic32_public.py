# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original scalar concert-pitch and intrinsic-clef fixtures; no score data."""
import copy
from pathlib import Path
import struct,tempfile,unittest,xml.etree.ElementTree as ET
from sheet_interpreter import clef_export,spelling_export,typed_events,guide_writer
from sheet_interpreter.musicxml import write_musicxml

def document(clef=32,midi=67):
    head=dict(measureIndex=0,staffIndex=0,staffCount=1,clefBottomDiatonic=clef,staffStep=0,writtenAccidental=2,octaveShift=0)
    event=dict(measureIndex=0,staffIndex=0,staffCount=1,midi=midi,startBeat=0,durationBeats=1,clefBottomDiatonic=clef)
    return dict(pages=[dict(measureBeats=[4],events=[event],score=dict(notes=[head]))])

def guide(clef=32):
    note=dict(measureIndex=0,positionInMeasure=.5,staffStep=0,staffIndex=0,staffCount=1,pageY=.5,tiedFromPrevious=False,augmentationDots=0,beamCount=0,writtenAccidental=2,unbeamedDurationBeats=1.,tupletDivisor=1,followingRestBeats=0.,articulations=0,clefBottomDiatonic=clef,crossStaffBeam=False,leadingRestBeats=0.,compactOpening=False,octaveShift=0,boundaryTies=0,tupletNormalNotes=1,stemDirection=0,kind='PITCHED')
    return dict(measures=[dict(left=.1,right=.9,top=.1,bottom=.9)],firstMeasureNumber=1,notes=[note],keyChanges=[],tempoChanges=[],meterChanges=[],rests=[],techniqueChanges=[],dynamicChanges=[],playbackDirections=[],expressiveEvents=[])

class Intrinsic32PublicTests(unittest.TestCase):
    def xml(self,value):
        before=copy.deepcopy(value)
        with tempfile.TemporaryDirectory(prefix='original-intrinsic-clef-')as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(value,path);root=ET.parse(path).getroot()
        self.assertEqual(before,value);return root
    def test_written_fifteenth_and_concert_pitch_are_not_double_transposed(self):
        root=self.xml(document());self.assertEqual('F',root.findtext('.//clef/sign'));self.assertEqual('4',root.findtext('.//clef/line'));self.assertEqual('2',root.findtext('.//clef/clef-octave-change'));self.assertEqual(('G','4'),(root.findtext('.//pitch/step'),root.findtext('.//pitch/octave')));self.assertFalse(root.findall('.//transpose'));self.assertFalse(root.findall('.//octave-shift'))
    def test_retained_spelling_is_same_sounding_identity(self):
        page=document()['pages'][0];self.assertEqual(('G',0,4),spelling_export.retained(page,0,0));page['events'][0]['midi']=79;self.assertIsNone(spelling_export.retained(page,0,0))
    def test_new_explicit_display_clef_without_source_notes(self):
        value=document();value['pages'][0]['score']['notes']=[];self.assertEqual('2',self.xml(value).findtext('.//clef/clef-octave-change'))
    def test_old_intrinsic_clefs_still_have_same_concert_pitch(self):
        for clef,midi,octave in [(18,43,'2'),(25,55,'3'),(30,64,'4'),(37,76,'5')]:
            root=self.xml(document(clef,midi));self.assertEqual(octave,root.findtext('.//pitch/octave'))
    def test_unpitched_display_accepts32_without_midi(self):
        typed_events.validate_unpitched(dict(kind='UNPITCHED',displayStep='G',displayOctave=4,clefBottomDiatonic=32))
    def test_unknown_display_enum_still_rejected(self):
        with self.assertRaises(ValueError):typed_events.validate_unpitched(dict(kind='UNPITCHED',displayStep='G',displayOctave=4,clefBottomDiatonic=33))
    def test_new_guide_accepts32_in_existing_int_field(self):
        blob=guide_writer.encode(guide(),290);self.assertEqual(290,struct.unpack_from('>i',blob)[0]);self.assertEqual(32,struct.unpack_from('>i',blob,32+53)[0])
    def test_all_legacy_guide_writes_reject32(self):
        for version in range(260,290):
            with self.assertRaisesRegex(ValueError,'guide290'):guide_writer.encode(guide(),version)
    def test_guide_note_layout_and_paired_payload_are_unchanged(self):
        old=guide_writer.encode(guide(25),289);new=guide_writer.encode(guide(25),290);self.assertEqual(old[4:],new[4:]);self.assertEqual(len(old),len(guide_writer.encode(guide(32),290)))
    def test_unknown_guide_clef_cannot_silently_fall_back(self):
        with self.assertRaises(ValueError):guide_writer.encode(guide(33),290)

if __name__=='__main__':unittest.main()
