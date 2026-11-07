# Sheet-Music-Recognition — Optical Music Recognition

Convert sheet-music images and scanned PDFs to **MusicXML, MIDI, JSON and MP3**
with offline optical music recognition (OMR). Sheet-Music-Recognition includes downloadable
model weights, a Java decoder and a Python API and command-line tool, available
as **`sheet-music-recognition`** (command) and **`sheet_music_recognition`** (Python import).

[![Build and test](https://github.com/Luckolite/Sheet-Music-Recognition/actions/workflows/ci.yml/badge.svg)](https://github.com/Luckolite/Sheet-Music-Recognition/actions/workflows/ci.yml)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue)](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/LICENSE)

[Quick start](#install-and-use) · [Documentation](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/README.md) · [Python / Java API](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/integration.md) · [Model and limitations](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/models/MODEL_CARD.md) · [Downloads](https://github.com/Luckolite/Sheet-Music-Recognition/releases)

**Code and weights are Apache-2.0 licensed, including commercial and closed-source use.**
You do not need to publish your own code. Follow the [license and notice requirements](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/licensing.md).

**Experimental:** recognition can miss or add notes and misread rhythms or
symbols. Check exports against the printed score. See the
[model evaluation and limitations](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/models/MODEL_CARD.md#evaluation-and-limitations).

## What it supports 🎹🎸🎻🎷

- Piano, Violin, Guitar 6- and 7 string tabs, ensemble pages, and independently barred staves.

- Standard notation: pitches, accidentals, chords, rests, ties, repeats and endings, tempo and key changes.

- Note-equals-number tempo marks, and note-equals-note metric modulations, including dotted pulses.

- Expressive previews: rit./rall. slowing, rite/ritenuto, fermata holds, breath pauses, sf/sfz/sfp attacks and paired tremolos. See [the shared playback policy](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/expressive-performance.md).
- Piano pedal brackets carry through to MIDI sustain, audio previews and MusicXML.

- Tuning headers, detached tab stems, partial beams, rests, dots, triplets, grace frets, tied continuations, hammer-on, pull-off, tapping, slide, bend, vibrato, and harmonic symbols. Missing tab rhythm is estimated. Labelled graphical bend arrows include quarter-tone amounts and return strokes; explicit whammy-bar arrows retain their pitch direction, and up/down strum marks order chord attacks. See [gesture evidence and limits](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/tab-gestures.md).

- JSON for integration, MIDI/MP3 for preview, and MusicXML for editing with retained clefs, slurs, articulations, ornaments and grace notes.
- Staff-owned arco, pizzicato and expression labels in MusicXML follow the first proved affected note's exported clock.
- Absolute dynamics at retained note columns use their proved written onsets in MusicXML, including staff ownership and edited timing.
- Explicit unpitched events and caller-selected previews: [API examples](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/unpitched-events.md).

- MusicXML reconstructs a concert-pitch score, not the original layout or tab placement.
  Guitar effects are text annotations in MusicXML.

## Install and use

Requires **Python 3.10 or 3.11** and **JDK 17+** on your PATH.

```sh
git clone https://github.com/Luckolite/Sheet-Music-Recognition.git
cd Sheet-Music-Recognition
python -m venv .venv
```

PyPI publication under the new name is pending. Install from source for now.
Activate with `.venv\Scripts\Activate.ps1` on Windows or
`source .venv/bin/activate` on macOS/Linux, then build the Java core and install
with PDF input and MP3 export support:

```sh
python scripts/build_java.py
python -m pip install ".[pdf,audio]"
```

### Read a score

```sh
sheet-music-recognition score.png --output score.json --midi preview.mid --musicxml score.musicxml
```

Use an image or PDF as input. JSON output is required; MIDI, MusicXML and MP3
exports are optional. For example, with the extras installed:

```sh
sheet-music-recognition score.pdf --output score.json --mp3 preview.mp3
```

MP3 previews use a basic synthesized tone. The audio extra supplies an FFmpeg
encoder. You can also use an existing FFmpeg installation.

Use `--pages 1,2,3` to select PDF pages, `--meter 3/4` for the initial meter,
`--bpm 90` for the initial quarter-note tempo, or `--key-fifths 2` for two sharps.
The default meter and tempo are 4/4 and 120 BPM. Run `sheet-music-recognition --help`
for all options.

Processing runs locally. See [API and output details](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/integration.md) for
Python/Java integration and export behavior, or [GitHub releases](https://github.com/Luckolite/Sheet-Music-Recognition/releases)
for standalone JAR and model downloads.

## Integration and model

Use `Interpreter` and `write_musicxml` from Python, or `SheetInterpreter.analyze()`
from Java. See [API examples and output format](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/integration.md).
An optional [native Java decoding service](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/native-decoder.md) supports
bounded, source-matched geometry and analysis requests from local workers.
The Python reader runs bundled-model OCR automatically on images and scanned PDFs.
The separate [shared Java OCR pipeline](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/portable-ocr.md) and optional ONNX
binding remain available for cross-platform evaluation. The optional
[desktop ONNX segmentation adapter](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/portable-segmentation.md) uses the pinned
v3 conversion without changing the default Python model.

The bundled v4 model comes from our own synthetic training lineage, without pretrained
weights or commercial score scans. See the [model card](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/models/MODEL_CARD.md), [evaluation](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/models/evaluation.json),
[training guide](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/training/README.md) and [source origins](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/docs/source-provenance.md).

## Development

Follow the source installation steps above, then run the checks:

```sh
python scripts/test_java.py
python -m unittest discover -s tests
python scripts/smoke_test.py
```

Use `python scripts/test_java.py --jobs 4` in place of the serial Java test
command to run every class once across four isolated JVMs. Each parallel JVM
defaults to a 512 MiB heap; per-batch logs and results are saved under
`build/test-parallel`. A failed batch makes the command fail.

Bug reports and contributions are welcome. Use shareable examples and keep private logs
and commercial scores out of public posts. See [CONTRIBUTING.md](https://github.com/Luckolite/Sheet-Music-Recognition/blob/main/CONTRIBUTING.md).

### Pickup caller integration regression

After building the Java core, run the original numeric/content cases against the
actual standalone pickup call:

```sh
python scripts/check_pickup_ingress.py --public . --classpath src/sheet_music_recognition/interpreter.jar --out build/pickup-ingress
```

Use a new output directory on each run. Maintainers can add `--app <app-worktree>`
and use its matching core classpath to check the exact Android and native caller
expressions too. This compiles source slices with current helper sources; it does
not run Android, image recognition or a packaged worker. The fixtures contain no
score images or private data.
