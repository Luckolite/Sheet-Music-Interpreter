# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
# Original synthetic controls; no private score material.
import sys, unittest, copy, tempfile, xml.etree.ElementTree as ET
from pathlib import Path
from sheet_interpreter import tremolo_export
from sheet_interpreter.musicxml import write_musicxml

def fixture(chord=False, actual=1, normal=1):
    n=2 if chord else 1
    notes=[];events=[]
    for side in range(2):
        for member in range(n):
            note=dict(measureIndex=0,staffIndex=0,staffCount=1,kind='PITCHED',positionInMeasure=.2+side*.4,beamCount=0,augmentationDots=0,unbeamedDurationBeats=1,tupletDivisor=actual,tupletNormalNotes=normal,articulations=0)
            notes.append(note);events.append(dict(measureIndex=0,staffIndex=0,staffCount=1,midi=60+side*7+member*4,startBeat=side*.5*normal/actual,durationBeats=.5*normal/actual,tupletActualNotes=actual,tupletNormalNotes=normal))
    origins=[dict(pageIndex=0,noteIndex=i) for i in range(n*2)]
    pair=dict(id='original-synthetic',first=origins[:n],second=origins[n:],strokes=3)
    return dict(measureBeats=[4],events=events,score=dict(notes=notes,measures=[{}],tremoloSource=dict(pairs=[pair],noteOrigins=origins)))

class TremoloExportTests(unittest.TestCase):
    def test_scalar_chord_and_tuplet_export(self):
        for chord,actual,normal in [(False,1,1),(True,1,1),(True,3,2)]:
            page=fixture(chord,actual,normal)
            with tempfile.TemporaryDirectory() as folder:
                output=Path(folder)/'score.musicxml';write_musicxml({'pages':[page]},output);root=ET.parse(output).getroot()
                self.assertEqual(2,len(root.findall('.//tremolo')))
                for note in root.findall('.//note'):
                    if note.find('pitch') is None:continue
                    self.assertEqual('quarter',note.findtext('type'))
                    self.assertEqual(round(5040*normal/actual),int(note.findtext('duration')))
    def test_invalid_ownership_or_clock_rejected(self):
        for edit in [lambda p:p['events'].pop(),lambda p:p['score']['tremoloSource']['noteOrigins'].__setitem__(1,None),lambda p:p['score']['tremoloSource']['noteOrigins'].__setitem__(1,p['score']['tremoloSource']['noteOrigins'][0]),lambda p:p['events'][1].update(durationBeats=1),lambda p:p['events'][1].update(startBeat=.75),lambda p:p['events'][1].update(sourceNoteIndex=0),lambda p:p['events'][1].update(staffIndex=1),lambda p:p['events'][1].update(tupletActualNotes=3)]:
            page=fixture();edit(page)
            with self.assertRaises(ValueError):tremolo_export.retained(page)
    def test_empty_source_unchanged(self):
        page=fixture();page['score'].pop('tremoloSource');self.assertEqual({},tremolo_export.retained(page))


import unittest
import copy
import struct
from sheet_interpreter.tremolo_performance import source_spans, pulse_windows
from sheet_interpreter.midi import performed_document, performance_events


def document(chord=True, repeat=False, hold=False):
    page = fixture(chord)
    page['totalBeats'] = 4
    page['score']['tempoChanges'] = []
    page['score']['playbackDirections'] = [dict(measureBoundary=1, kind=5)] if repeat else []
    for note in page['events']:
        note['tiedFromPrevious'] = False
    if hold:
        bits = struct.unpack('>i', struct.pack('>f', .2))[0]
        page['score']['expressiveEvents'] = [dict(eventId='pair-hold', kind='FERMATA',
            start=None, end=None, scope='UNRESOLVED', staffIndex=0, staffCount=1,
            targetEventId=f'printed-attack:0:0:1:{bits}', strength='UNSPECIFIED',
            qualifierText='', evidence=[dict(sourceId='fermata-raw-ink', pageIndex=0,
                visualX=.2, staffIndex=0, staffCount=1, printedText='fermata')])]
    return {'pages': [page]}


