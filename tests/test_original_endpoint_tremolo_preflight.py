# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy
import unittest
from unittest.mock import patch
from test_original_slide_tremolo_conflict import document
from sheet_interpreter.slide_performance import prepare_slides


def plain():
    d=document()
    d['pages'][0]['score']['notes'][1]['articulations']=16576
    d['pages'][0]['events'][1].pop('tremoloBeats')
    return d


def paired(unrelated=False):
    d=plain();p=d['pages'][0]
    if unrelated:
        p['score']['notes'][1]['positionInMeasure']=.4
        for i in (2,3):
            raw=copy.deepcopy(p['score']['notes'][0]);raw.update(positionInMeasure=.4+i*.1)
            event=copy.deepcopy(p['events'][0]);event.update(startBeat=2.+(i-2)*.5,durationBeats=.5)
            p['score']['notes'].append(raw);p['events'].append(event)
        owners=(2,3)
    else:
        owners=(0,1)
        for i,e in enumerate(p['events']):e.update(startBeat=i*.5,durationBeats=.5)
    origins=[dict(pageIndex=0,noteIndex=i)for i in range(len(p['events']))]
    p['score']['tremoloSource']=dict(noteOrigins=origins,pairs=[dict(id='original-pair',
        first=[origins[owners[0]]],second=[origins[owners[1]]],strokes=2)])
    return d


class OriginalEndpointTremoloPreflightTests(unittest.TestCase):
    def test_source_single_tremolo_rejected(self):
        d=plain();d['pages'][0]['events'][0]['tremoloBeats']=.25
        with self.assertRaisesRegex(ValueError,'original endpoint tremolo'):prepare_slides(d)

    def test_raw_beams_without_decoded_subdivision_rejected(self):
        d=plain();d['pages'][0]['score']['notes'][1]['articulations']|=2<<16
        with self.assertRaisesRegex(ValueError,'original endpoint tremolo'):prepare_slides(d)

    def test_validated_paired_endpoint_rejected(self):
        with self.assertRaisesRegex(ValueError,'original endpoint tremolo'):prepare_slides(paired())

    def test_unrelated_validated_pair_preserved(self):
        d=paired(True);before=copy.deepcopy(d);p=prepare_slides(d)
        self.assertEqual(d,before)
        self.assertEqual(p['pages'][0]['score']['tremoloSource'],d['pages'][0]['score']['tremoloSource'])

    def test_early_rejection_has_zero_bridge_calls(self):
        with patch('sheet_interpreter.performance._bridge',side_effect=AssertionError('latebridge'))as bridge:
            with self.assertRaises(ValueError):prepare_slides(document())
            bridge.assert_not_called()

    def test_no_slide_tremolo_fast_path_remains_same_object(self):
        d=document();d['pages'][0]['events'][1].pop('slide')
        self.assertIs(d,prepare_slides(d))
