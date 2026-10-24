# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original full Guide298 frame and downgrade controls; no score data."""
import copy, struct, tempfile, unittest
from pathlib import Path
from sheet_interpreter import guide_writer, tremolo_wire, beam_source

def score(policy=None):
    notes=[]
    for i in range(3):
        notes.append(dict(measureIndex=0,positionInMeasure=.2 if i<2 else .6,
            staffStep=2+i*2,staffIndex=0,staffCount=1,pageY=.4,
            tiedFromPrevious=False,augmentationDots=0,beamCount=0,writtenAccidental=2,
            unbeamedDurationBeats=1,tupletDivisor=1,followingRestBeats=0,
            articulations=0,clefBottomDiatonic=30,crossStaffBeam=False,
            leadingRestBeats=0,compactOpening=False,octaveShift=0))
    origins=[dict(pageIndex=0,noteIndex=i) for i in range(3)]
    pair=dict(id='original-combined-chord',first=origins[:2],second=origins[2:],strokes=3,
        evidence=[dict(pageIndex=0,left=.1,top=.2,right=.7,bottom=.8)])
    if policy is not None:pair['clockPolicy']=policy
    return dict(measures=[dict(left=0,right=1,top=0,bottom=1)],notes=notes,
        firstMeasureNumber=0,keyChanges=[],tempoChanges=[],meterChanges=[],rests=[],
        techniqueChanges=[],dynamicChanges=[],playbackDirections=[],expressiveEvents=[],
        tremoloSource=dict(pairs=[pair],noteOrigins=origins),
        beamSource=dict(groups=[],detachedColumns=[dict(members=[origins[0]])],noteOrigins=origins))

class Guide298CombinedOriginalTest(unittest.TestCase):
    def test_legacy297_complete_body_is_exact_under298(self):
        s=score();old=guide_writer.encode(s,297);current=guide_writer.encode(s,298)
        self.assertEqual(298,struct.unpack_from('>i',current)[0])
        self.assertEqual(old[4:],current[4:])
        self.assertIn(b'TRS1',current)
        self.assertTrue(current.endswith(beam_source.encode_source(s['beamSource'],3)))

    def test_trs2_chord_and_complete_beam_tail_are_both_retained(self):
        for policy in ('UNRESOLVED','SHARED_DURATION','INDEPENDENT_ENDPOINT_DURATIONS'):
            s=score(policy);before=copy.deepcopy(s);raw=guide_writer.encode(s,298)
            trem=tremolo_wire.encode_source(s['tremoloSource'],3)
            beam=beam_source.encode_source(s['beamSource'],3)
            self.assertIn(b'TRS2',raw);start=raw.index(trem)
            self.assertEqual(trem,raw[start:start+len(trem)])
            self.assertEqual(beam,raw[-len(beam):])
            self.assertEqual(2,len(tremolo_wire.decode_source(trem,3)['pairs'][0]['first']))
            self.assertEqual(before,s)

    def test_old_formats_refuse_policy_before_creating_destination(self):
        for version in (296,297):
            s=score('UNRESOLVED')
            with tempfile.TemporaryDirectory() as folder:
                root=Path(folder);out=root/'absent'/'score.guide'
                with self.assertRaises(ValueError):guide_writer.write_new(out,s,version)
                self.assertFalse(out.parent.exists())
                out=root/'existing.guide';out.write_bytes(b'original destination')
                with self.assertRaises(FileExistsError):guide_writer.write_new(out,s,version)
                self.assertEqual(b'original destination',out.read_bytes())

if __name__=='__main__':unittest.main()
