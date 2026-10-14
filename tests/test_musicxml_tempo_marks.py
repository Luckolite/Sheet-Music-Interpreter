# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original tempo glyph/placement controls; quarter-note sound clocks are invariant."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml


def document(changes):
    return dict(initialBpm=120,pages=[dict(measureBeats=[4],events=[dict(
        midi=60,startBeat=0,durationBeats=4,staffIndex=0,tiedFromPrevious=False)],
        score=dict(tempoChanges=changes))])


class MusicXmlTempoMarkTests(unittest.TestCase):
    def export(self, changes):
        value=document(changes);before=copy.deepcopy(value)
        with tempfile.TemporaryDirectory(prefix='original-tempo-mark-') as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(value,path);root=ET.parse(path).getroot()
        self.assertEqual(before,value)
        return root

    def test_default_tempo_is_above_staff(self):
        root=self.export([])
        self.assertEqual('above',root.find('.//direction').get('placement'))
        self.assertEqual('120',root.findtext('.//per-minute'))

    def test_printed_opening_mark_replaces_fallback_heading(self):
        root=self.export([dict(measureIndex=0,positionInMeasure=0,bpm=81.5,beatUnit=.5)])
        self.assertEqual(1,len(root.findall('.//metronome')))
        self.assertEqual('eighth',root.findtext('.//beat-unit'))
        self.assertEqual('163',root.findtext('.//per-minute'))
        self.assertEqual(81.5,float(root.find('.//sound').get('tempo')))

    def test_dotted_and_short_pulses_keep_their_printed_rates(self):
        for pulse,unit,dots,marked in [(1.5,'quarter',1,80),(1.75,'quarter',2,80),
                                      (3,'half',1,40),(.75,'eighth',1,160),
                                      (.00390625,'1024th',0,30720)]:
            bpm=pulse*marked
            root=self.export([dict(measureIndex=0,positionInMeasure=0,bpm=bpm,beatUnit=pulse)])
            self.assertEqual(unit,root.findtext('.//beat-unit'))
            self.assertEqual(dots,len(root.findall('.//beat-unit-dot')))
            self.assertEqual(marked,float(root.findtext('.//per-minute')))
            self.assertEqual(bpm,float(root.find('.//sound').get('tempo')))

    def test_later_change_does_not_remove_initial_tempo(self):
        root=self.export([dict(measureIndex=0,positionInMeasure=.5,bpm=90,beatUnit=1.5)])
        self.assertEqual(['120','60'],[n.text for n in root.findall('.//per-minute')])
        self.assertEqual(['0','20160'],[n.text for n in root.findall('.//direction/offset')])
        self.assertEqual([120,90],[float(n.get('tempo')) for n in root.findall('.//sound')])

    def test_unknown_supported_numeric_pulse_keeps_equivalent_quarter_mark(self):
        root=self.export([dict(measureIndex=0,positionInMeasure=0,bpm=120,beatUnit=1.2)])
        self.assertEqual('quarter',root.findtext('.//beat-unit'))
        self.assertEqual('120',root.findtext('.//per-minute'))

    def test_legacy_tempo_without_unit_still_uses_quarter(self):
        root=self.export([dict(measureIndex=0,positionInMeasure=0,bpm=90)])
        self.assertEqual('quarter',root.findtext('.//beat-unit'))
        self.assertEqual('90',root.findtext('.//per-minute'))

    def test_fractional_rate_is_not_rounded_for_presentation(self):
        bpm=15.123456789012345
        root=self.export([dict(measureIndex=0,positionInMeasure=0,bpm=bpm,beatUnit=.5)])
        self.assertEqual(str(bpm*2),root.findtext('.//per-minute'))
        self.assertEqual(bpm,float(root.find('.//sound').get('tempo')))

    def test_invalid_tempo_preserves_existing_output(self):
        for field,value in [('bpm',float('nan')),('beatUnit',0),('beatUnit',True),('bpm',1601)]:
            change=dict(measureIndex=0,positionInMeasure=0,bpm=120,beatUnit=1);change[field]=value
            with tempfile.TemporaryDirectory(prefix='original-tempo-reject-') as folder:
                path=Path(folder)/'existing.musicxml';path.write_text('existing-output')
                with self.assertRaisesRegex(ValueError,'printed tempo'):
                    write_musicxml(document([change]),path)
                self.assertEqual('existing-output',path.read_text())
