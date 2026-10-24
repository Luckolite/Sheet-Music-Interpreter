# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original abstract written approaches; no private score data or recognition claim."""
import copy
import tempfile
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter import ornament_export
from sheet_interpreter.musicxml import write_musicxml

SLIDE=6<<5
STRAIGHT=1<<8
ABOVE=1<<13
PREVIOUS=1<<14

def source(flags,duration=1):
    note=dict(measureIndex=0,staffIndex=0,staffCount=1,articulations=flags)
    event=dict(measureIndex=0,staffIndex=0,staffCount=1,midi=64,startBeat=0,durationBeats=duration)
    return dict(pages=[dict(measureBeats=[4,4]if duration>4 else[4],events=[event],score=dict(notes=[note]))])

class ApproachShapeTests(unittest.TestCase):
    def direct(self,flags):
        root=ET.Element('notations');ornament_export.write(root,flags);return root
    def test_straight_below(self):
        r=self.direct(SLIDE|STRAIGHT)
        self.assertEqual('straight',r.find('articulations/scoop').get('line-shape'))
        self.assertEqual([],r.findall('ornaments'))
    def test_straight_above(self):
        r=self.direct(SLIDE|STRAIGHT|ABOVE)
        self.assertEqual('straight',r.find('articulations/plop').get('line-shape'))
        self.assertEqual([],r.findall('articulations/scoop'))
    def test_curved_defaults_keep_direction_without_new_attribute(self):
        for flags,tag in [(SLIDE,'scoop'),(SLIDE|ABOVE,'plop')]:
            r=self.direct(flags);self.assertEqual({},r.find('articulations/'+tag).attrib)
    def test_connected_does_not_become_isolated_even_with_shape_bit(self):
        for above in (0,ABOVE):
            r=self.direct(SLIDE|PREVIOUS|STRAIGHT|above)
            self.assertEqual([],list(r))
    def test_delayed_turn_is_not_reinterpreted_as_shape(self):
        for kind,tag in [(2,'delayed-turn'),(3,'delayed-inverted-turn')]:
            r=self.direct((kind<<5)|STRAIGHT)
            self.assertEqual([tag],[e.tag for e in r.findall('ornaments/*')])
            self.assertEqual([],r.findall('articulations'))
    def test_other_types_do_not_read_shape_alias(self):
        for kind in (0,1,4,5,7):
            r=self.direct((kind<<5)|STRAIGHT)
            self.assertEqual([],r.findall('articulations'))
    def test_retained_keeps_exact_qualifier_and_other_supported_bits(self):
        flags=SLIDE|STRAIGHT|ABOVE|(2<<16)|(3<<9)
        page=source(flags)['pages'][0];before=copy.deepcopy(page)
        self.assertEqual(flags,ornament_export.retained(page,0));self.assertEqual(before,page)
    def test_whole_writer_shape_is_on_first_attack_only_with_clock_unchanged(self):
        doc=source(SLIDE|STRAIGHT,6.5);before=copy.deepcopy(doc)
        with tempfile.TemporaryDirectory(prefix='original-approach-shape-')as directory:
            path=Path(directory)/'shape.musicxml';write_musicxml(doc,path);root=ET.parse(path).getroot()
        self.assertEqual(before,doc)
        self.assertEqual(1,len(root.findall('.//scoop')))
        self.assertEqual('straight',root.find('.//scoop').get('line-shape'))
        self.assertEqual(3,len(root.findall('.//note[pitch]')))
        self.assertEqual(6.5,sum(int(n.findtext('duration'))for n in root.findall('.//note[pitch]'))/int(root.findtext('.//divisions')))

if __name__=='__main__':unittest.main()
