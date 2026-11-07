#!/usr/bin/env python3
# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Build the standalone Java 17 core with the JDK only; no Gradle, Maven or Android SDK."""
import os
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def resource_snapshots(root=ROOT):
    """Verify every bundled glyph and notice before hashing and packaging exact bytes."""
    resources = root / 'java/src/main/resources'
    glyphs = resources / 'io/github/luckolite/interpreter/glyphs'
    manifest = json.loads((glyphs / 'manifest.json').read_text(encoding='utf-8'))
    listed = {row['path']: row for row in manifest['files']}
    snapshots = [(path, path.read_bytes()) for path in sorted(resources.rglob('*')) if path.is_file()]
    actual = {path.relative_to(glyphs).as_posix(): data for path, data in snapshots
              if path.is_relative_to(glyphs) and path.name != 'manifest.json'}
    if set(actual) != set(listed) or len(listed) != len(manifest['files']):
        raise ValueError('Glyph manifest must account for every bundled template and notice')
    for name, data in actual.items():
        if len(data) != listed[name]['bytes'] or hashlib.sha256(data).hexdigest() != listed[name]['sha256']:
            raise ValueError('Bundled glyph checksum mismatch: ' + name)
    return snapshots


def jdk_tool(name):
    suffix = '.exe' if os.name == 'nt' else ''
    if os.environ.get('JAVA_HOME'):
        path = Path(os.environ['JAVA_HOME']) / 'bin' / (name + suffix)
        if path.is_file():
            return str(path)
    found = shutil.which(name)
    if not found:
        raise SystemExit('Install JDK 17 or newer and set JAVA_HOME (missing ' + name + ')')
    return found


def compile_sources(sources, classes, argument_file):
    """Keep the javac command bounded even in long Windows checkout paths."""
    arguments = ['--release', '17', '-encoding', 'UTF-8', '-d', str(classes),
                 *map(str, sources)]
    # javac argument files use their own quoting rules, not shell quoting.
    argument_file.write_text('\n'.join('"' + argument.replace('\\', '\\\\')
                                      .replace('"', '\\"') + '"'
                                      for argument in arguments), encoding='utf-8')
    subprocess.run([jdk_tool('javac'), '@' + str(argument_file)], check=True)


def main():
    sources = sorted((ROOT / 'java/src/main/java').rglob('*.java'))
    classes = ROOT / 'build/classes'
    classes.mkdir(parents=True, exist_ok=True)
    digest=hashlib.sha256()
    snapshots=[(source,source.read_bytes().replace(b'\r\n',b'\n')) for source in sources]
    for source,data in snapshots:
        digest.update(source.name.encode()+b'\0'+data+b'\0')
    resources = resource_snapshots()
    for source,data in resources:
        digest.update(source.relative_to(ROOT/'java/src/main/resources').as_posix().encode()+b'\0'+data+b'\0')
    # Compile exactly the bytes fingerprinted, even if live sources change mid-build.
    generated=ROOT/'build/source-snapshots'/digest.hexdigest()
    compiled=[]
    for source,data in snapshots:
        target=generated/source.relative_to(ROOT/'java/src/main/java')
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(data)
        compiled.append(target)
    resource=classes/'io/github/luckolite/interpreter/native-decoder-sha256.txt'
    resource.parent.mkdir(parents=True,exist_ok=True)
    resource.write_text(digest.hexdigest(),encoding='utf-8')
    for source,data in resources:
        destination=classes/source.relative_to(ROOT/'java/src/main/resources')
        destination.parent.mkdir(parents=True,exist_ok=True)
        destination.write_bytes(data)
    compile_sources(compiled, classes, ROOT / 'build/javac-args.txt')
    target = ROOT / 'src/sheet_music_recognition/interpreter.jar'
    subprocess.run([jdk_tool('jar'), '--create', '--file', str(target), '--main-class',
                    'io.github.luckolite.interpreter.Main', '-C', str(classes), '.',
                    '-C', str(ROOT), 'LICENSE', '-C', str(ROOT), 'NOTICE'], check=True)
    data = ROOT / 'src/sheet_music_recognition/data'
    data.mkdir(exist_ok=True)
    for source in (ROOT / 'models/music_sheets_v4_float16.tflite', ROOT / 'models/paper_field_v1.onnx', ROOT / 'LICENSE', ROOT / 'NOTICE'):
        shutil.copyfile(source, data / source.name)
    print(target)


if __name__ == '__main__':
    main()
