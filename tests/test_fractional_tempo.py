# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_music_recognition.midi import write_midi
from sheet_music_recognition.musicxml import write_musicxml


class FractionalTempoTests(unittest.TestCase):
    def test_exports_preserve_fractional_quarter_tempo_without_applying_unit_twice(self):
        document = dict(pages=[dict(events=[], measureBeats=[4], totalBeats=4, score=dict(
            tempoChanges=[dict(measureIndex=0, positionInMeasure=0, bpm=81.5, beatUnit=.5)]))])
        with tempfile.TemporaryDirectory() as folder:
            midi, xml = Path(folder)/'preview.mid', Path(folder)/'preview.musicxml'
            write_midi(document, midi)
            self.assertIn(b'\xff\x51\x03' + round(60_000_000/81.5).to_bytes(3, 'big'), midi.read_bytes())
            write_musicxml(document, xml)
            self.assertIn('81.5', [n.attrib['tempo'] for n in ET.parse(xml).findall('.//sound')])
