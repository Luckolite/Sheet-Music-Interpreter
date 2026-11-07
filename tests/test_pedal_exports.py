# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original synthetic rail columns through the real shared policy and export paths."""
import copy
import math
from pathlib import Path
import struct
import tempfile
import unittest
import xml.etree.ElementTree as ET
import numpy as np
from sheet_music_recognition.performance import perform_expressions, _pedal_span
from sheet_music_recognition.midi import performance_events
from sheet_music_recognition.audio import _sample_events, _pcm_blocks, SAMPLE_RATE
from sheet_music_recognition.musicxml import write_musicxml, DIVISIONS


def bits(value):
    return struct.unpack('>i', struct.pack('>f', value))[0]


def rail(first, last, staff=0, count=1, identity='rail'):
    target = 'pedal-hooks-v1:'+':'.join(map(str, [*first, *last, staff, count]))
    common = dict(start=None, end=None, scope='UNRESOLVED', staffIndex=staff,
                  staffCount=count, targetEventId=target, strength='UNSPECIFIED',
                  qualifierText='continuous bracket', evidence=[dict(sourceId='printed-pedal-bracket',
                  pageIndex=0, visualX=.1, staffIndex=staff, staffCount=count, printedText='')])
    return [dict(common, eventId=identity+kind, kind=kind) for kind in ('PEDAL_DOWN', 'PEDAL_UP')]


def doc(marks=None):
    # One attack followed by an exact silent slot; a full second measure bounds the score.
    note = dict(startBeat=0, midi=69, durationBeats=1, durationFallback=False,
                tiedFromPrevious=False, staffIndex=0, staffCount=1)
    raw = dict(measureIndex=0, positionInMeasure=.2, articulations=0, staffIndex=0, staffCount=1)
    if marks is None:
        marks = rail((0, 0, 0, 0), (1, 0, 0, 0))
    return dict(inputName='Original synthetic pedal example', pages=[dict(events=[note],
        measureBeats=[4, 4], totalBeats=8, score=dict(firstMeasureNumber=1, notes=[raw],
        rests=[dict(measureIndex=0, positionInMeasure=.7, durationBeats=3, staffIndex=0, staffCount=1)],
        tempoChanges=[], expressiveEvents=marks))])


def controllers(document, bpm=120):
    _, messages, end = performance_events(document, bpm)
    return [(tick, message[0]&15, message[2]) for tick, _, message in messages
            if message[0]&0xf0 == 0xb0 and message[1] == 64], end


