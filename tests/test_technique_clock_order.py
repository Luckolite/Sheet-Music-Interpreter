# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
from test_technique_written_clock import document,TechniqueWrittenClockTests

class TechniqueClockOrderTests(unittest.TestCase):
    def test_time_reversal_of_a_later_affected_column_is_not_a_proved_clock(self):
        d=document();p=d['pages'][0];p['events'][0]['startBeat']=2;p['events'][1]['startBeat']=0
        self.assertEqual([],TechniqueWrittenClockTests().xml(d))
    def test_boolean_source_topology_is_not_an_integer_owner(self):
        d=document();p=d['pages'][0]
        p['score']['notes'][0]['staffCount']=True
        self.assertEqual([],TechniqueWrittenClockTests().xml(d))

if __name__=='__main__':unittest.main()