class PairedPerformanceTests(unittest.TestCase):
    def test_real_bridge_mid_pair_segno_and_fine(self):
        for entry in (False, True):
            doc = document(chord=False)
            page = doc['pages'][0]
            page['score']['playbackDirections'] = (
                [dict(measureBoundary=0, kind=0, details=dict(eventId='segno', quarterBeatOffset=.25)),
                 dict(measureBoundary=1, kind=8)] if entry else
                [dict(measureBoundary=0, kind=12, details=dict(eventId='fine', quarterBeatOffset=.5)),
                 dict(measureBoundary=1, kind=9)])
            played, _, _ = performed_document(doc)
            notes = sorted(played['pages'][0]['events'], key=lambda n:n['startBeat'])
            self.assertEqual([60,67,60,67,60,67] + ([67,60,67,60,67] if entry else [60,67,60]),
                             [n['midi'] for n in notes])
            self.assertAlmostEqual(4, notes[6]['startBeat'])
            self.assertAlmostEqual(4.75 if entry else 4.5, notes[-1]['startBeat']+notes[-1]['durationBeats'])
            _, messages, _ = performance_events(doc)
            attacks = [msg[1] for _,_,msg in messages if msg[0]&240 == 144 and msg[2]]
            self.assertEqual([n['midi'] for n in notes], attacks)

    def test_real_bridge_tuplet_pair(self):
        page = fixture(True, 3, 2)
        page['totalBeats'] = 4
        page['score']['tempoChanges'] = []
        for n in page['events']:
            n['tiedFromPrevious'] = False
        _, messages, _ = performance_events({'pages':[page]})
        attacks = [(t, m[1]) for t,_,m in messages if m[0]&240 == 144 and m[2]]
        self.assertEqual([0,0,64,64,128,128,192,192,256,256], [t for t,_ in attacks])
        self.assertEqual([60,64,67,71,60,64,67,71,60,64], [p for _,p in attacks])

    def test_real_bridge_midi_repeats_and_hold(self):
        for repeat, hold in [(False, False), (True, False), (False, True), (True, True)]:
            doc = document(repeat=repeat, hold=hold)
            original = copy.deepcopy(doc)
            played, _, expressive = performed_document(doc)
            self.assertTrue(expressive)
            evidence = played['expressivePerformance'][0]
            self.assertEqual(4 * (2 if repeat else 1), len(evidence['notes']))
            self.assertEqual((2 if repeat else 1) if hold else 0, len(evidence['holds']))
            for pause in evidence['holds']:
                self.assertEqual(4, len(pause['sustainedTargets']))
            ppq, messages, end = performance_events(doc)
            attacks = [(tick, msg[1]) for tick, _, msg in messages if msg[0] & 240 == 144 and msg[2]]
            self.assertEqual(12 * (2 if repeat else 1), len(attacks))
            self.assertEqual([60, 64, 67, 71] * (6 if repeat else 3), [pitch for _, pitch in attacks])
            releases = [tick for tick, _, msg in messages if msg[0] & 240 == 128]
            self.assertEqual(12 * (2 if repeat else 1), len(releases))
            if hold:
                self.assertEqual(960 if not repeat else 3360, max(releases))
            self.assertEqual(original, doc)

    def test_chord_identity_and_alternation(self):
        page = fixture(True)
        spans = source_spans(page)
        self.assertEqual(4, len(spans))
        pulses = sorted((a, owner, ordinal, b) for owner, span in spans.items()
                        for ordinal, a, b in pulse_windows(span, 3, 4, 0))
        self.assertEqual(12, len(pulses))
        self.assertEqual([0, 1, 2, 3] * 3, [p[1] for p in pulses])
        self.assertAlmostEqual(4, pulses[-1][3])
        self.assertEqual([0, 0, .5, .5], [n['startBeat'] for n in page['events']])

    def test_clipped_entry_keeps_second_chord_phase(self):
        spans = source_spans(fixture())
        pulses = sorted((a, owner, b) for owner, span in spans.items()
                        for _, a, b in pulse_windows(span, 7, 7.75, .25))
        self.assertEqual([1, 0, 1, 0, 1], [p[1] for p in pulses])
        self.assertEqual(7, pulses[0][0])
        self.assertAlmostEqual(7.75, pulses[-1][2])

    def test_repeated_tuplet_and_invalid_projection(self):
        spans = source_spans(fixture(True, 3, 2))
        for start in (0, 4):
            pulses = list(pulse_windows(spans[0], start, start + 2/3, 0))
            self.assertEqual(3, len(pulses))
        with self.assertRaises(ValueError):
            list(pulse_windows(spans[0], 0, 1, 0))


if __name__ == '__main__':
    unittest.main()
