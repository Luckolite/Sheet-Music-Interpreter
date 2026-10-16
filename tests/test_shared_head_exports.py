# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original procedural shared-head voices; no private score data."""
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.midi import performance_events
from sheet_interpreter.musicxml import write_musicxml, DIVISIONS

def document():
    events = []
    for i in range(8):
        events.append(dict(midi=48 if i % 2 == 0 else 60, startBeat=i/2,
                           durationBeats=.5, staffIndex=1, staffCount=2,
                           tiedFromPrevious=False))
        if i % 2 == 0:
            events.append(dict(events[-1], durationBeats=1))
    return dict(inputName='Original shared-head voices', pages=[dict(
        events=events, measureBeats=[4], totalBeats=4, score=dict(tempoChanges=[]))])

class SharedHeadExports(unittest.TestCase):
    def test_midi_keeps_separate_channels_for_overlapping_unisons(self):
        ppq, messages, end = performance_events(document())
        active = {}
        spans = []
        for tick, _, msg in sorted(messages):
            if msg[0] & 240 == 144:
                key = (msg[0] & 15, msg[1])
                self.assertNotIn(key, active)
                active[key] = tick
            elif msg[0] & 240 == 128:
                key = (msg[0] & 15, msg[1])
                spans.append((active.pop(key), tick, msg[1]))
        self.assertFalse(active)
        self.assertEqual(12, len(spans))
        self.assertEqual(4*ppq, end)
        for i in range(4):
            roots = [(a,b) for a,b,p in spans if p == 48 and a == i*ppq]
            self.assertEqual(2, len(roots))
            self.assertEqual(2, len({b for a,b in roots}))

    def test_musicxml_keeps_both_durations_and_onsets(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'score.musicxml'
            write_musicxml(document(), path)
            root = ET.parse(path).getroot()
        notes=[]
        for bar in root.findall('part/measure'):
            cursor=0
            for node in bar:
                if node.tag == 'backup': cursor -= int(node.findtext('duration'))
                if node.tag != 'note': continue
                duration=int(node.findtext('duration'))
                if node.find('pitch') is not None:
                    notes.append((cursor,duration,node.findtext('voice')))
                if node.find('chord') is None: cursor += duration
        self.assertEqual(12,len(notes))
        for i in range(4):
            pair=[n for n in notes if n[0] == i*DIVISIONS]
            self.assertEqual({DIVISIONS//2,DIVISIONS},{n[1] for n in pair})
            self.assertEqual(2,len({n[2] for n in pair}))

if __name__ == '__main__': unittest.main()
