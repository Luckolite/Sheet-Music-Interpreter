# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Actual Main CLI with an isolated original scalar analysis backend, no model input."""
import gzip
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest

ROOT = Path(os.environ.get('MAIN_TYPED_REPOSITORY', Path(__file__).resolve().parents[1]))
CORE = os.environ.get('MAIN_TYPED_CORE', str(ROOT / 'build/classes'))
MAIN = os.environ.get('MAIN_TYPED_MAIN')
PACKAGE = 'io.github.luckolite.interpreter'

BACKEND = r'''
// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
public final class SheetInterpreter {
    public record NumberToken(int value,float left,float top,float right,float bottom,float annotationLeft) {}
    public record Word(String text,float left,float top,float right,float bottom) {}
    public record Annotations(List<NumberToken> measureNumbers,List<NumberToken> tempoNumbers,
            List<NumberToken> restCounts,List<Word> words,List<ScoreMeterChange> meters,List<Word> tabWords) {
        public static final Annotations EMPTY=new Annotations(List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
    }
    private static ScoreNoteEvent note(float x,int step) {
        return new ScoreNoteEvent(0,x,step,0,1,.3f,false,0,0,
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,1,1,0,0,ScoreNoteEvent.CLEF_TREBLE);
    }
    private static ScoreNoteEvent cross(float x,int step) {
        return note(x,step).withKind(ScoreNoteEvent.Kind.UNPITCHED);
    }
    public static ScorePageInterpretation analyze(byte[] labels,byte[] gray,int width,int height,Annotations annotations) {
        if(width!=8||height!=8||labels.length!=64||gray.length!=64)throw new AssertionError("Original 8x8 envelope required");
        for(byte value:gray)if(value!=(byte)255)throw new AssertionError("Synthetic grayscale must be blank");
        List<ScoreNoteEvent> notes;
        switch(labels[0]) {
            case 0 -> notes=List.of(note(.1f,0),note(.5f,2));
            case 1 -> {
                var a=cross(.2f,0);var b=cross(.2f,0);
                if(a==b||!a.equals(b))throw new AssertionError("Distinct equal U records required");
                notes=List.of(note(.2f,0),a,b);
            }
            case 2 -> {var a=cross(.2f,0);notes=List.of(a,a);}
            case 3 -> {
                var tuplets=new ArrayList<ScoreNoteEvent>();
                for(int i=0;i<6;i++)tuplets.add(cross(.1f+i*.14f,i).withTupletRatio(5,3));
                notes=List.copyOf(tuplets);
            }
            case 4 -> notes=List.of(new ScoreNoteEvent(0,.2f,0,0,1,.3f,true,0,0,
                    ScoreNoteEvent.ACCIDENTAL_FROM_KEY,1).withClef(30).withKind(ScoreNoteEvent.Kind.UNPITCHED));
            case 5 -> notes=List.of(cross(.2f,0).withArticulations(NoteOrnament.TRILL));
            case 6 -> notes=List.of(cross(.2f,0).withBoundaryTies(1));
            case 7 -> notes=List.of(cross(.2f,Integer.MAX_VALUE));
            case 8 -> notes=List.of(cross(.2f,-100));
            case 9 -> notes=List.of(new ScoreNoteEvent(0,.2f,0,1,2,.3f,false,0,0,
                    ScoreNoteEvent.ACCIDENTAL_FROM_KEY,1).withKind(ScoreNoteEvent.Kind.UNPITCHED));
            case 10 -> notes=List.of(cross(.2f,0).withOctaveShift(2));
            case 11 -> notes=List.of(cross(.2f,0).withLeadingRest(.25f));
            case 12 -> notes=List.of(cross(.2f,0).withArticulations(NoteOrnament.GLISSANDO),note(.5f,2));
            case 13 -> notes=List.of(cross(.2f,0).withArticulations(TabEffect.encode(TabEffect.DEAD,0)));
            case 14 -> notes=List.of(cross(.2f,0).withArticulations(TabEffect.encode(TabEffect.DEAD,2)));
            case 15 -> notes=List.of(cross(.2f,0).withArticulations(TabEffect.encode(TabEffect.DEAD,0)|TabEffect.VIBRATO));
            case 16 -> notes=List.of(cross(.2f,0).withArticulations(TabEffect.encode(TabEffect.DEAD,0)|TabEffect.PALM_MUTE));
            case 17 -> notes=List.of(cross(.2f,0).withArticulations(TabEffect.encode(TabEffect.BEND,0)));
            default -> throw new AssertionError("Unknown original fixture");
        }
        return new ScorePageInterpretation(List.of(new MeasureRegion(.1f,.9f,.2f,.4f)),notes);
    }
}
'''

