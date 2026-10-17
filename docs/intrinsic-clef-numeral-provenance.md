# Original clef numeral fixtures

The intrinsic-clef recognizer uses 30 small masks generated from existing,
publicly distributed SIL Open Font License fonts: Bravura time-signature
digits and Noto Serif regular/bold digits. It contains no score-image masks
or trained-model changes.

`GenerateClefNumerals.java` and `generate_clef_numerals.py` reproduce all masks
using Java AWT at 24 points, threshold 175 and nearest-neighbor 16 by 24 sampling.
The checker verifies the exact existing font hashes before rasterization.
Preserve the Bravura and Noto Serif OFL notices and their copyright attribution.
The generator and recognizer code are original Apache-2.0 code.

The portable public tool accepts a repository directory, Java home and helper
source through command-line options. It has no private path or score input.
The old private build wrapper and private investigation receipts are not public
port inputs. Font binaries are unchanged and already belong to the public tree.

Original tests place the public-font glyphs in procedural clef/header contexts:
multiple fonts and scales, attached intrinsic 15, ordinary measure-number 15,
tempo/fingering context, other digit pairs, isolated 8, tight 28, incomplete
components, cross-baseline components, noise and blocked white corridors.

## Verified generation environment

```text
java.version=21.0.10
java.runtime.version=21.0.10+-14961533-b1163.108
java.vendor=JetBrains s.r.o.
java.vm.name=OpenJDK 64-Bit Server VM
os.name=Windows 11
os.arch=amd64
headless=false
Java2DRenderingEngine=sun.java2d.marlin.DMarlinRenderingEngine
Bravura.otf scaler=sun.font.FreetypeFontScaler
NotoSerif.ttf scaler=sun.font.FreetypeFontScaler
```

The portable headless reproduction check yields exactly the initial masks:
`acf8b41ce201357d6b14b24f13ea19a93e1c343c529301d472b1fc3babe40350`.
The font source paths are `java/assets/Bravura.otf` and
`training/fonts/NotoSerif.ttf`; checksums are validated by the portable script.

Font SHA-256 values:

- Bravura: `cdf0f893ee1fdb64b7f6713d71ee0dcfc349c0ac01429a8e451b01a9e79f5f3b`
- Noto Serif: `4d8e6761424656867019081a1a01336f3cb086982682698714054fc33f782713`
