# Third-party notices

The runtime wheel and Java JAR contain our original code, our independent weights,
and the recognition-only glyph rasters described below. Other dependencies are
installed separately, not copied into the wheel or JAR. Preserve their
complete upstream notices if you redistribute them as part of a larger application.

| Component | Role | Upstream license information |
|---|---|---|
| TensorFlow 2.15.1 / LiteRT-compatible inference | Python inference | [Apache-2.0](https://github.com/tensorflow/tensorflow/blob/v2.15.1/LICENSE); its distributions carry dependency notices |
| NumPy 1.26.x | Pixel arrays | [BSD-3-Clause and bundled-component notices](https://github.com/numpy/numpy/blob/v1.26.4/LICENSE.txt); binary wheels include additional library licenses |
| Pillow | Image I/O | [MIT-CMU](https://github.com/python-pillow/Pillow/blob/main/LICENSE) for the verified 12.2.0 build, plus codec/dependency notices in binary distributions |
| pypdfium2 4.x / PDFium | Optional PDF rasterization | [Apache-2.0 OR BSD-3-Clause, plus PDFium third-party notices](https://github.com/pypdfium2-team/pypdfium2/tree/main/LICENSES) |
| imageio-ffmpeg | Optional MP3 encoder discovery and separately installed FFmpeg binary | [BSD-2-Clause wrapper](https://github.com/imageio/imageio-ffmpeg/blob/main/LICENSE); the FFmpeg executable has its own build-dependent [license and notices](https://ffmpeg.org/legal.html), not Apache-2.0 |
| RapidOCR 3.9.2 and its packaged OCR models | Offline text and fret recognition | [Apache-2.0 and model provenance](https://github.com/RapidAI/RapidOCR/tree/v3.9.2); installed separately from this wheel |
| ONNX Runtime 1.23.2 | Runs the OCR models | [MIT and bundled third-party notices](https://github.com/microsoft/onnxruntime/tree/v1.23.2); installed separately from this wheel |
| Java runtime | Runs the decoder | Supplied by the user; licensing depends on the JDK/JRE distribution |
| PyTorch 2.1.2 | Optional training | [BSD-style license and third-party notices](https://github.com/pytorch/pytorch/blob/v2.1.2/LICENSE) |
| OpenCV Python 4.10.0.84 | Optional training data and augmentation | [MIT wrapper](https://github.com/opencv/opencv-python/blob/4.x/LICENSE.txt), [Apache-2.0 OpenCV binary and bundled-component notices](https://github.com/opencv/opencv-python/blob/4.x/LICENSE-3RD-PARTY.txt) |
| Verovio 6.3.0 | Optional synthetic-score renderer | [LGPL-3.0](https://github.com/rism-digital/verovio/tree/version-6.3.0); external training tool, not a runtime dependency |
| resvg-py 0.3.0 / resvg | Optional SVG rasterizer for training | [MIT wrapper](https://github.com/baseplate-admin/resvg-py/blob/master/LICENSE), with resvg and its dependency licenses |
| JUnit 4.13.2 / Hamcrest 1.3 | Java tests only | [EPL-1.0](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt) / [BSD-3-Clause](https://github.com/hamcrest/JavaHamcrest/blob/master/LICENSE) |
| Gson 2.11.0 | Java serialization/identity regression tests only; not bundled in the interpreter | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) |

Dependency licenses are not replaced by this project's Apache license. In particular,
an upstream package's headline license is not a substitute for its complete binary
distribution notices.

## Bundled recognition glyphs

The JAR includes 27 small grayscale templates: nine dynamic symbols rendered from
Bravura, and nine ornament/auxiliary-accidental symbols rendered in both Bravura and
Leland. They are individual glyph samples, not score excerpts or font binaries.
Bravura is copyright Steinberg Media Technologies GmbH; Leland is copyright
MuseScore BVBA. Both retain SIL OFL 1.1. Their complete notices and exact per-file
SHA-256 checksums are packaged beside the templates under
`io/github/luckolite/interpreter/glyphs/` and kept in
[`java/src/main/resources/io/github/luckolite/interpreter/glyphs/`](java/src/main/resources/io/github/luckolite/interpreter/glyphs/).
These materials are not relicensed under Apache-2.0. The build verifies their
manifest and includes all resource bytes in the decoder fingerprint.

The test-only [`dynamic-original/fp.png`](java/src/test/resources/dynamic-original/fp.png) is an original compound raster generated from the same Bravura font and remains under SIL OFL 1.1. Its [fixture notes](java/src/test/resources/dynamic-original/README.md) record the generator and checksum; the complete notice is [Bravura-OFL.txt](java/assets/Bravura-OFL.txt). It is not included in the runtime glyph manifest.

The desktop adapter also renders `tr`/`tr.` in the user's JDK logical serif font
at runtime; those generated candidates are not bundled font assets.

## Training fonts

The bundled, unmodified `training/fonts/NotoSerif.ttf` is copyright 2022 The Noto
Project Authors and is distributed under SIL OFL 1.1. Its complete notice is
[NOTO-SERIF-OFL.txt](training/fonts/NOTO-SERIF-OFL.txt).

The unmodified `java/assets/Bravura.otf` is copyright Steinberg Media Technologies
GmbH and distributed under SIL OFL 1.1 for optional desktop meter matching. Its
complete notice is [Bravura-OFL.txt](java/assets/Bravura-OFL.txt). The runtime wheel
does not bundle this font. Training also uses music glyphs from the pinned Verovio
installation; their notices are [BRAVURA-OFL.txt](training/fonts/BRAVURA-OFL.txt)
and [LELAND-OFL.txt](training/fonts/LELAND-OFL.txt).

Generated score images in `examples/` are newly authored exercises. No commercial
sheet-music pages, recordings, pretrained third-party OMR models or Google OCR assets
are included.
