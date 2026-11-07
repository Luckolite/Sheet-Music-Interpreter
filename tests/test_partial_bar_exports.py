# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original synthetic partial bars; written meter is independent of proved span."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

from sheet_music_recognition.musicxml import write_musicxml, DIVISIONS
from sheet_music_recognition.midi import performance_events


def document():
    events = []
    raw = []
    for staff in range(2):
        for bar, (start, duration) in enumerate([(0, 1), (1, 4), (5, .5)]):
            events.append(dict(startBeat=start, durationBeats=duration,
                               midi=60+staff*12, durationFallback=False,
                               tiedFromPrevious=False, staffIndex=staff, staffCount=2))
            raw.append(dict(measureIndex=bar, positionInMeasure=.2,
                            staffIndex=staff, staffCount=2, articulations=0))
    return dict(inputName='Original synthetic partial boundary bars', initialMeter=[4, 4],
                pages=[dict(measureBeats=[1, 4, 1], totalBeats=6, events=events,
                            score=dict(firstMeasureNumber=1, notes=raw, rests=[],
                                       keyChanges=[], meterChanges=[], tempoChanges=[],
                                       expressiveEvents=[]))])


def extent(measure):
    cursor = maximum = 0
    for child in measure:
        if child.tag in ('backup', 'forward'):
            duration = int(child.findtext('duration'))
            cursor += duration if child.tag == 'forward' else -duration
        elif child.tag == 'note' and child.find('grace') is None:
            duration = int(child.findtext('duration'))
            if child.find('chord') is None:
                cursor += duration
            maximum = max(maximum, cursor)
    return maximum


class PartialBarExportTests(unittest.TestCase):
    def test_written_xml_preserves_partial_lengths_without_replacing_meter(self):
        source = document(); before = copy.deepcopy(source)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'partial.musicxml'
            write_musicxml(source, path)
            root = ET.parse(path).getroot()
            for part in root.findall('part'):
                bars = part.findall('measure')
                self.assertEqual([DIVISIONS, 4*DIVISIONS, DIVISIONS], [extent(b) for b in bars])
                self.assertEqual(['yes', None, 'yes'], [b.get('implicit') for b in bars])
                self.assertEqual(['4']*3, [b.findtext('attributes/time/beats') for b in bars])
                self.assertEqual(['4']*3, [b.findtext('attributes/time/beat-type') for b in bars])
            self.assertEqual(6, len(root.findall('.//pitch')))
        self.assertEqual(before, source)

    def test_midi_attacks_and_extent_use_the_same_six_beat_clock(self):
        source = document(); before = copy.deepcopy(source)
        _, messages, end = performance_events(source, 120)
        attacks = [tick for tick, _, msg in messages if msg[0]&0xf0 == 0x90 and msg[2] > 0]
        self.assertEqual([0, 0, 480, 480, 2400, 2400], sorted(attacks))
        self.assertEqual(2880, end)
        self.assertEqual(before, source)
