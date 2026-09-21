#!/usr/bin/env python3
# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Check an original scale through the actual model, Java decoder, CLI and MIDI output."""
import json
import xml.etree.ElementTree as ET
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    expected = json.loads((ROOT / 'examples/expected.json').read_text())
    with tempfile.TemporaryDirectory(prefix='sheet-smoke-') as folder:
        for extension in ('png', 'pdf'):
            output = Path(folder) / (extension + '.json')
            midi = Path(folder) / (extension + '.mid')
            musicxml = Path(folder) / (extension + '.musicxml')
            subprocess.run([sys.executable, '-m', 'sheet_interpreter.cli', str(ROOT / ('examples/scale.' + extension)),
                            '--output', str(output), '--midi', str(midi), '--musicxml', str(musicxml), '--meter', '4/4'], check=True)
            page = json.loads(output.read_text())['pages'][0]
            assert len(ET.parse(musicxml).findall('.//note/pitch')) == 8
            assert [n['midi'] for n in page['events']] == expected['midi'], extension
            assert [n['startBeat'] for n in page['events']] == list(range(8)), extension
            assert [n['durationBeats'] for n in page['events']] == expected['durationsQuarterBeats'], extension
            assert len(page['score']['measures']) == expected['measures'], extension
            assert midi.read_bytes().startswith(b'MThd'), extension
            assert not any(n['durationFallback'] or n['clefInferred'] for n in page['events']), extension
            print(extension.upper(), '8/8 printed pitches and durations, Java core and MIDI passed')


if __name__ == '__main__':
    main()
