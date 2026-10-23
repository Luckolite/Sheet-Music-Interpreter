# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original standalone unequal-hold export contracts; no private score fixtures."""
import copy,tempfile,unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml,DIVISIONS

def event(start,duration,staff=0):
    return dict(midi=67,startBeat=start,durationBeats=duration,staffIndex=staff)

class UnisonDurationConsumerTests(unittest.TestCase):
    def export(self,events):
        d=dict(pages=[dict(events=events,measureBeats=[4],score={})]);before=copy.deepcopy(d)
        with tempfile.TemporaryDirectory(prefix='original-unison-hold-') as folder:
            out=Path(folder)/'score.musicxml';write_musicxml(d,out);root=ET.parse(out).getroot()
        self.assertEqual(before,d)
        return root
    def durations(self,root):
        return sorted(int(n.findtext('duration')) for n in root.findall('.//note') if n.find('pitch') is not None)
    def test_one_part_preserves_quarter_and_eighth_at_the_same_attack(self):
        r=self.export([event(0,1),event(0,.5)])
        self.assertEqual([DIVISIONS//2,DIVISIONS],self.durations(r))
        self.assertEqual({'1','2'},{n.findtext('voice') for n in r.findall('.//note') if n.find('pitch') is not None})
    def test_two_original_parts_preserve_half_and_dotted_quarter(self):
        r=self.export([event(0,2),event(0,1.5,1)])
        self.assertEqual(2,len(r.findall('part')))
        self.assertEqual([3*DIVISIONS//2,2*DIVISIONS],self.durations(r))
    def test_tuplet_and_straight_duration_are_both_present(self):
        r=self.export([event(0,1/3),event(0,.5)])
        self.assertEqual([DIVISIONS//3,DIVISIONS//2],self.durations(r))
    def test_later_required_reattack_does_not_delete_the_initial_long_hold(self):
        r=self.export([event(0,1),event(0,.5),event(.5,.5)])
        self.assertEqual([DIVISIONS//2,DIVISIONS//2,DIVISIONS],self.durations(r))

if __name__=='__main__':unittest.main()
