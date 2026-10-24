# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Guide294 exact-owner natural-harmonic wire compatibility originals."""
import struct
import unittest
from sheet_interpreter import guide_writer, semantic_wire


def event(scope='NOTE', start='default'):
    return dict(eventId='harmonic-1', kind='NATURAL_HARMONIC', scope=scope,
        start=({'measureIndex': 0, 'quarterBeatOffset': 0.0} if scope == 'NOTE' else None) if start == 'default' else start,
        end=None, targetEventId='natural-harmonic-note-v1:0:0:1:3e4ccccd:0:3ecccccd',
        staffIndex=0, staffCount=1, strength='UNSPECIFIED', qualifierText='',
        evidence=[dict(sourceId='printed-natural-harmonic', pageIndex=0, visualX=.3,
                       staffIndex=0, staffCount=1, printedText='')])


def score():
    note=dict(measureIndex=0,positionInMeasure=.3,staffStep=0,staffIndex=0,staffCount=1,
      pageY=.4,tiedFromPrevious=False,augmentationDots=0,beamCount=0,writtenAccidental=0,
      unbeamedDurationBeats=1.,tupletDivisor=1,followingRestBeats=0.,articulations=0,
      clefBottomDiatonic=30,crossStaffBeam=False,leadingRestBeats=0.,compactOpening=False,
      octaveShift=0,boundaryTies=0,tupletNormalNotes=1,stemDirection=0,kind='PITCHED',visualSize='FULL')
    return dict(measures=[dict(left=.1,right=.9,top=.2,bottom=.6)],firstMeasureNumber=1,
      notes=[note],keyChanges=[],tempoChanges=[],meterChanges=[dict(measureIndex=0,numerator=4,denominator=4)],
      rests=[],techniqueChanges=[],dynamicChanges=[],playbackDirections=[],expressiveEvents=[event()])


class Guide294NaturalHarmonicOriginal(unittest.TestCase):
    def test_current_writer_emits295_without_changing_public_revision(self):
        raw=guide_writer.encode(score())
        self.assertEqual(295,struct.unpack_from('>i',raw)[0])
        self.assertEqual(112,guide_writer.RECOGNITION_REVISION)

    def test_note_and_unresolved_owner_roundtrip(self):
        for row in (event(),event('UNRESOLVED')):
            raw=semantic_wire.encode([], [row], 1, 294)
            _,decoded=semantic_wire.decode(raw,1,294)
            self.assertEqual(row['scope'],decoded[0]['scope'])
            self.assertEqual(row['targetEventId'],decoded[0]['targetEventId'])

    def test_older_guide_refuses_new_kind(self):
        with self.assertRaisesRegex(ValueError,'guide294'):
            semantic_wire.encode([], [event()], 1, 293)

    def test_malformed_owner_shapes_refuse(self):
        for row in (event('NOTE',None), event('UNRESOLVED', {'measureIndex':0,'quarterBeatOffset':0.0}), event('SCORE')):
            with self.assertRaises(ValueError):semantic_wire.encode([], [row], 1, 294)


if __name__=='__main__':unittest.main()
