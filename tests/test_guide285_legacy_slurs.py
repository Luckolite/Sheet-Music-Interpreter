# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original constant-size unowned source and explicit retained ownership controls."""
from pathlib import Path
import copy
import importlib.util
import sys
import unittest

from sheet_interpreter import guide_writer, slur_wire
from test_guide284_tab_ties import score as synthetic_score

class LegacyEmptySlurTest(unittest.TestCase):
    def setUp(self):
        self.score=synthetic_score()
        self.score['playbackDirections']=[]
        self.score['expressiveEvents']=[]
        self.empty=dict(noteOrigins=[None]*len(self.score['notes']),spans=[])
    def test_empty_source_keeps_every_legacy_format_byte_exact(self):
        for version in range(260,285):
            with self.subTest(version=version):
                before=guide_writer.encode(self.score,version)
                with_source=dict(self.score,slurSource=self.empty)
                self.assertEqual(before,guide_writer.encode(with_source,version))
    def test_null_extension_remains_legacy_compatible(self):
        self.assertEqual(guide_writer.encode(self.score,284),guide_writer.encode(dict(self.score,slurSource=None),284))
    def test_no_note_geometry_guide_can_have_an_explicit_empty_source(self):
        score=dict(self.score,notes=[])
        self.assertEqual(guide_writer.encode(score,284),guide_writer.encode(dict(score,slurSource=dict(noteOrigins=[],spans=[])),284))
    def test_owned_origins_without_arcs_are_still_retained_data(self):
        source=copy.deepcopy(self.empty);source['noteOrigins'][0]=dict(pageIndex=9,noteIndex=0)
        with self.assertRaises(ValueError):guide_writer.encode(dict(self.score,slurSource=source),284)
    def test_unresolved_cross_page_arc_cannot_be_silently_dropped(self):
        source=copy.deepcopy(self.empty)
        source['spans']=[dict(id='original-cross-page',start=dict(pageIndex=9,noteIndex=0),
            end=dict(pageIndex=12,noteIndex=1),placement='ABOVE',lineType='SOLID',
            evidence=[dict(pageIndex=9,startX=.2,startY=.3,endX=.8,endY=.4)])]
        with self.assertRaises(ValueError):guide_writer.encode(dict(self.score,slurSource=source),284)
    def test_malformed_empty_map_is_not_a_legacy_escape(self):
        with self.assertRaises(ValueError):guide_writer.encode(dict(self.score,slurSource=dict(noteOrigins=[],spans=[])),284)
    def test_unknown_empty_fields_are_not_ignored(self):
        with self.assertRaises(ValueError):guide_writer.encode(dict(self.score,slurSource=dict(self.empty,extra=True)),284)
    def test_empty285_source_is_still_written_and_readable(self):
        expected=slur_wire.encode_source(None,len(self.score['notes']))
        old=guide_writer.encode(self.score,284);new=guide_writer.encode(dict(self.score,slurSource=self.empty),285)
        self.assertEqual(new[4:-len(expected)],old[4:]);self.assertTrue(new.endswith(expected))
        self.assertEqual(24,len(expected))

if __name__=='__main__':unittest.main()
