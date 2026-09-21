# Music Sheets Interpreter

Offline sheet-music recognition for images and PDFs, with downloadable model weights,
a Java decoder and a Python interface. Exports JSON, MIDI and MusicXML.

**Code and weights are Apache-2.0 licensed, including commercial and closed-source use.**
You do not need to publish your own code. Follow the [license and notice requirements](docs/licensing.md).

This is experimental software. Check recognized notes and timing against the original score.

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
Everything runs locally after installation; no account or server is required.

## What it supports

- Standard notation: pitches, accidentals, chords, rests, ties and written timing.
- Six-string guitar tabs, with fret readings supplied by your OCR engine.
- Explicit tab rhythms and common hammer-on, pull-off, slide, bend, vibrato and harmonic symbols.
- JSON for integration, MIDI for preview, and MusicXML for editing in notation software.

MusicXML reconstructs a concert-pitch score, not the original layout or tab placement.
Guitar effects are text annotations in MusicXML. Recognition can miss symbols, and
missing tab rhythm remains estimated. Graphical bend curves, tab ties and tab tuplets
are not fully supported. An OCR engine is not included.

## Integration and model

Use `Interpreter` and `write_musicxml` from Python, or `SheetInterpreter.analyze()`
from Java. See [API examples and output format](docs/integration.md).

The bundled v4 model comes from our own synthetic training lineage, without pretrained
HOMR/oemer weights or commercial score scans. Tab and export improvements do not change
the weights. See the [model card](models/MODEL_CARD.md), [evaluation](models/evaluation.json),
[training guide](training/README.md) and [source provenance](docs/source-provenance.json).

## Development

```sh
python scripts/build_java.py
python scripts/test_java.py
python -m unittest discover -s tests
python scripts/smoke_test.py
```

Bug reports and contributions are welcome. Use shareable examples and keep private logs
and commercial scores out of public posts. See [CONTRIBUTING.md](CONTRIBUTING.md).
