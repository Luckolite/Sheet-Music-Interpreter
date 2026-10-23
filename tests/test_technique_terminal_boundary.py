# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from test_technique_written_clock import document,TechniqueWrittenClockTests
class TechniqueTerminalBoundaryTests(unittest.TestCase):
    def test_single_non_grace_column_at_next_bar_start_cannot_bind_this_bar(self):
        d=document();p=d['pages'][0];p['measureBeats']=[4,4]
        p['score']['notes']=p['score']['notes'][:1];p['events']=p['events'][:1]
        p['events'][0].update(startBeat=4,durationBeats=2)
        self.assertEqual([],TechniqueWrittenClockTests().xml(d))
