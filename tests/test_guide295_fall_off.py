# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original note-owned curved falloff records; no published score material."""
import copy
import struct
import unittest
from sheet_interpreter import guide_writer, semantic_wire
from test_guide294_natural_harmonic import score

def event(scope='UNRESOLVED'):
    return dict(eventId='original-fall',kind='FALL_OFF',scope=scope,
        start=dict(measureIndex=0,quarterBeatOffset=0.) if scope=='NOTE' else None,
        end=None,staffIndex=0,staffCount=1,targetEventId='fall-off-note-v1:0:0:1:3e99999a:0:3ecccccd',
        strength='UNSPECIFIED',qualifierText='falloff-v1:curved',
        evidence=[dict(sourceId='printed-fall-off',pageIndex=0,visualX=.5,staffIndex=0,staffCount=1,printedText='curved falloff')])

class Guide295FallOffOriginals(unittest.TestCase):
    def test_historical296_writer_emits296_with_source_kind21(self):
        source=score();source['expressiveEvents']=[event()];before=copy.deepcopy(source)
        revision_before=guide_writer.RECOGNITION_REVISION
        raw=guide_writer.encode(source,296)
        self.assertEqual(296,struct.unpack_from('>i',raw)[0]);self.assertEqual(before,source)
        self.assertEqual(revision_before,guide_writer.RECOGNITION_REVISION)
    def test_note_and_unresolved_records_roundtrip(self):
        for scope in ['NOTE','UNRESOLVED']:
            row=event(scope);raw=semantic_wire.encode([], [row],1,295)
            self.assertEqual(([],[row]),semantic_wire.decode(raw,1,295))
    def test_old294_cannot_encode_or_decode_appended_kind(self):
        row=event();raw=semantic_wire.encode([], [row],1,295)
        with self.assertRaises(ValueError):semantic_wire.encode([], [row],1,294)
        with self.assertRaises(ValueError):semantic_wire.decode(raw,1,294)
    def test_unknown_shape_amount_and_target_cannot_become_a_note_mark(self):
        for key,value in [('qualifierText','pitch:-2'),('strength','MOLTO'),('scope','PART'),
                ('targetEventId','fall-off-note-v1:0:0:1:7fc00000:0:3ecccccd')]:
            row=event();row[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):semantic_wire.encode([], [row],1,295)
    def test_old294_records_keep_their_complete_semantic_bytes(self):
        old=score()['expressiveEvents'][0]
        self.assertEqual(semantic_wire.encode([], [old],1,294),semantic_wire.encode([], [old],1,295))

if __name__=='__main__':unittest.main()
