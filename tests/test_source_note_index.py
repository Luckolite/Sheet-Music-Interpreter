# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Run the actual Main file bridge with original scalar analysis results.

Only the analysis backend is supplied by the test. Main's parser, timing,
boundary fields, event traversal and JSON serializer are the production code.
No recognition model, score scan, Android API or private data is involved.
"""
import gzip
import hashlib
import json
import os
import shutil
import struct
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(os.environ.get('SOURCE_INDEX_REPOSITORY', Path(__file__).resolve().parents[1]))
MAIN = Path(os.environ.get('SOURCE_INDEX_MAIN', ROOT / 'java/src/main/java/io/github/luckolite/interpreter/Main.java'))
PACKAGE = 'io.github.luckolite.interpreter'

# Test-only backend. It supplies real immutable production records and validates
# the duplicate-instance/same-object distinction before Main receives the score.
BACKEND = r'''
// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;

public final class SheetInterpreter {
    public record NumberToken(int value, float left, float top, float right, float bottom, float annotationLeft) {}
    public record Word(String text, float left, float top, float right, float bottom) {}
    public record Annotations(List<NumberToken> measureNumbers, List<NumberToken> tempoNumbers,
            List<NumberToken> restCounts, List<Word> words, List<ScoreMeterChange> meters, List<Word> tabWords) {
        public static final Annotations EMPTY = new Annotations(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
    private static ScoreNoteEvent note(float position, int step, int clef, int boundary, int shift, int staff, int count) {
        return new ScoreNoteEvent(0, position, step, staff, count, .5f,
                false, 0, 0, ScoreNoteEvent.ACCIDENTAL_NATURAL, 1, 1, 0,
                0, clef, false, 0, false, shift, boundary, 1, 0);
    }
    public static ScorePageInterpretation analyze(byte[] labels, byte[] gray, int width, int height, Annotations annotations) {
        if (width != 8 || height != 8 || labels.length != 64 || gray.length != 64)
            throw new AssertionError("Expected the original 8x8 synthetic bridge page");
        for (byte value : gray) if (value != (byte)255) throw new AssertionError("Expected blank synthetic grayscale");
        var prefix = note(.1f, 0, ScoreNoteEvent.CLEF_TREBLE, 0, 0, 0, 1);
        var duplicate = note(.25f, 2, ScoreNoteEvent.CLEF_TREBLE, 5, 1, 0, 1);
        List<ScoreNoteEvent> notes;
        switch (labels[0]) {
            case 1 -> {
                var equal = note(.25f, 2, ScoreNoteEvent.CLEF_TREBLE, 5, 1, 0, 1);
                if (duplicate == equal || !duplicate.equals(equal)) throw new AssertionError("Need distinct equal-valued records");
                notes = List.of(prefix, duplicate, equal);
            }
            case 2 -> notes = List.of(prefix, duplicate, duplicate);
            case 3 -> {
                var values = new ArrayList<ScoreNoteEvent>();
                int[] flags = {1, 2, 4, 8, 15};
                for (int i = 0; i < flags.length; i++)
                    values.add(note(.1f + .15f * i, i, ScoreNoteEvent.CLEF_TREBLE, flags[i], i == 4 ? 1 : 0, 0, 1));
                notes = List.copyOf(values);
            }
            case 4 -> notes = List.of(prefix,
                    note(.3f, 0, ScoreNoteEvent.CLEF_UNKNOWN, 15, 0, 0, 1),
                    note(.5f, 0, ScoreNoteEvent.CLEF_UNKNOWN, 15, 0, 1, 2),
                    note(.7f, 3, ScoreNoteEvent.CLEF_BASS, 10, -1, 0, 1));
            case 5 -> notes = List.of(prefix, prefix);
            case 0 -> {
                var guessed = note(.2f, 0, ScoreNoteEvent.CLEF_UNKNOWN, 5, 0, 0, 1);
                notes = List.of(guessed, guessed);
            }
            default -> throw new AssertionError("Unknown original synthetic fixture");
        }
        return new ScorePageInterpretation(List.of(new MeasureRegion(.1f, .9f, .2f, .8f)), notes);
    }
}
'''


def jdk_tool(name):
    suffix = '.exe' if os.name == 'nt' else ''
    if os.environ.get('JAVA_HOME'):
        tool = Path(os.environ['JAVA_HOME']) / 'bin' / (name + suffix)
        if tool.is_file():
            return str(tool)
    tool = shutil.which(name)
    if not tool:
        raise RuntimeError('JDK 17 or newer required: ' + name)
    return tool


class SourceNoteIndexTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        parent = os.environ.get('SOURCE_INDEX_OUTPUT_ROOT')
        if parent:
            Path(parent).mkdir(parents=True, exist_ok=True)
        cls.folder = Path(tempfile.mkdtemp(prefix='source-note-index-', dir=parent))
        if not os.environ.get('SOURCE_INDEX_KEEP_OUTPUT'):
            cls.addClassCleanup(shutil.rmtree, cls.folder)
        elif parent:
            (Path(parent) / 'execution-directory.txt').write_text(str(cls.folder), encoding='utf-8')
        backend = cls.folder / 'SheetInterpreter.java'
        backend.write_text(BACKEND, encoding='utf-8')
        cls.classes = cls.folder / 'classes'
        cls.core = ROOT / 'build/classes'
        cls.guarded = [ROOT / 'java/src/main/java/io/github/luckolite/interpreter' / name for name in ('Main.java', 'SheetInterpreter.java')]
        cls.guarded += [cls.core / 'io/github/luckolite/interpreter' / name for name in ('Main.class', 'SheetInterpreter.class')]
        cls.hashes_before = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in cls.guarded}
        if not (cls.core / 'io/github/luckolite/interpreter/ScoreNoteEvent.class').is_file():
            raise RuntimeError('Build the actual standalone Java classes before this bridge regression')
        # Normal public mode uses the actual built Main. Only a private before/
        # after reproduction supplies an exact Main source override to compile.
        sources = ([str(MAIN)] if os.environ.get('SOURCE_INDEX_MAIN') else []) + [str(backend)]
        command = [jdk_tool('javac'), '--release', '17', '-encoding', 'UTF-8', '-implicit:none',
                   '-cp', str(cls.core), '-d', str(cls.classes), *sources]
        compiled = subprocess.run(command, capture_output=True, text=True, timeout=60)
        (cls.folder / 'compile.log').write_text(compiled.stdout + compiled.stderr, encoding='utf-8')
        if compiled.returncode:
            raise RuntimeError('Actual Main/test backend compile failed: ' + compiled.stderr)

    @classmethod
    def tearDownClass(cls):
        after = {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in cls.guarded}
        if after != cls.hashes_before:
            raise AssertionError('The isolated fixture changed real production source/classes')
        (cls.folder / 'isolation-receipt.json').write_text(json.dumps({
            'status': 'PASS_TEST_BACKEND_CHILD_PROCESS_ISOLATION',
            'fixtureChildClassPath': [str(cls.classes), str(cls.core)],
            'realBackendChildClassPath': [str(cls.core)],
            'globalTestClassPathUsed': False, 'productionSourceAndClassHashesUnchanged': after,
            'normalPublicModeCompilesOnlyFixtureBackend': not bool(os.environ.get('SOURCE_INDEX_MAIN'))
        }, indent=2) + '\n', encoding='utf-8')

    def bridge(self, fixture):
        page = self.folder / ('fixture-' + str(fixture) + '.page.gz')
        output = self.folder / ('fixture-' + str(fixture) + '.json')
        # Same original RSP1 envelope as the frontend tests; no OCR/model input.
        data = struct.pack('>III', 0x52535031, 8, 8) + bytes([fixture % 6]) + bytes(63) + bytes([255]) * 64
        page.write_bytes(gzip.compress(data, mtime=0))
        run = subprocess.run([jdk_tool('java'), '-cp', os.pathsep.join([str(self.classes), str(self.core)]),
                              PACKAGE + '.Main', str(page), str(output), '4', '4', '0'],
                             capture_output=True, text=True, timeout=30)
        self.assertEqual(0, run.returncode, run.stderr)
        self.assertEqual('', run.stdout)
        # Preserve configured JVM flags while rejecting actual CLI diagnostics.
        banners = {prefix + os.environ[name] for name, prefix in (
            ('JAVA_TOOL_OPTIONS', 'Picked up JAVA_TOOL_OPTIONS: '),
            ('JDK_JAVA_OPTIONS', 'NOTE: Picked up JDK_JAVA_OPTIONS: '),
            ('_JAVA_OPTIONS', 'Picked up _JAVA_OPTIONS: ')) if name in os.environ}
        self.assertEqual([], [line for line in run.stderr.splitlines() if line not in banners])
        return json.loads(output.read_text(encoding='utf-8'))

    def test_distinct_equal_boundary_records_keep_actual_occurrence_indices(self):
        value = self.bridge(1)
        self.assertEqual([1, 2], [event['sourceNoteIndex'] for event in value['events'] if 'sourceNoteIndex' in event])
        self.assertEqual(value['score']['notes'][1], value['score']['notes'][2])
        self.assertEqual([5, 5], [event['boundaryTies'] for event in value['events'][1:]])

    def test_same_note_object_twice_keeps_actual_occurrence_indices(self):
        value = self.bridge(2)
        self.assertEqual([1, 2], [event['sourceNoteIndex'] for event in value['events'] if 'sourceNoteIndex' in event])
        self.assertEqual(3, len(value['events']))
        self.assertNotIn('sourceNoteIndex', value['events'][0])

    def test_ordinary_distinct_records_keep_all_boundary_flags(self):
        value = self.bridge(3)
        self.assertEqual(list(range(5)), [event['sourceNoteIndex'] for event in value['events']])
        self.assertEqual([1, 2, 4, 8, 15], [event['boundaryTies'] for event in value['events']])
        self.assertEqual([False] * 5, [event['clefInferred'] for event in value['events']])
        self.assertEqual([note['clefBottomDiatonic'] + note['staffStep'] + note['octaveShift'] * 7 for note in value['score']['notes']],
                         [event['boundaryPitch'] for event in value['events']])

    def test_no_boundary_or_guessed_clef_keeps_index_and_boundary_fields_absent(self):
        events = self.bridge(4)['events']
        for event in events[:3]:
            for field in ('sourceNoteIndex', 'boundaryTies', 'boundaryPitch', 'boundaryAccidental'):
                self.assertNotIn(field, event)
        self.assertEqual([False, True, True, False], [event['clefInferred'] for event in events])
        self.assertEqual(3, events[3]['sourceNoteIndex'])
        self.assertEqual(10, events[3]['boundaryTies'])
        self.assertEqual(-1, events[3]['octaveShift'])

    def test_duplicate_notes_without_boundary_do_not_gain_source_indices(self):
        events = self.bridge(5)['events']
        self.assertEqual(2, len(events))
        self.assertTrue(all('sourceNoteIndex' not in event for event in events))
        # A fresh child without the fixture prefix still runs real analysis.
        # Its original blank page has no notes, unlike the two-note fake result.
        page = self.folder / 'real-backend.page.gz'
        output = self.folder / 'real-backend.json'
        page.write_bytes(gzip.compress(struct.pack('>III', 0x52535031, 8, 8) + bytes(64) + bytes([255]) * 64, mtime=0))
        run = subprocess.run([jdk_tool('java'), '-cp', str(self.core), PACKAGE + '.Main',
                              str(page), str(output), '4', '4', '0'], capture_output=True, text=True, timeout=30)
        self.assertEqual(0, run.returncode, run.stderr)
        actual = json.loads(output.read_text(encoding='utf-8'))
        self.assertEqual([], actual['score']['notes'])
        self.assertEqual([], actual['events'])

    def test_duplicate_guessed_clef_boundary_notes_keep_fields_absent(self):
        events = self.bridge(6)['events']
        self.assertEqual(2, len(events))
        self.assertTrue(all(event['clefInferred'] for event in events))
        self.assertTrue(all('sourceNoteIndex' not in event and 'boundaryTies' not in event for event in events))


if __name__ == '__main__':
    unittest.main()
