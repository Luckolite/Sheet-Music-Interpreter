# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import struct
import unittest
from sheet_interpreter import guide_writer as wire

def original_score():
    note = dict(measureIndex=0,positionInMeasure=.375,staffStep=-4,staffIndex=0,
                staffCount=1,pageY=.5,tiedFromPrevious=False,augmentationDots=0,beamCount=1,
                writtenAccidental=2,unbeamedDurationBeats=0.,tupletDivisor=3,
                followingRestBeats=0.,articulations=0,clefBottomDiatonic=18,
                crossStaffBeam=False,leadingRestBeats=0.,compactOpening=False,
                octaveShift=0,boundaryTies=0,tupletNormalNotes=2)
    return dict(measures=[dict(left=.1,right=.9,top=.2,bottom=.8)],firstMeasureNumber=1,
                notes=[note],keyChanges=[],tempoChanges=[],meterChanges=[],rests=[],
                techniqueChanges=[],dynamicChanges=[],playbackDirections=[],expressiveEvents=[])

class PrintedStemGuideTests(unittest.TestCase):
    def test_new_record_appends_real_signed_int_and_keeps_remaining_framing(self):
        score=original_score();legacy=wire.encode(score,280)
        for stem in [-1,0,1]:
            score['notes'][0]['stemDirection']=stem;new=wire.encode(score,281)
            self.assertEqual(len(legacy)+4,len(new))
            self.assertEqual(281,struct.unpack('>i',new[:4])[0])
            self.assertEqual(stem,struct.unpack('>i',new[107:111])[0])
            self.assertEqual(legacy[4:107],new[4:107])
            self.assertEqual(legacy[107:],new[111:])
    def test_legacy_missing_stem_and_explicit_unknown_are_byte_identical(self):
        score=original_score();legacy=wire.encode(score,280)
        score['notes'][0]['stemDirection']=0
        self.assertEqual(legacy,wire.encode(score,280))
    def test_legacy_format_cannot_silently_discard_printed_direction_or_c_clef(self):
        for field,value in [('stemDirection',1),('stemDirection',-1),('clefBottomDiatonic',22),('clefBottomDiatonic',24)]:
            score=original_score();score['notes'][0][field]=value
            with self.assertRaises(ValueError):wire.encode(score,280)
            wire.encode(score,281)
    def test_bad_stem_types_and_range_are_rejected(self):
        for value in [-2,2,True,False,1.,None,'1']:
            score=original_score();score['notes'][0]['stemDirection']=value
            with self.assertRaises(ValueError):wire.encode(score,281)
    def test_unknown_future_layout_is_rejected(self):
        with self.assertRaises(ValueError):wire.encode(original_score(),283)

    def test_current_layout282_appends_kind_after_legacy281_signed_stem(self):
        self.assertEqual(4,wire.RECOGNITION_REVISION)
        self.assertEqual(282,wire.GUIDE_VERSION)
        score=original_score();score['notes'][0]['stemDirection']=1
        legacy=wire.encode(score,281);encoded=wire.encode(score)
        self.assertEqual(282,struct.unpack('>i',encoded[:4])[0])
        self.assertEqual(len(legacy)+1,len(encoded))
        self.assertEqual(legacy[4:111],encoded[4:111])
        self.assertEqual(0,encoded[111])
        self.assertEqual(legacy[111:],encoded[112:])

if __name__=='__main__':unittest.main()
