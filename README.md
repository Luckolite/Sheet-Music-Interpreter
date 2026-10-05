# Music Sheets Interpreter

Offline sheet-music recognition for images and PDFs, with downloadable model weights,
a Java decoder and a Python interface. Exports JSON, MIDI, MP3 and MusicXML.

**Code and weights are Apache-2.0 licensed, including commercial and closed-source use.**
You do not need to publish your own code. Follow the [license and notice requirements](docs/licensing.md).

## What it supports 🎹🎸🎻🎷

- Standard notation: pitches, accidentals, chords, rests, ties, tempo and key changes.

- Multi-staff piano, 6- and 7 string guitar tabs, violin and ensemble pages, including independently barred staves.

- Note-equals-number tempo marks and note-equals-note metric modulations, including dotted pulses.

- Expressive previews: rit./rall. slowing, rite/ritenuto, fermata holds, breath pauses and sf/sfz/sfp attacks. See [the shared playback policy](docs/expressive-performance.md) for timing defaults and evidence requirements.
- Continuous pedal brackets with two upward hooks retain written columns. Complete, part-owned pairs supply ordered MIDI sustain controls, finite audio release gates and MusicXML bracket endpoints.

- Tuning headers, detached tab stems, partial beams, rests, dots, triplets, grace frets, tied continuations, hammer-on, pull-off, tapping, slide, bend, vibrato, and harmonic symbols. Missing tab rhythm is estimated. Graphical bends, quarter-tone bends, whammy-bar directions and strum direction aren't supported.

- JSON for integration, MIDI/MP3 for preview, and MusicXML for editing in notation software.
- Explicit unpitched events and caller-selected previews: [API examples](docs/unpitched-events.md).

Explicit tuplets expose `tupletActualNotes` and `tupletNormalNotes` on derived JSON
events and retain both counts in the raw score notes. A proved wavy connector
adds `glissando: {"style": "white_keys", "targetMidi": ...}` to its source event.
Preview playback holds that source for two thirds of its duration, then plays
the intervening white keys; the target retains its ordinary pitch and onset.
MusicXML exports paired wavy endpoints and explicit tuplet time modifications.

- MusicXML reconstructs a concert-pitch score, not the original layout or tab placement.
  Guitar effects are text annotations in MusicXML.

## Install and use

Requires **Python 3.10 or 3.11** and **JDK 17+**. Build from source for the latest features;
[release downloads](https://github.com/Luckolite/Music-Sheets-Interpreter/releases) may be older.

```sh
git clone https://github.com/Luckolite/Music-Sheets-Interpreter.git
cd Music-Sheets-Interpreter
python -m venv .venv
```

Activate with `.venv\Scripts\Activate.ps1` on Windows or `source .venv/bin/activate`
on macOS/Linux, then run:

```sh
python scripts/build_java.py
python -m pip install ".[inference,pdf]"
sheet-interpreter score.pdf --output score.json --midi preview.mid --musicxml score.musicxml
```

Images work too. `--midi` and `--musicxml` are optional. Use `--meter 3/4`, `--bpm 90`,
`--key-fifths 2` or `--pages 1,2,3` when needed. Default meter and tempo are 4/4 and 120 BPM.

Detected tempo changes report `bpm` in quarter notes per minute, including fractional values,
and `beatUnit` as the printed pulse length in quarter notes. For example, eighth note = 163
reports `bpm: 81.5, beatUnit: 0.5`. MIDI and MusicXML use the quarter-note tempo directly.
Everything runs locally after installation; no account or server is required.

### MP3 audio previews

To export it, install FFmpeg on PATH or the optional bundled encoder:

```sh
python -m pip install ".[audio]"
sheet-interpreter score.pdf --output score.json --mp3 preview.mp3
```

You can export MIDI and MP3 together. `--mp3-bitrate 192` sets the MP3 bitrate in
kbps (default 192); `--ffmpeg /path/to/ffmpeg` selects an encoder explicitly.
MP3 uses a basic built-in synthesized tone, not a realistic instrument soundfont.
It shares MIDI's tempo changes, ties, tremolo, supported guitar effects and repeat
navigation, including MIDI's current expressive limitations. Rendering is streamed
in bounded blocks and limited to one hour. No soundfont, inference rerun or network
is needed to export an already decoded document through the Python API.
FFmpeg is a separate optional dependency with its own license; it is not committed
or bundled in this repository's Apache-2.0 source/model artifacts.

## Integration and model

Use `Interpreter` and `write_musicxml` from Python, or `SheetInterpreter.analyze()`
from Java. See [API examples and output format](docs/integration.md).
An optional [native Java decoding service](docs/native-decoder.md) supports
bounded, source-matched geometry and analysis requests from local workers.
The Python reader runs bundled-model OCR automatically on images and scanned PDFs.
The separate [shared Java OCR pipeline](docs/portable-ocr.md) and optional ONNX
binding remain available for cross-platform evaluation. The optional
[desktop ONNX segmentation adapter](docs/portable-segmentation.md) uses the pinned
v3 conversion without changing the default Python model.

The bundled v4 model comes from our own synthetic training lineage, without pretrained
weights or commercial score scans. See the [model card](models/MODEL_CARD.md), [evaluation](models/evaluation.json),
[training guide](training/README.md) and [source origins](docs/source-provenance.md).

## Development

```sh
python scripts/build_java.py
python scripts/test_java.py
python -m unittest discover -s tests
python scripts/smoke_test.py
```

Bug reports and contributions are welcome. Use shareable examples and keep private logs
and commercial scores out of public posts. See [CONTRIBUTING.md](CONTRIBUTING.md).
