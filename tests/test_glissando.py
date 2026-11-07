# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
import unittest
import tempfile
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_music_recognition.midi import performance_events, white_key_gliss
from sheet_music_recognition.musicxml import write_musicxml


def note(pitch, start, duration, **extra):
    return dict(midi=pitch, startBeat=start, durationBeats=duration,
                staffCount=1, staffIndex=0, tiedFromPrevious=False, **extra)


class GlissandoTests(unittest.TestCase):
    def test_two_octaves_are_discrete_and_target_keeps_its_onset(self):
        source = note(84, 0, .375, glissando=dict(style='white_keys', targetMidi=60))
        target = note(60, .375, .375)
        page = dict(events=[source, target], measureBeats=[1], totalBeats=1,
                    score=dict(tempoChanges=[]))
        ppq, events, end = performance_events(dict(pages=[page]))
        ons = [(tick, message[1]) for tick, _, message in events if message[0] & 0xf0 == 0x90]
        self.assertEqual([84,83,81,79,77,76,74,72,71,69,67,65,64,62,60], [p for _, p in ons])
        self.assertEqual((180,60), ons[-1])
        self.assertEqual(120, ons[1][0])
        self.assertEqual(480, end)
        self.assertFalse(any(message[0] & 0xf0 == 0xe0 for _, _, message in events))
        self.assertNotIn('glissando', target)

    def test_black_endpoints_are_retained_and_window_closes(self):
        steps = white_key_gliss(90,210,61,70)
        self.assertEqual([61,62,64,65,67,69], [p for _, _, p in steps])
        self.assertEqual(90, steps[0][0])
        self.assertEqual(210, steps[-1][1])
        self.assertTrue(all(a[1] == b[0] for a,b in zip(steps,steps[1:])))

    def test_unison_and_adjacent_do_not_add_an_attack(self):
        for target in (60,61):
            self.assertEqual([(0,120,60)], white_key_gliss(0,120,60,target))

    def test_invalid_metadata_is_rejected(self):
        for metadata in ({'style':'chromatic','targetMidi':60},
                         {'style':'white_keys','targetMidi':True},
                         {'style':'white_keys','targetMidi':128}):
            page = dict(events=[note(84,0,1,glissando=metadata)], measureBeats=[1],
                        totalBeats=1, score=dict(tempoChanges=[]))
            with self.assertRaises(ValueError):
                performance_events(dict(pages=[page]))

    def test_musicxml_chained_glissandos_keep_all_four_endpoints(self):
        notes = [note(84,0,1,glissando=dict(style='white_keys',targetMidi=60)),
                 note(60,1,1,glissando=dict(style='white_keys',targetMidi=72)), note(72,2,1)]
        page = dict(events=notes,measureBeats=[3],totalBeats=3,score=dict(tempoChanges=[]))
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'original.musicxml'
            write_musicxml(dict(pages=[page]),path)
            tree=ET.parse(path)
        endpoints=tree.findall('.//glissando')
        self.assertEqual(['start','stop','start','stop'], [n.get('type') for n in endpoints])
        self.assertEqual(endpoints[0].get('number'), endpoints[1].get('number'))
        self.assertEqual(endpoints[2].get('number'), endpoints[3].get('number'))
        self.assertTrue(all(n.get('line-type')=='wavy' for n in endpoints))

    def test_musicxml_five_in_three_preserves_regular_closing_attack(self):
        notes=[note(60+i,.15*i,.15,tupletActualNotes=5,tupletNormalNotes=3) for i in range(5)]
        notes.append(note(65,.75,.25))
        page=dict(events=notes,measureBeats=[1],totalBeats=1,score=dict(tempoChanges=[]))
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'original.musicxml'
            write_musicxml(dict(pages=[page]),path)
            tree=ET.parse(path)
        modifications=tree.findall('.//time-modification')
        self.assertEqual(5,len(modifications))
        self.assertTrue(all(n.findtext('actual-notes')=='5' and n.findtext('normal-notes')=='3' for n in modifications))
        self.assertEqual('2520',tree.findall('.//note')[-1].findtext('duration'))
        self.assertIsNone(tree.findall('.//note')[-1].find('time-modification'))
