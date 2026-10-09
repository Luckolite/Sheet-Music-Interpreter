# Guitar tab interpretation

Triplet counts apply to three rhythmic onset columns. A chord or muted strum
occupies one column; a written rest occupies its own column. Grace notes do not
consume the count. Literal rest durations combine augmentation dots and the same
supported tuplet ratios as notes, preserving later attack times.

H and T in an established Q/E/S/W duration lane mean half and thirty-second
notes. They do not also add hammer-on or tapping effects. H/P/T technique marks
outside that proved rhythm lane retain their attack semantics. In compound
tokens such as `5h7~` or `14/16~`, trailing vibrato belongs to the final fret.

Tuning headers accept detached unambiguous sharp/flat glyphs on the same text
line. Explicit octave spellings retain accidental carry: B-sharp2 is C3 and
C-flat4 is B3. Inferred octaves, six/seven-string validation, capo and unsupported
reentrant-tuning rejection retain their existing behavior.

Original synthetic OCR boxes and a generated raster cover these repairs through
tab detection, decoding, the performed note/rest clock and native wire transport.
Their expected pitches and timings are asserted in `TabTripletOnsetTest`,
`TabTechniqueOwnershipTest` and `TabTuningPitchRegressionTest`. They contain no
commercial score material and do not establish whole-library accuracy.

Recognition revision 27 invalidates earlier derived interpretations. Guide 284,
native analysis -25, 80-byte notes, 33-byte rests, protocol 1, playback audio 77,
models and production dependencies are unchanged. The existing
[tie ownership rules](tab-ties.md) still apply.
