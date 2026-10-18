# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import copy,unittest
from test_straight_slide_consumer import document,export
class StraightSlideForkTests(unittest.TestCase):
    def test_one_source_two_explicit_targets_rejected(self):
        d=document();p=d['pages'][0];p['score']['notes'].append(copy.deepcopy(p['score']['notes'][1]));p['score']['notes'][2]['staffStep']=7
        p['events'].append(copy.deepcopy(p['events'][1]));p['events'][2]['midi']=76
        with self.assertRaises(ValueError):export(d)
