# Sheet-Music-Recognition documentation

Sheet-Music-Recognition is an experimental offline optical music recognition (OMR) toolkit for
turning sheet-music images and scanned PDFs into MusicXML, MIDI, JSON and MP3.
The Python package and primary command are named `sheet-music-recognition`.

## Start here

| Goal | Guide |
| --- | --- |
| Install and convert an image or PDF | [Quick start and CLI examples](../README.md#install-and-use) |
| Integrate recognition or export in Python / Java | [API examples and output format](integration.md) |
| Understand supported notation and export limits | [Supported features](../README.md), [model limitations](../models/MODEL_CARD.md#evaluation-and-limitations) |
| Download standalone Java and model artifacts | [GitHub releases](https://github.com/Luckolite/Sheet-Music-Recognition/releases) |
| Reuse code or weights commercially | [Licensing and redistribution](licensing.md) |
| Report a recognition problem or contribute | [Contribution guide](../CONTRIBUTING.md), [issue tracker](https://github.com/Luckolite/Sheet-Music-Recognition/issues) |

## Notation and playback

- [Guitar tablature](guitar-tabs.md), [gestures and effects](tab-gestures.md), and [ties](tab-ties.md).
- [Expressive performance policy](expressive-performance.md) and [expressive events](expressive-events.md).
- [Unpitched events and preview selection](unpitched-events.md).

## Runtime integrations

- [Native Java decoding service](native-decoder.md).
- [Portable Java OCR](portable-ocr.md) and [ONNX segmentation](portable-segmentation.md).
- [Optional Android adapters](../android-adapters/README.md).

## Models, training and evidence

- [Model card](../models/MODEL_CARD.md): inputs, outputs, training lineage and known limitations.
- [Evaluation data](../models/evaluation.json): measurement scope and coverage.
- [Training and export guide](../training/README.md).
- [Source provenance](source-provenance.md) and [third-party notices](../THIRD_PARTY_NOTICES.md).

Recognition is experimental. Review the generated notes, rhythms and symbols
against the source page before relying on an export.
