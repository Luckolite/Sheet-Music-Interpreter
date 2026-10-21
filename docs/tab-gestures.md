# Guitar gestures

The decoder recognizes an attached bend arrow and an explicit amount: `full` or
`1` (two semitones), `1/2` or `½` (one semitone), `1/4` or `¼` (half a semitone),
`3/4`, `1 1/2`, and `2`. The amount must belong to exactly one fret. A return
stroke reconnecting to the original height produces a bend followed by release.
Numbers alone, detached arrows and plain ties/slurs do not establish a bend.

A nearby `w/bar`, `bar`, or `whammy` label identifies a bar gesture. Its arrow
direction establishes rising or falling pitch; a printed negative amount must
agree with a falling arrow. Returning gestures reach the original pitch.
Explicit amounts are required; the decoder does not guess the depth of an
unlabelled scoop, dip or dive from drawing height.

Strums accept SMuFL `guitarStrumUp` (U+E846), `guitarStrumDown` (U+E847), explicit
`upstroke`/`downstroke` text, and complete vertical arrows with two head arms.
They require a chord of at least two distinct strings. Downstrokes play bass to
treble; upstrokes play treble to bass, using string identity rather than sorting
fret numbers. Each successive attack is delayed 0.024 quarter beats, reduced for
short chords so all attacks remain inside their written duration. Releases,
source pitch, fret count and written rhythm remain unchanged.

JSON `guitarEffect` retains `type` and `semitones` (fractional values included),
plus `strumIndex` for strums (zero-based attack order). Other effects keep their
existing JSON keys. MIDI/audio
isolate pitch gestures from overlapping notes on other channels and reset bend
at release. MusicXML uses `bend-alter`, signed release deltas, `with-bar`, and
directional `arpeggiate`; its direction refers to sounding pitch order.

Legacy articulation-bit encodings keep their original integer-semitone meaning.
New kinds 9/10 represent half-semitone bend units, 11/12 represent bar gestures
in half-semitone units, and 13/14 represent down/up strum ranks. Kind 15 reserves
half-semitone pre-bend performance data; automatic pre-bend recognition is not
claimed. The physical guide layout is unchanged. Recognition/audio cache
revisions must still advance together with app and desktop consumers.

Recognition is conservative. Labelled connected arrows and standard vertical
strum arrows are covered by original generated regressions, not a general
real-score accuracy claim. Ambiguous ownership, unlabelled amounts, arbitrary
multi-stage curves, and simultaneous independent strum/bend effects on one
head are left unchanged. Graphical pitch amounts are bounded to twelve
semitones; this work changes no trained weights.

References: [SMuFL guitar symbols](https://smufl.formats.music/latest/tables/guitar.html),
[MusicXML bend-alter](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/bend-alter/),
[with-bar](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/with-bar/),
and [arpeggiate](https://www.w3.org/2021/06/musicxml40/musicxml-reference/elements/arpeggiate/).
