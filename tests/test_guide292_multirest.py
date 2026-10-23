# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy, struct, unittest
from sheet_interpreter import guide_writer, multi_rest_wire

class PrintedMultiRestGuideOriginalTest(unittest.TestCase):
    def setUp(self):
        self.group=dict(firstMeasureIndex=0,measureCount=4,staffIndex=0,staffCount=1)
        self.score=dict(measures=[dict(left=.1,right=.9,top=.2,bottom=.4)]*4,
            notes=[],firstMeasureNumber=1,keyChanges=[],tempoChanges=[],meterChanges=[dict(measureIndex=0,numerator=4,denominator=4)],
            rests=[dict(measureIndex=i,positionInMeasure=.5,pageY=.3,pageHeight=.04,staffIndex=0,staffCount=1,durationBeats=4,kind='FULL_MEASURE') for i in range(4)],
            techniqueChanges=[],dynamicChanges=[],playbackDirections=[],expressiveEvents=[],openingPickup=False,multiRestGroups=[self.group])
    def test_four_bar_frame_matches_java_golden(self):
        self.assertEqual('4d524731000000140000000100000000000000040000000000000001',multi_rest_wire.encode_groups([self.group],4).hex())
    def test_four_bar_count_roundtrips(self):
        self.assertEqual([self.group],multi_rest_wire.decode_groups(multi_rest_wire.encode_groups([self.group],4),4))
    def test_current_writer_carries_framed_group(self):
        result=guide_writer.encode(self.score,292)
        self.assertEqual([self.group],multi_rest_wire.decode_groups(result[-28:],4))
    def test_legacy_writer_refuses_to_drop_group(self):
        with self.assertRaises(ValueError): guide_writer.encode(self.score,291)
    def test_ordinary_four_full_rests_do_not_gain_group(self):
        del self.score['multiRestGroups']
        self.assertEqual([],multi_rest_wire.decode_groups(guide_writer.encode(self.score,292)[-12:],4))
    def test_literal_four_beats_cannot_claim_hbar(self):
        self.score['rests'][1]['kind']='LITERAL'
        with self.assertRaises(ValueError):guide_writer.encode(self.score,292)
    def test_missing_full_rest_cannot_claim_hbar(self):
        del self.score['rests'][1]
        with self.assertRaises(ValueError):guide_writer.encode(self.score,292)
    def test_duplicate_full_rest_cannot_claim_hbar(self):
        self.score['rests'].append(dict(self.score['rests'][1]))
        with self.assertRaises(ValueError):guide_writer.encode(self.score,292)
    def test_group_past_page_rejects(self):
        with self.assertRaises(ValueError):multi_rest_wire.encode_groups([self.group],3)
    def test_overlapping_groups_reject(self):
        with self.assertRaises(ValueError):multi_rest_wire.encode_groups([self.group,self.group],4)
    def test_boolean_count_is_not_integer(self):
        self.group['measureCount']=True
        with self.assertRaises(ValueError):multi_rest_wire.encode_groups([self.group],4)
    def test_truncated_frame_rejects(self):
        with self.assertRaises(ValueError):multi_rest_wire.decode_groups(multi_rest_wire.encode_groups([self.group],4)[:-1],4)
if __name__=='__main__':unittest.main()