class PedalExportTests(unittest.TestCase):
    def test_barline_hooks_preserve_note_off_extent_and_source(self):
        document = doc(); original = copy.deepcopy(document)
        controls, end = controllers(document)
        self.assertEqual([(0, 0, 127), (1920, 0, 0)], controls)
        self.assertEqual(3840, end)
        plain = performance_events(doc([]))[1]
        actual = performance_events(document)[1]
        notes = lambda rows: [(t, m) for t, _, m in rows if m[0]&0xf0 in (0x80, 0x90)]
        self.assertEqual(notes(plain), notes(actual))
        self.assertEqual(original, document)

    def test_note_hook_uses_musical_onset_instead_of_x(self):
        document = doc(rail((0, 1, bits(.2), bits(.001)), (1, 0, 0, 0)))
        self.assertEqual(0, _pedal_span(document['pages'][0]['score']['expressiveEvents'][0], document['pages'][0])[0]['quarterBeatOffset'])
        self.assertEqual(0, controllers(document)[0][0][0])

    def test_rest_hook_uses_exact_silent_slot(self):
        document = doc(rail((0, 2, bits(.7), bits(.001)), (1, 0, 0, 0)))
        self.assertEqual([(480, 0, 127), (1920, 0, 0)], controllers(document)[0])
        document['pages'][0]['score']['rests'][0]['durationBeats'] = 2
        self.assertEqual([], controllers(document)[0])

    def test_ambiguous_chord_column_refuses_cached_anchor(self):
        document = doc(rail((0, 1, bits(.2), bits(.001)), (1, 0, 0, 0)))
        page = document['pages'][0]
        page['score']['notes'].append(dict(page['score']['notes'][0]))
        page['events'].append(dict(page['events'][0], midi=72, startBeat=.5))
        for mark in page['score']['expressiveEvents']:
            mark.update(scope='PART', start=dict(measureIndex=0, quarterBeatOffset=0))
        self.assertEqual([], controllers(document)[0])

    def test_grace_and_fallback_columns_stay_unresolved(self):
        for field in ('grace', 'fallback'):
            with self.subTest(field=field):
                document = doc(rail((0, 1, bits(.2), bits(.001)), (1, 0, 0, 0)))
                page = document['pages'][0]
                if field == 'grace':
                    page['score']['notes'][0]['articulations'] = 1 << 15
                else:
                    page['events'][0]['durationFallback'] = True
                self.assertEqual([], controllers(document)[0])

    def test_invalid_ownership_and_nonfinite_columns_do_not_realize(self):
        variants = [lambda m: m['evidence'][0].update(sourceId='unrelated'),
                    lambda m: m['evidence'][0].update(staffIndex=1, staffCount=2),
                    lambda m: m.update(targetEventId='pedal-hooks-v1:0:1:'+str(bits(math.nan))+':0:1:0:0:0:0:1'),
                    lambda m: m.update(targetEventId='pedal-hooks-v1:0:0:0:0:99:0:0:0:0:1'),
                    lambda m: m.update(targetEventId='pedal-hooks-v1:0:1:4294967296:0:1:0:0:0:0:1')]
        for mutate in variants:
            document = doc()
            for mark in document['pages'][0]['score']['expressiveEvents']:
                mutate(mark)
            self.assertEqual([], controllers(document)[0])

    def test_shared_boundary_releases_before_repress(self):
        marks = rail((0, 0, 0, 0), (1, 0, 0, 0), identity='first')
        marks += rail((1, 0, 0, 0), (2, 0, 0, 0), identity='second')
        self.assertEqual([(0, 0, 127), (1920, 0, 0), (1920, 0, 127), (3840, 0, 0)], controllers(doc(marks))[0])

    def test_missing_release_stays_diagnostic_and_finite(self):
        document = doc(); document['pages'][0]['score']['expressiveEvents'].pop()
        result = perform_expressions(document, 120)['expressivePerformance'][0]
        self.assertEqual([], result['pedalSpans'])
        self.assertTrue(any('missing pedal release' in s for s in result['diagnostics']))
        self.assertEqual([], controllers(document)[0])

    def test_pedal_does_not_capture_unrelated_part(self):
        document = doc(); page = document['pages'][0]
        page['score']['notes'].append(dict(page['score']['notes'][0], staffIndex=1, staffCount=2))
        page['events'].append(dict(page['events'][0], midi=60, staffIndex=1, staffCount=2))
        _, messages, _ = performance_events(document)
        channels = {m[1]: m[0]&15 for _, _, m in messages if m[0]&0xf0 == 0x90}
        self.assertNotEqual(channels[69], channels[60])
        self.assertEqual({channels[69]}, {channel for _, channel, _ in controllers(document)[0]})

    def test_repeated_pitch_keeps_held_voice_separate(self):
        document = doc(); page = document['pages'][0]
        page['score']['notes'].append(dict(page['score']['notes'][0], positionInMeasure=.4))
        page['events'].append(dict(page['events'][0], startBeat=2))
        _, messages, _ = performance_events(document)
        channels = [m[0]&15 for _, _, m in messages if m[0]&0xf0 == 0x90]
        self.assertEqual(2, len(set(channels)))

    def test_channel_bend_reset_waits_until_damper_release(self):
        document=doc();document['pages'][0]['events'][0]['guitarEffect']=dict(type='bend',semitones=1)
        _, messages, _=performance_events(document)
        resets=[tick for tick, _, m in messages if m[0]&0xf0==0xe0 and m[1:]==bytes([0,64])]
        self.assertEqual([0,1920],resets)
        self.assertEqual([480],[tick for tick, _, m in messages if m[0]&0xf0==0x80])

    def test_pedal_controls_follow_tempo_policy(self):
        document = doc()
        mark = copy.deepcopy(document['pages'][0]['score']['expressiveEvents'][0])
        mark.update(eventId='rit', kind='RITARDANDO', scope='SCORE', targetEventId=None,
                    start=dict(measureIndex=0, quarterBeatOffset=0), end=dict(measureIndex=1, quarterBeatOffset=0))
        document['pages'][0]['score']['expressiveEvents'].append(mark)
        controls, end = controllers(document)
        self.assertAlmostEqual(10*math.log(1.25)*960, controls[1][0], delta=1)
        self.assertGreater(end, 3840)

    def test_piano_preview_sustains_and_damps_without_clock_extension(self):
        baseline, end = _sample_events(doc([]), 120)
        held, held_end = _sample_events(doc(), 120)
        self.assertEqual(end, held_end)
        render = lambda rows: np.frombuffer(b''.join(_pcm_blocks(rows, end)), dtype='<f4')
        plain, pedal = render(baseline), render(held)
        self.assertTrue(np.allclose(plain[:int(.49*SAMPLE_RATE)], pedal[:int(.49*SAMPLE_RATE)], atol=1e-7))
        self.assertEqual(0, np.max(np.abs(plain[SAMPLE_RATE:int(1.5*SAMPLE_RATE)])))
        self.assertGreater(np.max(np.abs(pedal[SAMPLE_RATE:int(1.5*SAMPLE_RATE)])), .01)
        self.assertEqual(0, np.max(np.abs(pedal[int(2.1*SAMPLE_RATE):])))

    def test_pcm_redepress_cannot_recapture_released_resonance(self):
        messages = [(0, bytes([0xb0,64,127])), (0, bytes([0x90,69,80])),
                    (100, bytes([0x80,69,0])), (1000, bytes([0xb0,64,0])),
                    (1000, bytes([0xb0,64,127])), (10000, bytes([0xb0,64,0]))]
        samples = np.frombuffer(b''.join(_pcm_blocks(messages, 12000)), dtype='<f4')
        self.assertEqual(0, np.max(np.abs(samples[6000:9000])))

    def test_musicxml_has_written_brackets_owner_and_final_release(self):
        marks = rail((0, 0, 0, 0), (1, 0, 0, 0), identity='first')
        marks += rail((1, 0, 0, 0), (2, 0, 0, 0), identity='second')
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'original.musicxml'
            write_musicxml(doc(marks), path)
            root = ET.parse(path).getroot()
        self.assertEqual(['start','stop','start','stop'], [e.get('type') for e in root.findall('.//pedal')])
        self.assertTrue(all(e.get('line') == 'yes' and e.get('sign') == 'no' for e in root.findall('.//pedal')))
        directions = [e for e in root.findall('.//direction') if e.find('direction-type/pedal') is not None]
        self.assertEqual([0, 0, 0, 4*DIVISIONS], [int(e.findtext('offset')) for e in directions])
        self.assertTrue(all(e.get('placement') == 'below' for e in directions))


if __name__ == '__main__':
    unittest.main()
