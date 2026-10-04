# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original fake ORT fault controls; no runtime, trained models or source images."""
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('lifecycle_build_java', ROOT / 'scripts/build_java.py')
build_java = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build_java)


def fault_instrumented_copy(raw):
    """Replace only verification IO and dictionary IO in an isolated test copy."""
    text = raw.decode('utf-8')
    verifier = re.search(r'    private static void verify\(.*?(?=    public List<String> dictionary)', text, re.S)
    if verifier is None:
        raise AssertionError('Checksum verifier boundary must be unique')
    original_verify = verifier.group(0)
    if 'Unrecognized OCR artifact checksum' not in original_verify:
        raise AssertionError('Only the known verifier body may be replaced')
    verify_hook = ('    private static void verify(String path, java.util.Set<String> allowed) throws Exception {\n'
                   '        ai.onnxruntime.FakeOrt.verify(path, allowed);\n'
                   '    }\n\n')
    dictionary = re.compile(
        r'java\.nio\.file\.Files\.readAllLines\(\s*'
        r'new java\.io\.File\(recognizerPath\s*\+\s*"\.dictionary"\)\.toPath\(\),\s*'
        r'java\.nio\.charset\.StandardCharsets\.UTF_8\)')
    matches = list(dictionary.finditer(text))
    if len(matches) != 1:
        raise AssertionError('Only one dictionary IO expression may be replaced')
    original_dictionary = matches[0].group(0)
    dictionary_hook = ('ai.onnxruntime.FakeOrt.readDictionary(recognizerPath + ".dictionary", '
                       'java.nio.charset.StandardCharsets.UTF_8)')
    instrumented = text.replace(original_verify, verify_hook, 1).replace(original_dictionary, dictionary_hook, 1)
    restored = instrumented.replace(verify_hook, original_verify, 1).replace(dictionary_hook, original_dictionary, 1)
    if restored.encode('utf-8') != raw:
        raise AssertionError('Reversing the two test-only IO hooks must restore exact source bytes')
    return instrumented.encode('utf-8')


class OnnxOcrLifecycleTest(unittest.TestCase):
    def test_constructor_and_close_preserve_ownership_and_primary_failure(self):
        binding = ROOT / 'optional/ocr/java/io/github/luckolite/interpreter/OnnxOcrInference.java'
        raw = binding.read_bytes()
        instrumented = fault_instrumented_copy(raw)
        fixtures = sorted((ROOT / 'tests/fixtures/ocr_lifecycle/java').rglob('*.java'))
        self.assertEqual(7, len(fixtures))
        with tempfile.TemporaryDirectory(prefix='ocr lifecycle ') as folder:
            work = Path(folder)
            source = work / 'source/io/github/luckolite/interpreter/OnnxOcrInference.java'
            source.parent.mkdir(parents=True)
            source.write_bytes(instrumented)
            classes = work / 'classes'
            classes.mkdir()
            arguments = ['--release', '17', '-encoding', 'UTF-8', '-d', str(classes),
                         str(source), *map(str, fixtures)]
            argument_file = work / 'javac arguments.txt'
            argument_file.write_text('\n'.join(json.dumps(arg.replace('\\', '/')) for arg in arguments) + '\n', encoding='utf-8')
            compiled = subprocess.run([build_java.jdk_tool('javac'), '-J-XX:ActiveProcessorCount=1',
                                       '-J-Xmx128m', '@' + str(argument_file)],
                                      text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
            self.assertEqual(0, compiled.returncode, compiled.stdout)
            result_file = work / 'result.json'
            executed = subprocess.run([build_java.jdk_tool('java'), '-XX:ActiveProcessorCount=1', '-Xmx96m',
                                       '-cp', str(classes), 'io.github.luckolite.interpreter.LifecycleFaultControls',
                                       'repaired', str(result_file)],
                                      text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
            self.assertEqual(0, executed.returncode, executed.stdout)
            result = json.loads(result_file.read_text(encoding='utf-8'))
            self.assertEqual('PASS', result['status'])
            self.assertEqual(29, result['criteria'])
            self.assertEqual(29, result['criterionPasses'])
            self.assertEqual(0, result['criterionFailures'])
            self.assertTrue(result['allExpected'])
            for row in result['rows']:
                with self.subTest(case=row['name']):
                    self.assertEqual('PASS', row['criteria'], row)
                    self.assertTrue(row['expectedResult'], row)
        self.assertEqual(raw, binding.read_bytes(), 'Fault hooks must never alter the optional binding')


if __name__ == '__main__':
    unittest.main()
