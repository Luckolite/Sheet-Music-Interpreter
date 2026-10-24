# Joined printed triplet numerals

`JoinedTupletNumeral` can recover a bracketed Three that crosses a separately
observed returning curve. It samples the original raster without whitening curve
pixels. A physical staff frame and the exact first/last note owners are required.
The caller retains its system, fingering, beam-break and rhythmic-group checks.
The added route is limited to real beamed single-note attack triples and does not
seed continuation from a separately proven printed beam numeral.

Complete retained core graphs, bracket arms, competing numeral classes, text and
weak continuation checks remain active. Font likelihood compares all ten digits
and bounded full-glyph extents at measured curve contacts, using the existing
0.56 score and 0.07 margin. A font score is not a claim that every glyph pixel has
an exact outline model. Physical curve hypotheses may agree on the same numeral,
glyph extent and exact note triple despite differing in incidental sample bands.
Competing complete numeral interpretations refuse. Resource exhaustion returns
unknown; it never establishes an absent or positive symbol.

## Fonts and generated examples

The first thirty 16-by-24 masks reuse `ClefOctaveNumerals` rasterizations. Ten more
use Bravura SMuFL tuplet codepoints U+E880 through U+E889; twenty use Noto Serif
synthetic italic and bold italic. The fonts are unmodified and licensed under
SIL OFL 1.1. Existing font notices are retained. Generators are Apache-2.0.

Bravura: `java/assets/Bravura.otf`, SHA-256
`cdf0f893ee1fdb64b7f6713d71ee0dcfc349c0ac01429a8e451b01a9e79f5f3b`.
Noto Serif: `training/fonts/NotoSerif.ttf`, SHA-256
`4d8e6761424656867019081a1a01336f3cb086982682698714054fc33f782713`.

`GenerateTupletDigits.java` and `GenerateItalicDigits.java` preserve the existing
24-point, threshold-175, nearest 16-by-24 rasterization. Arguments are Bravura
font, Noto font and output text file. `GenerateJoinedNumeralFixtures.java` takes
the Noto font and a new gzip output path. It creates 480 original 128-by-128
scenes from ten digits, four styles, two sizes, two crossing lifts and three
procedural curve shapes. The fixture was generated with JBR 21.0.10. It contains
original gray samples and an independently generated curve roster, not score
images, private note coordinates or commercial material.

The focused regression retains four supported curved Three contexts, all 432
rival-digit contexts, foreign core strokes, weak continuations, clipped frames,
complete memo dependencies and malformed/resource calls. It does not imply every
printed Three or every possible font/curve combination is recognized. Tests use
the saved original fixture directly and require no AWT or installed fonts.

## Optical sizes and classified rival evidence

The complete digit family contains140 masks: the original60 plus Noto Serif
at20 and28 points, all four styles and every digit. `GenerateOpticalDigitFamilies`
takes the Noto font and a new output text path and reproduces those80 additions.
Rasterization, normalization and the0.56/0.07 confidence thresholds are unchanged.

A diagonal path with a full foot can borrow its foot from the measured curve.
When the complete font family is uncertain, that candidate remains unsupported
and unclassified. It cannot supply a positive Three or a definite conflicting Two.
A confidently classified competing digit, or a complete geometric Two foot made
entirely of independent raw core, still conflicts. Typed class evidence preserves
this distinction across the whole curve family. Every original graph, weak-ink,
bracket, owner, foreign-branch and complete-rival gate remains active.

The caller allows at most8million charged operations for this bounded curve
fallback, so the larger balanced family can finish. All other resource caps are
retained; exhaustion still returns unknown. The regression keeps the original
four supported Three scenes and432 rival scenes, and adds2880 original contexts
at independent18/22/26/30-point sizes with widths1/3 and three contact offsets.
`GenerateCertaintyGrid` takes the Noto font and a new gzip output path. All2592
rival-digit contexts remain conservative;19 complete original rivals retain
their actual non-Three classes. These are generated examples, not a claim of
complete font recognition or commercial-score accuracy.