def jdk(name):
    suffix='.exe' if os.name=='nt' else ''
    if os.environ.get('JAVA_HOME'):
        path=Path(os.environ['JAVA_HOME'])/'bin'/(name+suffix)
        if path.is_file():return str(path)
    path=shutil.which(name)
    if not path:raise RuntimeError('JDK17+ required: '+name)
    return path

def clean_stderr(text):
    banners={prefix+os.environ[name] for name,prefix in (
        ('JAVA_TOOL_OPTIONS','Picked up JAVA_TOOL_OPTIONS: '),
        ('JDK_JAVA_OPTIONS','NOTE: Picked up JDK_JAVA_OPTIONS: '),
        ('_JAVA_OPTIONS','Picked up _JAVA_OPTIONS: ')) if os.environ.get(name)}
    return '\n'.join(line for line in text.splitlines() if line not in banners)

class MainTypedCliTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        parent=os.environ.get('MAIN_TYPED_OUTPUT_ROOT')
        cls.folder=Path(tempfile.mkdtemp(prefix='main-typed-cli-',dir=parent))
        if not os.environ.get('MAIN_TYPED_KEEP_OUTPUT'):cls.addClassCleanup(shutil.rmtree,cls.folder)
        elif parent:(Path(parent)/'execution-directory.txt').write_text(str(cls.folder))
        cls.real_core=ROOT/'build/classes'
        cls.guards=[ROOT/'java/src/main/java/io/github/luckolite/interpreter'/n for n in ('Main.java','SheetInterpreter.java')]
        cls.guards +=[cls.real_core/'io/github/luckolite/interpreter'/n for n in ('Main.class','SheetInterpreter.class')]
        cls.before={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in cls.guards}
        backend=cls.folder/'SheetInterpreter.java';backend.write_text(BACKEND,encoding='utf-8')
        cls.classes=cls.folder/'classes'
        sources=([MAIN] if MAIN else [])+[str(backend)]
        p=subprocess.run([jdk('javac'),'--release','17','-encoding','UTF-8','-implicit:none',
            '-cp',CORE,'-d',str(cls.classes),*sources],capture_output=True,text=True,timeout=60)
        (cls.folder/'compile.log').write_text(p.stdout+p.stderr,encoding='utf-8')
        if p.returncode:raise RuntimeError(p.stderr)
    @classmethod
    def tearDownClass(cls):
        after={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in cls.guards}
        if after!=cls.before:raise AssertionError('Real source/classes changed by isolated fixture')
        (cls.folder/'isolation-receipt.json').write_text(json.dumps({'status':'PASS_ISOLATED_TEST_BACKEND',
            'testChildClasspath':[str(cls.classes),CORE],'realBackendClasspath':str(cls.real_core),
            'globalTestClasspathUsed':False,'productionSourceAndClassHashesUnchanged':after},indent=2)+'\n')
    def bridge(self,fixture,*,reject=None):
        page=self.folder/f'fixture-{fixture}.page.gz';output=self.folder/f'fixture-{fixture}.json'
        page.write_bytes(gzip.compress(struct.pack('>III',0x52535031,8,8)+bytes([fixture])+bytes(63)+bytes([255])*64,mtime=0))
        if reject:output.write_text('existing-output',encoding='utf-8')
        run=subprocess.run([jdk('java'),'-cp',str(self.classes)+os.pathsep+CORE,PACKAGE+'.Main',
            str(page),str(output),'4','4','7'],capture_output=True,text=True,timeout=30)
        (self.folder/f'fixture-{fixture}.stderr').write_text(run.stderr,encoding='utf-8')
        if reject:
            self.assertNotEqual(0,run.returncode)
            self.assertIn(reject,clean_stderr(run.stderr))
            self.assertEqual('existing-output',output.read_text(encoding='utf-8'))
            return None
        self.assertEqual(0,run.returncode,run.stderr)
        self.assertEqual('',run.stdout)
        self.assertEqual('',clean_stderr(run.stderr))
        return json.loads(output.read_text(encoding='utf-8'))
    def test_mixed_equal_display_heads_keep_kind_and_independent_indices(self):
        value=self.bridge(1);events=value['events']
        self.assertEqual([1,2],[e['sourceNoteIndex'] for e in events[1:]])
        self.assertEqual(['UNPITCHED']*2,[e['kind'] for e in events[1:]])
        self.assertEqual(['E']*2,[e['displayStep'] for e in events[1:]])
        self.assertEqual([4]*2,[e['displayOctave'] for e in events[1:]])
        self.assertIn('midi',events[0]);self.assertNotIn('sourceNoteIndex',events[0])
        for e in events[1:]:
            for key in ('midi','boundaryPitch','boundaryAccidental','octaveShift'):self.assertNotIn(key,e)
        self.assertEqual('UNPITCHED',value['score']['notes'][1]['kind'])
    def test_repeated_same_object_keeps_two_source_occurrences(self):
        value=self.bridge(2)
        self.assertEqual([0,1],[e['sourceNoteIndex'] for e in value['events']])
        self.assertEqual(value['score']['notes'][0],value['score']['notes'][1])
    def test_typed_tuplets_retain_finite_written_clocks(self):
        events=self.bridge(3)['events']
        self.assertEqual(list(range(6)),[e['sourceNoteIndex'] for e in events])
        for i,e in enumerate(events):
            self.assertEqual(5,e['tupletActualNotes']);self.assertEqual(3,e['tupletNormalNotes'])
            self.assertAlmostEqual(.6,e['durationBeats']);self.assertFalse(e['durationFallback'])
            self.assertTrue(math.isfinite(e['startBeat']) and e['startBeat']>=0)
            self.assertAlmostEqual(i*.6,e['startBeat'])
    def test_raw_unpitched_tie_rejects_before_output(self):self.bridge(4,reject='explicit instrument semantics')
    def test_raw_unpitched_trill_rejects_before_output(self):self.bridge(5,reject='explicit instrument semantics')
    def test_raw_unpitched_boundary_evidence_rejects_before_output(self):self.bridge(6,reject='explicit instrument semantics')
    def test_display_overflow_cannot_wrap_into_a_valid_octave(self):self.bridge(7,reject='display octave')
    def test_negative_display_octave_rejects_before_output(self):self.bridge(8,reject='display octave')
    def test_unknown_lower_staff_clef_is_display_only(self):
        e=self.bridge(9)['events'][0]
        self.assertTrue(e['clefInferred']);self.assertEqual(18,e['clefBottomDiatonic'])
        self.assertEqual(('G',2),(e['displayStep'],e['displayOctave']));self.assertNotIn('midi',e)
    def test_raw_unpitched_octave_shift_rejects_before_output(self):
        self.bridge(10,reject='source octave shifts need explicit instrument semantics')
    def test_explicit_leading_rest_retains_nonzero_written_onset(self):
        e=self.bridge(11)['events'][0]
        self.assertEqual(.25,e['startBeat']);self.assertEqual(1,e['durationBeats'])
    def test_raw_unpitched_gliss_rejects_before_target_lookup(self):self.bridge(12,reject='explicit instrument semantics')
    def test_exact_neutral_dead_retains_typed_metadata_and_written_clock(self):
        value=self.bridge(13);e=value['events'][0]
        self.assertEqual('UNPITCHED',e['kind']);self.assertEqual(0,e['sourceNoteIndex'])
        self.assertEqual(dict(type='dead',semitones=0,vibrato=False,palmMute=False),e['guitarEffect'])
        self.assertEqual(0,e['startBeat']);self.assertEqual(1,e['durationBeats'])
        self.assertNotIn('midi',e)
        self.assertNotEqual(0,value['score']['notes'][0]['articulations'])
    def test_dead_with_pitch_delta_rejects_before_output(self):self.bridge(14,reject='explicit instrument semantics')
    def test_dead_with_vibrato_rejects_before_output(self):self.bridge(15,reject='explicit instrument semantics')
    def test_dead_with_palm_mute_rejects_before_output(self):self.bridge(16,reject='explicit instrument semantics')
    def test_other_tab_effect_even_at_zero_delta_rejects_before_output(self):self.bridge(17,reject='explicit instrument semantics')
    def test_real_analysis_class_remains_unshadowed(self):
        page=self.folder/'real.page.gz';output=self.folder/'real.json'
        page.write_bytes(gzip.compress(struct.pack('>III',0x52535031,8,8)+bytes(64)+bytes([255])*64,mtime=0))
        run=subprocess.run([jdk('java'),'-cp',str(self.real_core),PACKAGE+'.Main',str(page),str(output),'4','4','0'],capture_output=True,text=True,timeout=30)
        self.assertEqual(0,run.returncode,run.stderr)
        self.assertEqual([],json.loads(output.read_text(encoding='utf-8'))['events'])

if __name__=='__main__':unittest.main()
