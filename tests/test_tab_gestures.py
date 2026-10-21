# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original synthetic performance and export controls; no commercial score material."""
import unittest
import xml.etree.ElementTree as ET
from test_tremolo import attacks, note
from sheet_interpreter import guitar_export
from sheet_interpreter.guitar_effect import pitch_offset


class TabGestureTests(unittest.TestCase):
    def tone(self, kind, delta=0, rank=0, pitch=60, duration=2):
        n = note(0, duration)
        n.update(midi=pitch, guitarEffect=dict(type=kind, semitones=delta, strumIndex=rank))
        return n

    def test_quarter_tone_uses_one_attack_and_fractional_pitch_bend(self):
        events = attacks([self.tone('bend', .5)])
        self.assertEqual(1, sum(s & 0xf0 == 0x90 for _, s, _, _ in events))
        bends = [a+(b<<7) for _, s, a, b in events if s & 0xf0 == 0xe0]
        self.assertEqual(round(8192+8192*.5/24), max(bends))
        self.assertEqual(8192, bends[-1])
        self.assertEqual([960], [t for t,s,_,_ in events if s & 0xf0 == 0x80])

    def test_negative_whammy_release_is_isolated_and_returns_to_center(self):
        other = note(0,2); other['midi'] = 67
        events = attacks([self.tone('whammy_release', -2), other])
        channels = {p:s & 15 for _,s,p,_ in events if s & 0xf0 == 0x90}
        bends = [(s & 15,a+(b<<7)) for _,s,a,b in events if s & 0xf0 == 0xe0]
        self.assertNotEqual(channels[60], channels[67])
        self.assertTrue(all(c == channels[60] for c,_ in bends))
        self.assertLess(min(b for _,b in bends),8192)
        self.assertEqual(8192,bends[-1][1])
        self.assertEqual(-2,pitch_offset('whammy_release',-2,.5))
        self.assertEqual(0,pitch_offset('whammy_release',-2,1))

    def test_strum_delays_attacks_and_preserves_releases_and_input(self):
        for kind in ('strum_down','strum_up'):
            pitches = [60,64,67] if kind == 'strum_down' else [67,64,60]
            tones = [self.tone(kind,rank=i,pitch=p) for i,p in enumerate(pitches)]
            events = attacks(tones)
            self.assertEqual(pitches,[p for _,s,p,_ in events if s & 0xf0 == 0x90])
            self.assertEqual([0,12,23],[t for t,s,_,_ in events if s & 0xf0 == 0x90])
            self.assertEqual([960]*3,[t for t,s,_,_ in events if s & 0xf0 == 0x80])
            self.assertEqual([0]*3,[n['startBeat'] for n in tones])

    def test_short_strum_stays_inside_written_span(self):
        events=attacks([self.tone('strum_down',rank=6,duration=.01)])
        on=next(t for t,s,_,_ in events if s & 0xf0 == 0x90)
        off=next(t for t,s,_,_ in events if s & 0xf0 == 0x80)
        self.assertLess(on,off)

    def test_musicxml_retains_fraction_and_signed_release(self):
        for kind,delta in [('bend',.5),('bend_release',1.5),('whammy_release',-2)]:
            notation=ET.Element('notations')
            guitar_export.write(notation,dict(type=kind,semitones=delta))
            values=[float(n.text) for n in notation.findall('./technical/bend/bend-alter')]
            self.assertEqual([delta,-delta] if kind.endswith('_release') else [delta],values)
            self.assertEqual(kind.startswith('whammy'),notation.find('./technical/bend/with-bar') is not None)
            if kind.endswith('_release'):
                self.assertIsNotNone(notation.find('./technical/bend/release'))

    def test_musicxml_strum_direction_is_pitch_order(self):
        for kind,direction in [('strum_down','up'),('strum_up','down')]:
            notation=ET.Element('notations')
            guitar_export.write(notation,dict(type=kind,semitones=0,strumIndex=1))
            self.assertEqual(direction,notation.find('arpeggiate').get('direction'))

    def test_invalid_effects_reject_instead_of_silent_detuning(self):
        for fields in [dict(semitones=True),dict(semitones=float('nan')),dict(strumIndex=7),
                       dict(strumIndex=-1),dict(strumIndex=True),dict(semitones=1)]:
            n=self.tone('strum_down');n['guitarEffect'].update(fields)
            with self.assertRaises(ValueError): attacks([n])

    def test_guide_keeps_new_effect_bits_in_existing_integer_field(self):
        from test_guide284_tab_ties import score
        from sheet_interpreter.guide_writer import encode
        import struct
        source=score()
        plain=encode(source)
        for kind,payload in [(9,33),(11,31),(12,28),(13,34),(14,32)]:
            marks=(kind<<19)|(payload<<23)
            source['notes'][0]['articulations']=marks
            data=encode(source)
            self.assertEqual(len(plain),len(data))
            diffs=[i for i,(a,b) in enumerate(zip(plain,data)) if a!=b]
            self.assertTrue(diffs)
            self.assertLessEqual(max(diffs)-min(diffs),3)
            self.assertIn(struct.pack('>i',marks),data)
