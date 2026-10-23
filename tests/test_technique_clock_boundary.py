# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from test_technique_written_clock import document,TechniqueWrittenClockTests
class TechniqueClockBoundaryTests(unittest.TestCase):
    def test_non_grace_owner_cannot_borrow_following_bar_start(self):
        d=document();p=d['pages'][0];p['measureBeats']=[4,4]
        p['events'][0].update(startBeat=4,durationBeats=2)
        p['events'][1].update(startBeat=6,durationBeats=2)
        self.assertEqual([],TechniqueWrittenClockTests().xml(d))
