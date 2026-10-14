# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import struct
import unittest
from sheet_interpreter import slur_wire

def source():
    return dict(noteOrigins=[dict(pageIndex=9,noteIndex=i) for i in range(3)],spans=[],candidates=[dict(
        id='deferred-original',start=dict(pageIndex=9,noteIndex=0),end=dict(pageIndex=9,noteIndex=2),
        placement='ABOVE',lineType='DASHED',evidence=[dict(pageIndex=9,startX=.125,startY=.25,endX=.75,endY=.375)])])

class DeferredSourceWireTest(unittest.TestCase):
    def test_original_candidates_round_trip_without_mutation(self):
        original=source();before=copy.deepcopy(original)
        wire=slur_wire.encode_source(original,3)
        self.assertEqual(0x534c5432,struct.unpack_from('>i',wire)[0])
        self.assertEqual(original,slur_wire.decode_source(wire,3));self.assertEqual(before,original)
    def test_confirmed_only_source_keeps_slt1_layout(self):
        original=source();original['spans']=original.pop('candidates')
        before=slur_wire.encode_source(original,3)
        self.assertEqual(0x534c5431,struct.unpack_from('>i',before)[0])
        self.assertEqual(before,slur_wire.encode_source(dict(original,candidates=[]),3))
    def test_deferred_geometry_is_not_an_empty_legacy_source(self):
        self.assertFalse(slur_wire.is_empty_source(source(),3))
        self.assertTrue(slur_wire.is_empty_source(dict(noteOrigins=[None]*3,spans=[],candidates=[]),3))
    def test_confirmed_and_deferred_ids_must_be_jointly_unique(self):
        original=source();original['spans']=copy.deepcopy(original['candidates'])
        with self.assertRaises(ValueError):slur_wire.encode_source(original,3)
    def test_every_truncated_deferred_prefix_and_extra_byte_rejects(self):
        wire=slur_wire.encode_source(source(),3)
        for size in range(len(wire)):
            with self.subTest(size=size),self.assertRaises(ValueError):slur_wire.decode_source(wire[:size],3)
        with self.assertRaises(ValueError):slur_wire.decode_source(wire+b'\0',3)
    def test_candidate_layout_cannot_be_relabelled_as_confirmed_layout(self):
        wire=slur_wire.encode_source(source(),3)
        with self.assertRaises(ValueError):slur_wire.decode_source(struct.pack('>i',0x534c5431)+wire[4:],3)
    def test_candidate_without_surviving_origins_still_retains_evidence(self):
        original=source();original['noteOrigins']=[None]*3
        self.assertEqual(original,slur_wire.decode_source(slur_wire.encode_source(original,3),3))
