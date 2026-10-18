# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original document-order numbering controls, distinct from optical acquisition."""
import copy, sys, unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from original_numbering_repro import chain, exported
from sheet_interpreter import slide_numbering


def notation(order):
    root=ET.Element('score-partwise');part=ET.SubElement(root,'part',id='P1')
    for token,kind in order:
        note=ET.SubElement(part,'note');n=ET.SubElement(note,'notations')
        ET.SubElement(n,'slide',type=kind,number=str(token),**{'line-type':'solid'})
    return root


def numbers(root):
    return [int(n.get('number')) for n in root.findall('.//slide')]


class BoundedStraightSlideNumberingTest(unittest.TestCase):
    def test_seventeen_note_chain_reuses_closed_number(self):
        root=exported(chain(17));self.assertEqual([1]*32,numbers(root))
        # At every interior principal note the incoming stop precedes outgoing start.
        for note in root.findall('.//note'):
            kinds=[n.get('type') for n in note.findall('notations/slide')]
            if len(kinds)==2:self.assertEqual(['stop','start'],kinds)

    def test_seventeen_sequential_pairs_keep_every_endpoint(self):
        d=chain(34);p=d['pages'][0]
        for i in range(2,34,2):
            p['score']['notes'][i]['articulations']=0;p['events'][i].pop('slide')
        root=exported(d);self.assertEqual([1]*34,numbers(root))
        self.assertEqual(['start','stop']*17,[n.get('type')for n in root.findall('.//slide')])

    def test_split_source_uses_final_fragment_and_target_first(self):
        d=chain(2);p=d['pages'][0]
        p['events'][0]['durationBeats']=6.;p['score']['notes'][0]['unbeamedDurationBeats']=6.
        p['score']['notes'][0]['augmentationDots']=1
        p['events'][1].update(startBeat=6.,durationBeats=2.)
        p['score']['notes'][1]['unbeamedDurationBeats']=2.
        root=exported(d);notes=[n for n in root.findall('.//note')if n.find('pitch')is not None]
        self.assertEqual(3,len(notes))
        self.assertEqual([[],['start'],['stop']],[[s.get('type')for s in n.findall('notations/slide')]for n in notes])
        self.assertEqual([1,1],numbers(root))

    def test_sixteen_concurrent_document_intervals_have_distinct_numbers(self):
        r=notation([(i,'start')for i in range(16)]+[(i,'stop')for i in reversed(range(16))])
        slide_numbering.assign(r);self.assertEqual(list(range(1,17)),numbers(r)[:16])
        self.assertEqual(list(reversed(range(1,17))),numbers(r)[16:])

    def test_seventeen_concurrent_intervals_fail_without_partial_mutation(self):
        r=notation([(i,'start')for i in range(17)]+[(i,'stop')for i in range(17)])
        before=ET.tostring(r)
        with self.assertRaisesRegex(ValueError,'More than 16 overlapping'):slide_numbering.assign(r)
        self.assertEqual(before,ET.tostring(r))

    def test_voice_document_stop_before_start_keeps_identity(self):
        # A backup can serialize the later-time voice first. Intervals overlap
        # in document order, even when the endpoint labelled stop appears first.
        r=notation([(90,'stop'),(91,'start'),(90,'start'),(91,'stop')])
        slide_numbering.assign(r);self.assertEqual([1,2,1,2],numbers(r))

    def test_page_local_indices_do_not_change_global_endpoint_pairs(self):
        d=chain(2);d['pages'].append(copy.deepcopy(d['pages'][0]))
        r=exported(d);self.assertEqual([1,1,1,1],numbers(r))
        self.assertEqual(['start','stop']*2,[n.get('type')for n in r.findall('.//slide')])

    def test_unpaired_or_duplicate_endpoint_identity_is_explicit(self):
        for order in [[(1,'start')],[(1,'start'),(1,'start')],[(1,'start'),(1,'stop'),(1,'stop')]]:
            r=notation(order);before=ET.tostring(r)
            with self.assertRaises(ValueError):slide_numbering.assign(r)
            self.assertEqual(before,ET.tostring(r))
