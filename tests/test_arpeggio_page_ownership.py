# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original page ownership controls; no private runner or fixtures."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from test_arpeggio_port import document
from sheet_interpreter.musicxml import write_musicxml

class ParentPageOwnership(unittest.TestCase):
 def test_second_page_local_zero_does_not_mark_first_page(self): self.check(1)
 def test_first_page_marks_do_not_transfer_to_second_page(self): self.check(0)
 def check(self, owner):
  d=document(); d["pages"].append(copy.deepcopy(d["pages"][0]))
  d["pages"][1-owner]["score"]["expressiveEvents"]=[]
  # Canonical ordinary pitched events omit optional sourceNoteIndex.
  for page in d["pages"]:
   for event in page["events"]:event.pop("sourceNoteIndex",None)
  original=copy.deepcopy(d)
  with tempfile.TemporaryDirectory() as tmp:
   p=Path(tmp)/"two-pages.musicxml";write_musicxml(d,p);xml=ET.parse(p).getroot()
  self.assertEqual(original,d)
  measures=xml.findall("./part/measure")
  self.assertEqual(2,len(measures))
  self.assertEqual([3 if i==owner else 0 for i in range(2)],[len(x.findall(".//arpeggiate")) for x in measures])
