# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Model-free actual reader/parser/CLI operations fed by the isolated real Main bridge."""
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image
from sheet_interpreter import cli, reader
from sheet_interpreter.typed_performance import unpitched_intervals
import test_main_typed_cli as bridge_fixture
from test_main_typed_cli import CORE, PACKAGE, jdk

REAL_RUN = subprocess.run
REAL_POPEN = subprocess.Popen

class TypedFrontendCliTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        bridge_fixture.MainTypedCliTests.setUpClass()
        cls.backend = bridge_fixture.MainTypedCliTests.classes
    @classmethod
    def tearDownClass(cls):
        bridge_fixture.MainTypedCliTests.tearDownClass()
        bridge_fixture.MainTypedCliTests.doClassCleanups()
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='typed-frontend-', dir=os.environ.get('MAIN_TYPED_OUTPUT_ROOT'))
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.image = self.folder/'original.png'
        Image.new('L',(8,8),255).save(self.image)
        self.annotations = self.folder/'annotations.json'
        self.annotations.write_text('[{}]',encoding='utf-8')
        self.output = self.folder/'decoded.json'
        # The real interpret method runs; only unavailable inference and its resize input are supplied.
        self.engine = reader.Interpreter.__new__(reader.Interpreter)
        self.engine.jar = Path('unused-isolated-fixture.jar')
        self.engine.model_sha256 = 'original-synthetic-no-model-used'
        self.engine.ocr = None
        self.fixture = 1
        def predict(gray):
            labels = np.zeros_like(gray)
            labels.flat[0] = self.fixture
            return labels
        self.engine.predict = predict
    def bridge_run(self,args,**kwargs):
        if '-jar' in args:
            return REAL_RUN([jdk('java'),'-cp',str(self.backend)+os.pathsep+CORE,
                    PACKAGE+'.Main',*map(str,args[-5:])],**kwargs)
        return REAL_RUN(args,**kwargs)
    def invoke(self,*exports,reject=None):
        args=['sheet-interpreter',str(self.image),'-o',str(self.output),'--annotations',str(self.annotations),*map(str,exports)]
        errors=io.StringIO()
        with patch('sys.argv',args), patch.object(cli,'Interpreter',return_value=self.engine), \
             patch.object(reader,'grayscale',return_value=np.full((8,8),255,dtype=np.uint8)), \
             patch.object(reader.subprocess,'run',side_effect=self.bridge_run), \
             contextlib.redirect_stderr(errors):
            if reject:
                with self.assertRaises(SystemExit) as result:cli.main()
                self.assertEqual(1,result.exception.code)
                self.assertIn(reject,errors.getvalue())
            else:cli.main()
        return json.loads(self.output.read_text(encoding='utf-8'))
    def test_actual_reader_json_parse_preserves_kind_metadata_and_source_indices(self):
        value=self.invoke()
        page=value['pages'][0]
        self.assertEqual([1,2],[e['sourceNoteIndex'] for e in page['events'][1:]])
        self.assertEqual(['UNPITCHED']*2,[e['kind'] for e in page['events'][1:]])
        self.assertEqual('UNPITCHED',page['score']['notes'][1]['kind'])
        self.assertEqual(1,page['sourcePage'])
        self.assertEqual(4,page['totalBeats'])
        self.assertEqual([4],page['measureBeats'])
        self.assertEqual(['unpitched:0:1','unpitched:0:2'],[n['sourceId'] for n in unpitched_intervals(value)])
        for e in page['events'][1:]:self.assertNotIn('midi',e)
    def test_actual_cli_xml_preserves_all_heads_and_written_chord_clocks(self):
        path=self.folder/'written.musicxml'
        value=self.invoke('--musicxml',path)
        doc=ET.parse(path)
        self.assertEqual(2,len(doc.findall('.//unpitched')))
        self.assertEqual(1,len(doc.findall('.//pitch')))
        cross=[n for n in doc.findall('.//note') if n.find('unpitched') is not None]
        self.assertEqual(['x','x'],[n.findtext('notehead') for n in cross])
        self.assertEqual(['10080','10080'],[n.findtext('duration') for n in cross])
        self.assertEqual(['E','E'],[n.findtext('unpitched/display-step') for n in cross])
        self.assertEqual([0,0],[e['startBeat'] for e in value['pages'][0]['events'][1:]])
    def test_actual_reader_retains_neutral_dead_without_inventing_pitch(self):
        self.fixture=13
        value=self.invoke();event=value['pages'][0]['events'][0]
        self.assertEqual('UNPITCHED',event['kind']);self.assertEqual(0,event['sourceNoteIndex'])
        self.assertEqual(dict(type='dead',semitones=0,vibrato=False,palmMute=False),event['guitarEffect'])
        self.assertNotIn('midi',event)
        self.assertEqual(1,event['durationBeats'])
    def test_raw_octave_shift_reader_failure_preserves_existing_json(self):
        self.fixture=10
        self.output.write_text('{"existing":"json"}',encoding='utf-8')
        value=self.invoke(reject='source octave shifts need explicit instrument semantics')
        self.assertEqual(dict(existing='json'),value)
    def test_default_cli_midi_rejects_typed_notes_without_replacing_output(self):
        path=self.folder/'preview.mid';path.write_bytes(b'existing-midi')
        value=self.invoke('--midi',path,reject='explicit UnpitchedMidiPreview policy')
        self.assertEqual(b'existing-midi',path.read_bytes())
        self.assertEqual('UNPITCHED',value['pages'][0]['events'][1]['kind'])
    def test_default_cli_mp3_rejects_before_encoder_and_preserves_output(self):
        path=self.folder/'preview.mp3';path.write_bytes(b'existing-mp3')
        encoder_calls=[]
        def launch(args,*values,**options):
            if args[0]=='unused-ffmpeg':
                encoder_calls.append(args)
                raise AssertionError('Unsupported raw U must not launch the MP3 encoder')
            return REAL_POPEN(args,*values,**options)
        with patch('sheet_interpreter.audio.find_ffmpeg',return_value='unused-ffmpeg'), \
             patch('sheet_interpreter.audio.subprocess.Popen',side_effect=launch):
            value=self.invoke('--mp3',path,reject='explicit UnpitchedAudioPreview policy')
            self.assertEqual([],encoder_calls)
        self.assertEqual(b'existing-mp3',path.read_bytes())
        self.assertEqual('UNPITCHED',value['pages'][0]['events'][1]['kind'])

if __name__=='__main__':unittest.main()
