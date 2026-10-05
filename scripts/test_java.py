#!/usr/bin/env python3
# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Run generated-geometry regression tests, verifying downloaded test dependencies."""
import hashlib
import json
import os
import subprocess
import shutil
import urllib.request
from pathlib import Path
from build_java import ROOT, jdk_tool


def main():
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
    main()
