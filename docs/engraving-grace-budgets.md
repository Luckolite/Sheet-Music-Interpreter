# Engraving identity and explicit grace budgets

`HairpinNumbering` assigns MusicXML wedge numbers in emitted document order.
Numbers remain distinct while their owners overlap across measures and staves;
closed owners may release a number for a later start. Eight original procedural
controls cover these boundaries and the MusicXML limit of sixteen concurrent pairs.
The app writer integration remains platform-specific.

The original synthetic fixtures in `tests/fixtures/engraving-grace-budgets` specify
leading, trailing, grouped and dual-side grace timing. Written grace notes consume
no metric duration. Explicit steal-time percentages allocate playback time from
their principal note; decimal allocations must survive until final MIDI tick
quantization. Additional pitches in a grace chord may omit timing attributes
without clearing the chord's owned budget. A trailing slashed grace note retains
both its trailing placement and its slash through native reload and MusicXML export.

These fixtures define a cross-component regression contract. The Apache interpreter
does not include or link the GPL engraving renderer. Its separately licensed
corresponding source and build instructions are published in the
[Music Sheets Hub source tree](https://github.com/Luckolite/Sheet-Music-Hub/tree/3daea7292084f9acc32f162b703c877aa5d4d28a/source/webmscore).
GPL implementation code is not relicensed here.

Validation distinguished source timing from old rendered output: thirteen timing
cases, nine source slur cases and four inheritance/dual-side cases passed. A broader
twelve-score check preserved all written notes/rests/spans, SVG geometry and edit
mapping. Eleven MIDI timelines were identical; the remaining differences exactly
implemented four explicit source grace allocations, with every other MIDI event
unchanged. Thirteen PDF pages were pixel-identical. These are bounded desktop
checks, not whole-library recognition or Android runtime claims.
