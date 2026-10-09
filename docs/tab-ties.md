# Guitar tab ties

A same-fret tie holds the preceding attack. Hammer-ons, pull-offs and tapping
retain their separate performance semantics. A curved mark alone cannot join
different frets or transfer a unison to another string.

Existing standalone frets can prove a hold without detached rhythm stems when
one connected returning bow joins adjacent same-string, same-fret tokens. The
finite glyph endpoint windows support wide frets and courtesy parentheses;
column ink coverage follows thin antialiased strokes without bridging white gaps.
Blank-fret continuations require an owned detached stem. A short stem must be
an isolated narrow component, so a connected lyric stroke cannot create a fret.
Successfully parsed explicit T/H/P attacks override a conflicting inferred tie.

At system breaks, both independently printed shoulders must return toward the
same string. Connected component centerlines retain filled bows; tip sampling
scales with string spacing. The predecessor must be on the adjacent system,
with equal fret, tuning, pitch and voice and no intervening rest or navigation.
Page-edge evidence keeps physical string/count/fret/effective open identity
until the actual adjacent-page resolver proves the continuation. A pure tapped
source may sustain; an explicit incoming effect remains an attack.

Boundary detection receives the original tab raster after the ordinary notation
pass. Dark-pixel and antialias coverage each prove their own connected component;
their pixels are never combined to bridge a gap or borrow a neighboring glyph.
Matching vibrato and palm-mute marks may continue through a held note.

Guide 284 and native analysis -25 implement this identity in the existing
boundary integer. They preserve 80-byte notes and 33-byte rests, reject tagged
identity in every older layout, and use recognition revision 26. Transport
protocol 1, playback audio format 77, models and production dependencies are
unchanged. See [the binary contract](native-decoder.md#typed-tab-boundary-identity).

Original generated pixel and scalar controls cover true holds, chains, wide
frets, filled and antialiased bows, touching pale glyphs, owned stems, explicit
attacks, vibrato and palm mute, untied repeats, rests,
navigation, tuning and unison ownership, malformed identity and legacy framing.
The Android playback controls also check attack count and held duration.
Private source comparisons remain outside this repository. These controls do
not establish whole-library accuracy or physical-device playback validation.
