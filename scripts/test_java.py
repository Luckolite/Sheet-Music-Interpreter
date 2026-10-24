#!/usr/bin/env python3
# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Run generated-geometry regression tests, verifying downloaded test dependencies."""
import argparse
import concurrent.futures
import hashlib
import json
import os
import subprocess
import shutil
import urllib.request
import time
from pathlib import Path
from build_java import ROOT, jdk_tool


def parallel_batches(class_names, jobs):
    """Stable round-robin distribution; never deduplicate or omit a class."""
    if not 1 <= jobs <= 4:
        raise ValueError('jobs must be between 1 and 4')
    if len(class_names) != len(set(class_names)):
        raise ValueError('Duplicate test class names')
    batches = [class_names[index::jobs] for index in range(jobs)]
    return [batch for batch in batches if batch]


def run_parallel(java, class_path, class_names, root, jobs, heap_mb):
    batches = parallel_batches(class_names, jobs)
    if not batches:
        raise ValueError('No test classes found')
    run_root = root / 'build/test-parallel'
    run_root.mkdir(parents=True, exist_ok=True)
    plans = []
    for index, names in enumerate(batches, 1):
        batch_dir = run_root / ('batch-%02d' % index)
        batch_dir.mkdir(exist_ok=True)
        temporary = batch_dir / 'tmp'
        temporary.mkdir(exist_ok=True)
        arguments = ['-Xmx%dm' % heap_mb, '-XX:ActiveProcessorCount=2',
                     '-Djava.io.tmpdir=' + str(temporary.resolve()), '-cp', class_path,
                     'org.junit.runner.JUnitCore', *names]
        argument_file = batch_dir / 'args.txt'
        argument_file.write_text(
            '\n'.join('"' + argument.replace('\\', '\\\\').replace('"', '\\"') + '"'
                      for argument in arguments), encoding='utf-8')
        plans.append({'batch': index, 'classes': names, 'argumentFile': str(argument_file),
                      'log': str(batch_dir / 'junit.log')})
    # Save planned coverage before any JVM starts, including failed-launch cases.
    manifest = {'jobs': jobs, 'heapMiBPerJvm': heap_mb,
                'maximumHeapMiB': jobs * heap_mb, 'classCount': len(class_names),
                'batches': plans}
    (run_root / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')

    def execute(plan):
        start = time.monotonic()
        with Path(plan['log']).open('w', encoding='utf-8') as log:
            try:
                process = subprocess.run([java, '@' + plan['argumentFile']], cwd=root,
                                         stdin=subprocess.DEVNULL, stdout=log,
                                         stderr=subprocess.STDOUT, check=False)
                code = process.returncode
            except OSError as error:
                log.write('JVM launch failed: ' + str(error) + '\n')
                code = 127
        return {**plan, 'exitCode': code, 'seconds': round(time.monotonic() - start, 3)}

    # Finish every batch after failures: every discovered class remains scheduled once.
    with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as executor:
        results = list(executor.map(execute, plans))
    status = 0 if all(result['exitCode'] == 0 for result in results) else 1
    (run_root / 'results.json').write_text(
        json.dumps({**manifest, 'exitCode': status, 'batches': results}, indent=2) + '\n',
        encoding='utf-8')
    for result in results:
        print('Java batch %d: %d classes, exit %d; %s' %
              (result['batch'], len(result['classes']), result['exitCode'], result['log']))
        if result['exitCode']:
            # CI otherwise reports only the batch exit, losing the JUnit failure
            # once its ephemeral runner is gone. Keep the diagnostic bounded.
            tail = '\n'.join(Path(result['log']).read_text(encoding='utf-8', errors='replace').splitlines()[-80:])
            print('Failed Java batch %d JUnit tail:\n%s' % (result['batch'], tail[-12000:]))
    return status


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jobs', type=int, choices=range(1, 5), default=1,
                        help='isolated JVM batches (default: unchanged serial runner)')
    parser.add_argument('--heap-mb', type=int, choices=(256, 384, 512, 768, 1024), default=512,
                        help='maximum heap per parallel JVM in MiB (default: 512)')
    options = parser.parse_args(argv)
    dependencies = json.loads((ROOT / 'scripts/test-dependencies.json').read_text())
    jars = []
    for name, info in dependencies.items():
        path = ROOT / 'build/test-deps' / (name + '.jar')
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.exists():
            path.write_bytes(urllib.request.urlopen(info['url'], timeout=60).read())
        if hashlib.sha256(path.read_bytes()).hexdigest() != info['sha256']:
            raise SystemExit('Test dependency checksum mismatch: ' + name)
        jars.append(path)
    sources = sorted((ROOT / 'java/src/test/java').rglob('*.java'))
    tests = [source for source in sources if source.name.endswith('Test.java')]
    classes = ROOT / 'build/test-classes'
    # Old implicit production classes can shadow the newly built core because
    # the runner places test output first. Always compile into a fresh directory.
    if classes.exists():
        if classes.resolve().parent != (ROOT / 'build').resolve():
            raise SystemExit('Refusing to clear test classes outside the build directory')
        shutil.rmtree(classes)
    classes.mkdir(exist_ok=True)
    resources = ROOT / 'java/src/test/resources'
    if resources.is_dir():
        shutil.copytree(resources, classes, dirs_exist_ok=True)
    cp = os.pathsep.join(map(str, [ROOT / 'build/classes', *jars]))
    source_list = ROOT / 'build/test-sources.txt'
    source_list.write_text('\n'.join('"' + p.relative_to(ROOT).as_posix() + '"' for p in sources),
                           encoding='utf-8')
    subprocess.run([jdk_tool('javac'), '--release', '17', '-encoding', 'UTF-8', '-cp', cp,
                    '-d', str(classes), '@' + str(source_list)], cwd=ROOT, check=True)
    if options.jobs > 1:
        return run_parallel(jdk_tool('java'), os.pathsep.join([str(classes), cp]),
                            ['io.github.luckolite.interpreter.' + p.stem for p in tests],
                            ROOT, options.jobs, options.heap_mb)
    # The growing regression suite exceeds Windows' command-line limit. Java 17
    # reads the same arguments from a file without splitting or omitting tests.
    arguments = ['-cp', os.pathsep.join([str(classes), cp]),
                 'org.junit.runner.JUnitCore',
                 *['io.github.luckolite.interpreter.' + p.stem for p in tests]]
    runner_list = ROOT / 'build/test-runner-args.txt'
    runner_list.write_text('\n'.join('"' + argument.replace('\\', '\\\\').replace('"', '\\"') + '"'
                                    for argument in arguments), encoding='utf-8')
    subprocess.run([jdk_tool('java'), '@' + str(runner_list)], check=True)


if __name__ == '__main__':
    raise SystemExit(main())
