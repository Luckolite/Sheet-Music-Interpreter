# Source-owned engraving adapters

These original Apache-2.0 browser modules preserve source MusicXML hairpin
identity, rational endpoints, layout recovery and export boundaries. They are
browser adapters, separate from the standalone Java recognition API. They do
not include GPL renderer implementation. Serve this directory over local HTTP
and open `tests/hairpins.test.html`; inspect `hairpinTestResults` for procedural
DOM controls. Native integration additionally requires the separately licensed
webmscore source and a qualified renderer bundle.

Octave-shift identity compares pitches only after matching exact source/native
staff timelines and reciprocal endpoints. Native stored pitch and comparison
pitch must both be in range. Source notes, native pitches, ties and transposition
are retained. See the [MusicXML octave-shift definition](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/octave-shift/)
for the distinction between displayed and true pitch.

Normalization requires native format 4.00 and supported explicit shift types.
Overlapping or unmatched spans, ambiguous grace boundaries, and combined
capo/drum transforms are refused. Same-number overlapping marks on different
staves of one part are conservatively refused. Documents without octave shifts
retain the previous identity behavior.

Collapsed multi-rest graphics do not own musical measures. Identity, repair and
carrier export share the same validated logical measure sequence; an added
graphic cannot move ownership to a different bar or cover sounding notes.

For optional Node source controls, provide the test-only `xml-js` package in the
environment, or set `MUSIC_SHEETS_XML_JS` to an existing module directory:

```sh
node browser/engraving/tests/compatibility35.cjs
node browser/engraving/tests/ottava99.cjs
```

Set `MUSIC_SHEETS_TEST_OUTPUT` to keep JSON receipts in a chosen directory;
otherwise they go to a process-specific temporary directory. The frozen helper
under `tests/fixtures` is a compatibility-test fixture, not a production rollback.

These commands cover 134 original synthetic cases. The browser harness also
includes the same 99 octave-shift cases and three multi-rest repair/export
controls; duplicate cases are not additional independent evidence. All fixtures
are original. No commercial score, captured renderer document, phone log, model,
native binary or personal library is included. Browser/source checks do not
establish Android, audio, PDF or deployment behavior.
