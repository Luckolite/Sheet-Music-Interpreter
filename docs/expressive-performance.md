# Expressive previews

The written score and the performed clock remain separate. MusicXML retains the
original pitches, durations, ties and rest spans; MIDI and audio use the performed
clock. Detection keeps the printed word or symbol and its owning source column.
Unproved ownership remains unresolved rather than being turned into an optical
estimate of musical time.

The shared Java `ScoreExpressivePerformance` policy supplies Android, desktop
playback and the standalone export bridge. Its `expressive-preview-v1` defaults
are reproducible preview choices, not a claim that expressive notation specifies
one universal duration:

| Printed evidence | Preview behavior |
| --- | --- |
| `rit.`, `rall.` | Gradual slowing to 80% of the current quarter-note BPM, across four quarter beats unless a proved endpoint is supplied. |
| `poco`, `molto` | Slowing targets of 90% and 60%, respectively. |
| `rite`, `ritenuto` | Immediate tempo reduction at the proved onset. |
| `a tempo`, `tempo primo` | Restore the relevant earlier tempo. |
| Note equals note | Multiply BPM by the right pulse divided by the left pulse, including augmentation dots. Dotted eighth equals quarter therefore multiplies BPM by 4/3. |
| Fermata | Add the held span's active duration. An identified sounding note sustains through its hold; an identified rest stays silent. |
| Breath | Insert a pause lasting one quarter of a quarter-note beat at the active tempo, after the owning release. |
| `sf`, `sfz` | Attack gain 1.6, returning to normal gain across 0.12 seconds. |
| `sfp` | Attack gain 1.6, settling to gain 0.55 across 0.12 seconds. |
| Continuous pedal bracket | Sustain keys released strictly inside a complete, owned bracket until its release. Written note lengths, attacks and score extent remain unchanged. |

Tempo ramps integrate reciprocal BPM. Playback onsets, note releases, navigation
projection and inverse seeking use that same clock. Tremolo subdivisions are
created in musical beats before tempo integration, so slowing or a hold does not
create additional attacks. A tie keeps one sounding identity.

Paired tremolos retain two source-owned endpoints, including chord members,
across one shared duration. Each endpoint advances half its printed duration;
MusicXML preserves its printed note type and emits tremolo start/stop marks.
Playback alternates the endpoint chords across that shared span. The preview
uses the app's calmer attack density: 75% of the written subdivision count,
rounded to the nearest integer, retaining at least two attacks when available.
This is a preview policy rather than a literal stroke-count performance.
Repeat visits retain separate performance identities. A jump into a pair keeps
the original alternating phase, and a Fine inside it clips the release. A
fermata owned by either endpoint binds the complete pair and extends the final
sounding pulse without adding attacks. Missing source owners or incompatible
endpoint durations are rejected instead of exporting two ordinary notes.

Rest-fermata timing requires an identified rest and an exactly accounted silent
slot between proved written note columns or bar boundaries. Overlapping sound,
ambiguous ownership or unaccounted silence prevents resolution. Continuation
pages are resolved again with inherited meter after page joining. A rest hold
does not invent a note.

MusicXML writes two metronome beat-unit groups for a pulse relation, standard
`sf`/`sfz`/`sfp` dynamics, fermatas on their owning note or rest, and breath marks
on the final written release fragment. Omitted source parts do not move these
marks onto another surviving part.

Pedal rails require continuous raw ink, two short upward hooks and independently
owned staff/bar geometry. Solid beams, dashed octave brackets, slurs, missing
hooks and ambiguous columns are rejected. Recognition persists hook columns as
`pedal-hooks-v1` evidence. Note attacks and exactly accounted rest slots resolve
those columns against the final inherited meter, including partial boundaries;
page spacing does not estimate a musical offset. Stale cached anchors cannot
replace that proof.

The shared `piano-pedal-preview-v1` policy requires a complete PART-owned down/up
pair. A missing release or conflicting repeated down remains diagnostic. Pedal
state follows navigation occurrences: a jump closes the outgoing resonance and
restores the printed state at the target. Touching brackets retain separate
release and repress operations, with release first at the shared beat. A key
still held after pedal-up retains its ordinary written release.

MIDI emits CC64 values 127 and 0 on channels isolated by part. Note-off events
retain their written/performed times; sustained voices also retain channel
ownership until pedal-up so a later pitch bend cannot retune them. The basic
standalone audio preview honors those controllers, without adding score time.
The app and desktop apply release gates only to the piano voice, after existing
tie, chord and ornament realization; custom recordings and other instruments
retain ordinary releases. MusicXML writes `pedal` start/stop directions below
their owning staff with `line="yes"` and `sign="no"`, including a final-barline
release. It preserves the written bracket rather than expanding note durations.

Guide 278 is a recognition revision of the implemented 277 record layout. It
retains the 75-byte explicit-tuplet note record, metric kind 19 and existing pedal
kinds 14/15. It introduces no new semantic kind or record field.

Guide 277 extends the stable expressive-kind table with `METRIC_MODULATION` at
ID 19. Its payload is the typed qualifier
`metric-pulse-v1:<left-quarter-beats>:<right-quarter-beats>`. Existing IDs and
length-framed records retain their meaning. Explicit guide-version readers and
writers reject this kind under a 263–276 header. App, Hub and worker compatibility
must be updated together before releasing a cache containing the new kind.

Raster templates are derived from the SIL Open Font License Bravura font. Their
manifest records the font checksum and SMuFL code points. Regressions use original
generated staff geometry and redistributable font-derived glyphs; no commercial
score scans or user-library data are included.
