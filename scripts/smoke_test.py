#!/usr/bin/env python3
# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Check an original scale through recognition, CLI and available export formats."""
import json
import xml.etree.ElementTree as ET
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    expected = json.loads((ROOT / 'examples/expected.json').read_text())
    from sheet_music_recognition.audio import find_ffmpeg
    try:
        encoder = find_ffmpeg()
    except RuntimeError:
        encoder = None
    with tempfile.TemporaryDirectory(prefix='sheet-smoke-') as folder:
        for extension in ('png', 'pdf'):
            output = Path(folder) / (extension + '.json')
            midi = Path(folder) / (extension + '.mid')
            musicxml = Path(folder) / (extension + '.musicxml')
            mp3 = Path(folder) / (extension + '.mp3')
            audio_args = ['--mp3', str(mp3), '--ffmpeg', encoder] if encoder else []
            subprocess.run([sys.executable, '-m', 'sheet_music_recognition.cli', str(ROOT / ('examples/scale.' + extension)),
                            '--output', str(output), '--midi', str(midi), '--musicxml', str(musicxml), '--meter', '4/4', *audio_args], check=True)
            page = json.loads(output.read_text())['pages'][0]
            assert len(ET.parse(musicxml).findall('.//note/pitch')) == 8
            assert [n['midi'] for n in page['events']] == expected['midi'], extension
            assert [n['startBeat'] for n in page['events']] == list(range(8)), extension
            assert [n['durationBeats'] for n in page['events']] == expected['durationsQuarterBeats'], extension
            assert len(page['score']['measures']) == expected['measures'], extension
            assert midi.read_bytes().startswith(b'MThd'), extension
            if encoder:
                assert mp3.stat().st_size > 1000, extension
                subprocess.run([encoder, '-v', 'error', '-i', str(mp3), '-f', 'null', '-'],
                               check=True, timeout=30)
            assert not any(n['durationFallback'] or n['clefInferred'] for n in page['events']), extension
            print(extension.upper(), '8/8 printed pitches and durations, Java core, MIDI/MusicXML',
                  'and MP3 passed' if encoder else 'passed (MP3: no encoder installed)')


if __name__ == '__main__':
    main()
