# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original complete group writer fixtures; no published score material."""
import copy
import struct
import unittest
from sheet_interpreter import guide_writer, semantic_wire, parenthesized_note_group as group
from test_guide294_natural_harmonic import score


def document():
    source=score();first=source['notes'][0]
    source['notes']=[dict(first,positionInMeasure=x,staffStep=step)for x,step in[(.1,0),(.4,2),(.7,4)]]
    pair=group.Pair(tuple(group.member_of(n)for n in source['notes']),(-1.2,0,1.1,0,1.75,1.75))
    mark=dict(eventId='original-guide296-group',kind='PARENTHESIZED_NOTE_GROUP',scope='UNRESOLVED',
        start=None,end=None,staffIndex=0,staffCount=1,targetEventId=pair.target,strength='UNSPECIFIED',
        qualifierText=pair.qualifier,evidence=[dict(sourceId=group.SOURCE,pageIndex=0,visualX=group.f32(.1),
            staffIndex=0,staffCount=1,printedText='( complete group )')])
    source['expressiveEvents']=[mark]
    return source


class Guide296ParenthesizedGroupOriginals(unittest.TestCase):
    def test_historical296_writer_retains_group296_without_changing_current_revision(self):
        source=document();before=copy.deepcopy(source);revision_before=guide_writer.RECOGNITION_REVISION
        raw=guide_writer.encode(source,296)
        self.assertEqual(296,struct.unpack_from('>i',raw)[0]);self.assertEqual(revision_before,guide_writer.RECOGNITION_REVISION)
        self.assertEqual(before,source)
    def test_both_scopes_keep_complete_member_bits(self):
        for scope in ['UNRESOLVED','NOTE']:
            mark=document()['expressiveEvents'][0];mark.update(scope=scope,start=None if scope=='UNRESOLVED'else dict(measureIndex=0,quarterBeatOffset=0))
            self.assertEqual(([],[mark]),semantic_wire.decode(semantic_wire.encode([],[mark],1,296),1,296))
    def test_previous295_cannot_drop_or_read_the_new_record(self):
        mark=document()['expressiveEvents'][0]
        with self.assertRaises(ValueError):semantic_wire.encode([],[mark],1,295)
        raw=semantic_wire.encode([],[mark],1,296)
        with self.assertRaises(ValueError):semantic_wire.decode(raw,1,295)
    def test_target_mismatch_refuses_full_guide_write(self):
        source=document();source['expressiveEvents'][0]['targetEventId']='wrong'
        with self.assertRaises(ValueError):guide_writer.encode(source)
    def test_existing_semantic_records_keep_their_bytes_at296(self):
        old=score()['expressiveEvents'][0]
        self.assertEqual(semantic_wire.encode([],[old],1,295),semantic_wire.encode([],[old],1,296))


if __name__=='__main__':unittest.main()
