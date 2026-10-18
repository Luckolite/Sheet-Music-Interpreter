# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy,tempfile,unittest,xml.etree.ElementTree as ET
from pathlib import Path
from sheet_interpreter.musicxml import write_musicxml


def doc(rows,bars=2):
    events=[];notes=[]
    for start,duration,shift,staff in rows:
        notes.append(dict(measureIndex=int(start//4),staffIndex=staff,staffCount=2,clefBottomDiatonic=18,staffStep=2,writtenAccidental=2,octaveShift=shift))
        events.append(dict(measureIndex=int(start//4),staffIndex=staff,staffCount=2,clefBottomDiatonic=18,midi=47+12*shift,startBeat=start,durationBeats=duration,octaveShift=shift))
    return dict(pages=[dict(measureBeats=[4]*bars,events=events,score=dict(notes=notes))])

class OctaveShiftXmlOriginal(unittest.TestCase):
    def xml(self,d):
        before=copy.deepcopy(d)
        with tempfile.TemporaryDirectory() as t:
            f=Path(t)/'score.musicxml';write_musicxml(d,f);xml=ET.parse(f).getroot()
        self.assertEqual(before,d);return xml
    def test_all_supported_shifts_sound_without_double_transposition(self):
        for shift in [-2,-1,1,2]:
            r=self.xml(doc([(0,1,shift,0)]));self.assertEqual(str(2+shift),r.findtext('.//pitch/octave'))
            marks=r.findall('.//octave-shift');self.assertEqual(2,len(marks));self.assertEqual('down' if shift>0 else 'up',marks[0].get('type'));self.assertEqual(str(7*abs(shift)+1),marks[0].get('size'))
    def test_opposing_staves_are_independent(self):
        r=self.xml(doc([(0,4,1,0),(0,4,-1,1)]));self.assertEqual(4,len(r.findall('.//octave-shift')))
    def test_contiguous_merge_and_rest_gap(self):
        self.assertEqual(2,len(self.xml(doc([(0,4,1,0),(4,4,1,0)])).findall('.//octave-shift')))
        self.assertEqual(4,len(self.xml(doc([(0,1,1,0),(2,1,1,0)])).findall('.//octave-shift')))
    def test_mixed_chord_uses_independent_voices(self):
        r=self.xml(doc([(0,1,1,0),(0,1,-1,0)]));ds=[e for e in r.findall('.//direction') if e.find('direction-type/octave-shift') is not None];self.assertEqual(4,len(ds));self.assertEqual(2,len({e.findtext('voice') for e in ds}))
    def test_unshifted_has_no_new_directions(self):
        self.assertFalse(self.xml(doc([(0,1,0,0)])).findall('.//octave-shift'))
    def test_invalid_shift_does_not_emit_fabricated_notation(self):
        with self.assertRaises(ValueError):self.xml(doc([(0,1,3,0)]))
