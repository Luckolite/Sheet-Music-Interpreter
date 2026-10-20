# Half-rest optical fixtures

Copyright 2026 Luckolite. SPDX-License-Identifier: Apache-2.0.

These independently authored fixtures contain five complete 2-pixel staff rules, a standing rectangular plate, and isolated geometric adverse shapes. No score scan, commercial font, OCR, model output, or captured page pixel is used.

Run with Python 3.10 or later, standard library only:

```sh
python generate-half-rest-fixtures.py fresh-output
```

The output directory must not exist. Copy the two generated resource directories into the Java test resources root. Each contains row-major unsigned 8-bit 480 by 220 grayscale rasters and a geometry manifest. Compare raster SHA-256 values with fixture-provenance.json. Manifest formatting is documentary; raster bytes are exact.

Rendering uses 4x sample-centre coverage followed by a fixed horizontal [1,2,1]/4 and vertical [1,6,15,20,15,6,1]/64 optical convolution. Geometry is defined in the generator: gaps 14, 16 and 20; phases 0 and 0.5; plate width round(1.15*gap), height 0.5*gap, ending at the middle rule centre. The acquired-blur distant rectangle ends one pixel before that centre; the early-remote rectangle ends two pixels before it. It changes global row-mask pressure without contacting the local plate or side probes.

The four test classes contribute 50 checks per API: 22 optical preservation checks, 4 stable merge contracts, 16 early-remote checks, and 8 explicit supported-core entry checks. Three phase-zero acquired-direct early-remote positives are demonstrated old-API failures repaired by the new entry. All eight early-remote full-detector checks already passed the baseline. The explicit-core checks exercise a new method; the merge checks require completed records and dot identities to survive additive recovery.

Scope limits: the widened branch direct fixtures exceed the inherited width limit, so they demonstrate width refusal, not in-domain branch discrimination. Interrupted supporting-line geometry deliberately expects direct refusal but whole-detector ordinary-rest retention. Existing tests are reused separately; these resources do not replace or weaken them.
