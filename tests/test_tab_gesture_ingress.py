# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Generated RSP1 pages exercise real Java file ingress, decoding and JSON output."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import numpy as np
from PIL import Image, ImageDraw
from sheet_interpreter.reader import write_page

ROOT = Path(__file__).resolve().parents[1]


def fixture(kind):
    image = Image.new('L', (500,400),255)
    draw = ImageDraw.Draw(image)
    for y in range(140,241,20):
        draw.line((20,y,480,y),fill=0)
    for x in (20,480):
        draw.line((x,140,x,240),fill=0)
    def word(text,x,y):
        return dict(text=text,left=(x-7)/500,top=(y-5)/400,right=(x+7)/500,bottom=(y+5)/400)
    words = [word('5',100,140)]
    if kind == 'strum':
        words.append(word('5',100,240))
        draw.line((100,80,100,120),fill=0)
        draw.line((94,114,100,120,106,114),fill=0)
    else:
        draw.line((107,132,122,129,141,116,150,100),fill=0)
        draw.line((142,111,150,100,158,111),fill=0)
        words.append(word('1/4' if kind == 'quarter' else 'full',150,85))
        if kind == 'bar':
            words.append(word('w/bar',175,65))
    gray=np.asarray(image,dtype=np.uint8).copy()
    return np.zeros_like(gray),gray,dict(tabWords=words)


class TabGestureIngressTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.jar=ROOT/'src/sheet_interpreter/interpreter.jar'
        if not cls.jar.is_file():
            raise unittest.SkipTest('Build the Java core before file-ingress tests')
        cls.java=str(Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name == 'nt' else 'java')) if os.environ.get('JAVA_HOME') else 'java'

    def test_actual_bridge_emits_fractional_and_directional_events(self):
        with tempfile.TemporaryDirectory(prefix='tab-gesture-') as folder:
            for kind,expected in [('bend','bend'),('quarter','bend'),('bar','whammy'),('strum','strum_down')]:
                source=Path(folder)/(kind+'.page.gz');output=source.with_suffix('.json')
                write_page(source,*fixture(kind))
                subprocess.run([self.java,'-jar',str(self.jar),str(source),str(output),'4','4','0'],
                               check=True,capture_output=True,timeout=60)
                data=json.loads(output.read_text(encoding='utf-8'))
                events=data['events']
                self.assertEqual(2 if kind == 'strum' else 1,len(events),kind)
                self.assertTrue(all(e['guitarEffect']['type'] == expected for e in events),kind)
                if kind == 'quarter':self.assertEqual(.5,events[0]['guitarEffect']['semitones'])
                if kind == 'strum':self.assertEqual({0,1},{e['guitarEffect']['strumIndex'] for e in events})

    def test_selected_native_decoder_matches_raw_articulation_bits(self):
        cp=os.environ.get('NATIVE_TAB_TEST_CP')
        if not cp:self.skipTest('Native pair build supplies its selected decoder classpath')
        with tempfile.TemporaryDirectory(prefix='native-tab-gesture-') as folder:
            for kind,expected_kind in [('bend',4),('quarter',9),('bar',11),('strum',13)]:
                source=Path(folder)/(kind+'.page.gz');output=source.with_suffix('.json')
                write_page(source,*fixture(kind))
                subprocess.run([self.java,'-cp',cp,'com.musicsheets.app.pdf.NativePageGuide',str(source),str(output)],
                               check=True,capture_output=True,timeout=60)
                score=json.loads(output.read_text(encoding='utf-8'))
                self.assertEqual(2 if kind == 'strum' else 1,len(score['notes']),kind)
                self.assertTrue(all((n['articulations']>>19)&15 == expected_kind for n in score['notes']),kind)
