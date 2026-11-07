# Music Sheets independent v4 float16

**License:** Apache-2.0, including the weights. **Status:** experimental.

| Property | Value |
|---|---|
| Architecture | `musicsheets-wide-context-v2-experiment`, six-class U-net |
| Checkpoint | Candidate24, step 8,000 selected after 10,000 updates |
| Parameters | 1,946,503 including the auxiliary centre head |
| Feature widths | 32, 64, 128, 256; bottleneck dilations 2 and 4 |
| Default file | `music_sheets_v4_float16.tflite` |
| File size | 3,907,460 bytes |
| SHA-256 | `92ab7c34c27cb704d95f0cde247b3611d1563fdf1c114b9f9817abb18a7b6a93` |
| Input | float32 `[1,3,320,320]`, raw grayscale 0..255 replicated into three channels |
| Output | int64 `[1,320,320]`, class IDs 0..5 |
| Page preprocessing | Width 2048, preserve aspect ratio, grayscale on white |
| Tiling | 320-pixel tiles, stride 192, tail anchored to the image edge |
| Merge | Largest distance from a tile edge wins; later tiles win equal distances |
| Default execution | CPU, two threads |

Class IDs: **0** background, **1** stems/rests/barlines, **2** noteheads,
**3** clefs/keys/accidentals, **4** staff, **5** other symbols.

Float16 describes stored parameters; input remains float32 and output int64.
Normalization is inside the network. The output is a segmentation map. The Java
decoder combines it with the original grayscale page to estimate pitches and timing.
The checkpoint retains an auxiliary note-centre head. It is excluded from the default
TFLite model and is not a reliable substitute for the decoder's note checks.

## Training and reuse

The model continues our own randomly initialized v1–v3 lineage through recorded
development checkpoints. No external pretrained OMR weights, distillation targets,
downloaded score datasets, commercial sheets or phone images were used for training.
Ten original generated corpora provide notation examples; procedural augmentations
add appearance variation and nearby marks. Training used an RTX 2080 SUPER.

All 32 exported calibration inputs were matched to original synthetic training-page
crops. `weights.npz` is losslessly compressed; every array matches the original
export. `best.pt` contains the identical selected model tensors with private paths,
optimizer and scaler state removed. See [lineage.json](lineage.json),
[artifacts.json](artifacts.json) and [training.json](training.json).

The [training guide](../training/README.md) covers loading, fine-tuning and export.
These tools support further training; they do not reproduce every historical corpus
generation and optimizer update. Bit-for-bit retraining is not claimed. The earlier
v3 TFLite remains in `models/`; release v0.1.0 retains its checkpoint and package.

## Evaluation and limitations

Candidate24 improved clean and broad development segmentation scores over candidate23.
Dim/paper appearance mean IoU declined from 0.73073 to 0.71354, and the ledger stratum
declined slightly. These are repeated development measurements, not independent
full-page accuracy. The new raw-model held-out split remains unevaluated.

The release also uses manually reviewed pitch anchors on real scores. Coverage ranges
from short passages to full pages; it does not establish whole-library accuracy or
cover all extra notes, rhythms and repeats. Counts and scopes are in
[evaluation.json](evaluation.json). Private score images and coordinates are not distributed.

Errors remain in accidentals, hollow heads, small notation, complex polyphony, faint
scans, ties and timing. The interpreter can still omit or invent notes. MIDI is a
preview; check results against the printed page. No general accuracy percentage or
state-of-the-art claim is made.

The code and weights may be modified and used in commercial, closed-source products
under Apache-2.0. Preserve the license and applicable notices when redistributing.
Fonts and external tools retain the licenses in
[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

## Paper-field decoder correction v1

`paper_field_v1.onnx` and its original checkpoint `paper_field_v1.pt` are Apache-2.0.
This independently initialized ExtremaField model estimates smooth paper brightness
and print contrast. It does not replace either segmentation model. Segmentation,
tab preparation, source annotations and source clock evidence keep the original
raster; only the geometric decoder receives the corrected grayscale. Bright clean
pages bypass inference and retain identical bytes. Recognition revision140 separates
new caches from prior outputs; guide299 and audio102 layouts are unchanged.

Training used original procedural renderer train splits, seed11072026,1600 updates,
batch8 and AdamW0.001. Four paired augmentations vary fold shadows, gradients,
stains and texture without moving symbols. No commercial score pixels were used.
Architecture, exact augmentation, export recipe, corpus manifest checksums, loss,
weight checksums and synthetic evaluation are in `training/paper_field.py`,
`training/export_paper_field.py` and [paper-field-lineage.json](paper-field-lineage.json).
Export with PyTorch2.1.2 and ONNX1.17.0; exact historical retraining is not claimed.

On16 untouched synthetic pages, paired pitch+duration matches improved2294→2367
of2546; onset+pitch+duration improved1345→1574. All16 clean controls remained
pixel-identical and10 blank/empty-staff controls yielded no notes. Strict unmatched
counts rose40→42; a wider positional window reduced3→1, showing positional
matching sensitivity. Real photographed/scanned score accuracy remains unverified.
