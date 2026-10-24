# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import struct
import unittest
from sheet_interpreter.tremolo_wire import encode_source, decode_source, performance_pairs
from sheet_interpreter.tremolo_export import retained
from sheet_interpreter.tremolo_performance import source_spans

def fixture():
    origins = [dict(pageIndex=0, noteIndex=i) for i in range(3)]
    return dict(noteOrigins=origins, pairs=[dict(id='original-chord', first=origins[:2], second=origins[2:], strokes=3, evidence=[dict(pageIndex=0, left=.1, top=.2, right=.3, bottom=.4)])])

class ClockContractTests(unittest.TestCase):
    def test_legacy_optional_policy_preserves_bytes(self):
        source = fixture()
        raw = encode_source(source, 3)
        source['pairs'][0]['clockPolicy'] = 'LEGACY_SHARED_DURATION'
        self.assertEqual(raw, encode_source(source, 3))
        self.assertEqual(0x54525331, struct.unpack_from('>i', raw)[0])
        self.assertNotIn('clockPolicy', decode_source(raw, 3)['pairs'][0])

    def test_explicit_policies_preserve_complete_chord_and_strokes(self):
        for policy in ('UNRESOLVED', 'SHARED_DURATION', 'INDEPENDENT_ENDPOINT_DURATIONS'):
            source = fixture()
            source['pairs'][0]['clockPolicy'] = policy
            raw = encode_source(source, 3)
            decoded = decode_source(raw, 3)
            self.assertEqual(0x54525332, struct.unpack_from('>i', raw)[0])
            self.assertEqual(policy, decoded['pairs'][0]['clockPolicy'])
            self.assertEqual(source['noteOrigins'], decoded['noteOrigins'])
            self.assertEqual(source['pairs'][0]['first'], decoded['pairs'][0]['first'])
            self.assertEqual(3, decoded['pairs'][0]['strokes'])
            self.assertEqual(raw, encode_source(decoded, 3))

    def test_old_guide_cannot_downgrade(self):
        source = fixture()
        source['pairs'][0]['clockPolicy'] = 'UNRESOLVED'
        raw = encode_source(source, 3)
        with self.assertRaises(ValueError): encode_source(source, 3, False)
        with self.assertRaises(ValueError): decode_source(raw, 3, False)
        for bad in (raw[:-1], raw + b'x'):
            with self.assertRaises(ValueError): decode_source(bad, 3)

    def test_unknown_policy_refuses(self):
        source = fixture()
        source['pairs'][0]['clockPolicy'] = 'OTHER'
        with self.assertRaises(ValueError): encode_source(source, 3)

    def test_unresolved_export_abstains_and_retains_source(self):
        source = fixture()
        source['pairs'][0]['clockPolicy'] = 'UNRESOLVED'
        before = copy.deepcopy(source)
        page = dict(score=dict(tremoloSource=source))
        self.assertEqual([], performance_pairs(source))
        self.assertEqual({}, retained(page))
        self.assertEqual({}, source_spans(page))
        self.assertEqual(before, source)

    def test_independent_export_preserves_supported_quarter_clock(self):
        source = fixture()
        source['pairs'][0]['first'] = source['pairs'][0]['first'][:1]
        source['pairs'][0]['clockPolicy'] = 'INDEPENDENT_ENDPOINT_DURATIONS'
        source['noteOrigins'] = source['noteOrigins'][::2]
        notes = [dict(measureIndex=0, staffIndex=0, staffCount=1, unbeamedDurationBeats=d) for d in (1, 1)]
        events = [dict(measureIndex=0, staffIndex=0, staffCount=1, startBeat=s, durationBeats=d) for s, d in ((0, 1), (1, 1))]
        page = dict(score=dict(notes=notes, tremoloSource=source), events=events)
        self.assertEqual(2, len(retained(page)))
        # Retain the original unequal2+1 example as a refusal, not a silently
        # changed source clock. General independent timing is tested in Java.
        unsupported = copy.deepcopy(page)
        unsupported['score']['notes'][0]['unbeamedDurationBeats'] = 2
        unsupported['events'][0]['durationBeats'] = 2
        unsupported['events'][1]['startBeat'] = 2
        with self.assertRaisesRegex(ValueError, 'quarter-head'):
            retained(unsupported)
        source['pairs'][0]['clockPolicy'] = 'SHARED_DURATION'
        with self.assertRaises(ValueError): retained(page)
