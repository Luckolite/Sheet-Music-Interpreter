# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Stdlib-only orchestration checks. No javac, JVM, downloads or shared build outputs."""
import contextlib, importlib.util, io, json, pathlib, sys, tempfile, threading, time, types, unittest
from unittest import mock
PUBLIC = pathlib.Path(__file__).resolve().parents[1]
RUNNER_SOURCE = PUBLIC / 'scripts/test_java.py'


def load_runner(root, source):
    # Import the real helper without leaving sys.path or sys.modules modified.
    build_spec = importlib.util.spec_from_file_location('runner_test_build_java', root / 'scripts/build_java.py')
    build = importlib.util.module_from_spec(build_spec); build_spec.loader.exec_module(build)
    spec = importlib.util.spec_from_file_location('runner_under_test', source)
    module = importlib.util.module_from_spec(spec)
    with mock.patch.dict(sys.modules, {'build_java': build}): spec.loader.exec_module(module)
    return module


class ParallelRunnerChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.runner = load_runner(PUBLIC, RUNNER_SOURCE)

    def invoke(self, names, exits=None, launch_error=False):
        with tempfile.TemporaryDirectory(prefix='runner-proof-') as temp:
            root = pathlib.Path(temp); calls = []; lock = threading.Lock(); active = [0, 0]
            def fake(command, **kwargs):
                with lock:
                    active[0] += 1; active[1] = max(active)
                    calls.append((list(command), kwargs['cwd']))
                time.sleep(.01)
                index = int(pathlib.Path(command[1][1:]).parent.name.split('-')[1])
                try:
                    if launch_error and index == 2: raise OSError('independent simulated launch failure')
                    kwargs['stdout'].write('Simulated JUnit batch %d complete\n' % index)
                    return types.SimpleNamespace(returncode=(exits or {}).get(index, 0))
                finally:
                    with lock: active[0] -= 1
            with mock.patch.object(self.runner.subprocess, 'run', side_effect=fake), contextlib.redirect_stdout(io.StringIO()):
                status = self.runner.run_parallel('not-a-real-java', 'C:/class path;C:/test.jar', names, root, 4, 512)
            results = json.loads((root / 'build/test-parallel/results.json').read_text(encoding="utf-8"))
            manifest = json.loads((root / 'build/test-parallel/manifest.json').read_text(encoding="utf-8"))
            self.assertLessEqual(active[1], 4)
            self.assertEqual(4, len(calls))
            self.assertEqual(2048, results['maximumHeapMiB'])
            self.assertEqual(sorted(names), sorted(n for b in results['batches'] for n in b['classes']))
            self.assertEqual(len(names), len(set(n for b in results['batches'] for n in b['classes'])))
            for batch in results['batches']:
                args = pathlib.Path(batch['argumentFile']).read_text(encoding="utf-8")
                self.assertIn('"-Xmx512m"', args); self.assertIn('"-XX:ActiveProcessorCount=2"', args)
                self.assertIn('"C:/class path;C:/test.jar"', args)
                self.assertTrue(pathlib.Path(batch['log']).is_file())
                for name in batch['classes']: self.assertEqual(1, args.count('"' + name + '"'))
            self.assertEqual([b['classes'] for b in manifest['batches']], [b['classes'] for b in results['batches']])
            return status, results

    def test_real_discovered_classes_each_scheduled_once(self):
        names = ['io.github.luckolite.interpreter.' + p.stem
                 for p in sorted((PUBLIC / 'java/src/test/java').rglob('*Test.java'))]
        self.assertTrue(names)
        status, result = self.invoke(names)
        self.assertEqual(0, status); self.assertEqual(len(names), result['classCount'])

    def test_failure_does_not_skip_later_batches_and_propagates(self):
        status, result = self.invoke(['Test%02d' % n for n in range(17)], {2: 3})
        self.assertEqual(1, status); self.assertEqual([0, 3, 0, 0], [b['exitCode'] for b in result['batches']])

    def test_launch_failure_is_nonzero_and_other_batches_complete(self):
        status, result = self.invoke(['Test%02d' % n for n in range(17)], launch_error=True)
        self.assertEqual(1, status); self.assertEqual([0, 127, 0, 0], [b['exitCode'] for b in result['batches']])

    def test_partition_is_deterministic_and_empty_batches_are_not_launched(self):
        names = ['A', 'B', 'C', 'D', 'E']
        self.assertEqual([['A', 'E'], ['B'], ['C'], ['D']], self.runner.parallel_batches(names, 4))
        self.assertEqual([['A']], self.runner.parallel_batches(['A'], 4))
        with self.assertRaises(ValueError): self.runner.parallel_batches(['A', 'A'], 4)

    def test_default_main_retains_original_single_jvm_arguments(self):
        with tempfile.TemporaryDirectory(prefix='runner-default-') as temp:
            root = pathlib.Path(temp); (root / 'build').mkdir(); (root / 'scripts').mkdir(); (root / 'scripts/test-dependencies.json').write_text('{}', encoding='utf-8')
            source = root / 'java/src/test/java'; source.mkdir(parents=True)
            for name in ['ATest', 'BTest', 'Helper']: (source / (name + '.java')).write_text('// synthetic orchestration only', encoding='utf-8')
            calls = []
            with mock.patch.object(self.runner, 'ROOT', root), mock.patch.object(self.runner, 'jdk_tool', side_effect=lambda n: 'SIM-' + n), \
                 mock.patch.object(self.runner.subprocess, 'run', side_effect=lambda args, **kw: calls.append((args, kw))):
                self.assertIsNone(self.runner.main([]))
            self.assertEqual(2, len(calls)); self.assertEqual('SIM-javac', calls[0][0][0]); self.assertEqual('SIM-java', calls[1][0][0])
            arguments = (root / 'build/test-runner-args.txt').read_text(encoding="utf-8")
            self.assertNotIn('-Xmx', arguments); self.assertNotIn('ActiveProcessorCount', arguments)
            self.assertEqual(1, arguments.count('interpreter.ATest')); self.assertEqual(1, arguments.count('interpreter.BTest'))
            self.assertNotIn('interpreter.Helper', arguments); self.assertTrue(calls[1][1]['check'])

if __name__ == '__main__': unittest.main()
