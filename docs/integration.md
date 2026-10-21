# Integration and output format

## Inference contract

The bundled model takes float32 `[1,3,320,320]` pixels in 0..255 and returns int64
`[1,320,320]` class IDs. The three channels contain the same grayscale plane.
Normalization is part of the graph. Preserve the model card's analysis scale,
tile stride and overlap ownership when implementing another runtime.

The Java API accepts flat `byte[]` label and grayscale arrays, with unsigned pixel
values, and explicit dimensions. Invalid shapes, unknown class IDs and pages over
20 million analysis pixels are rejected. The decoder has no Android dependencies.
`ScorePageInterpretation` and the `Score*` records retain musical evidence without
forcing a MIDI representation.

## JSON

The CLI writes a document with `schemaVersion: 1`, `inputName`, `initialBpm` and
`pages`. Each page includes:

| Field | Meaning |
|---|---|
| `width`, `height` | Dimensions after analysis resizing |
| `sourcePage` | One-based page in the input PDF |
| `modelSha256` | Exact model artifact used |
| `score.measures` | Normalized 0..1 measure rectangles |
| `score.notes` | Raw `ScoreNoteEvent` fields: staff steps, beams, dots, accidentals, ties and other notation |
| `score.keyChanges` | Zero-based measure index and number of sharps/negative flats |
| `score.tempoChanges` | Quarter-note tempos supported by supplied OCR and raw printed geometry |
| `score.meterChanges` | Validated caller readings and independently supported common/cut-time signs |
| `score.rests`, `techniqueChanges`, `dynamicChanges` | Available rest/direction evidence |
| `score.playbackDirections` | Page-local measure boundaries and navigation kinds; absent in older output |
| `events` | MIDI pitch, start/duration in quarter-note beats, staff identity, analysis-pixel x/y, tie and fallback flags |
| `measureBeats`, `totalBeats` | Measure durations and total, in quarter-note beats |
| `warnings` | Recognition and metadata limitations |

Measure indices and event times are local to each page. The MIDI writer concatenates
selected pages in reading order. A fallback key and meter carry between CLI pages;
detected key signatures and supplied meter changes update that state.

`clefInferred` means a conventional treble/bass fallback was used because the printed
clef was not established. `durationFallback` means the duration was unresolved and
the preview used half a quarter-note beat. These are not calibrated confidence scores.
Absent written accidentals are resolved using the detected or fallback key.

Recognized small grace heads carry `NoteOrnament.GRACE` (`32768`) in the raw
note's `articulations` mask. Preview timing omits them from the metrical clock,
then plays the grace group on the principal beat, borrowing at most 0.25 quarter
beats or one quarter of the principal's duration, whichever is smaller. The
principal chord moves together; accompaniment and later beats keep their timing.
This is a preview convention, not a claim that every ornament style is recognized.

## MP3 export from decoded JSON

```python
import json
from sheet_interpreter import write_mp3

with open("score.json", encoding="utf-8") as source:
    document = json.load(source)
write_mp3(document, "preview.mp3", bpm=document.get("initialBpm", 120))
```

`write_mp3` uses MIDI's shared performance clock, with a basic synthesized tone.
It accepts optional `ffmpeg=` (executable path) and `bitrate=` (kbps, default 192).
Install the `[audio]` extra or provide local FFmpeg with `libmp3lame`. The existing
output is replaced only after encoding succeeds. This is an audio preview, not a
promise of realistic instrumentation or additional expressive-mark interpretation.

## OCR and optional annotations

The normal `.[inference,pdf]` install includes offline OCR models. The Python reader
uses them automatically for images and PDF pages, including PDFs with embedded tab text. The
Java decoder accepts caller-provided OCR words; it does not start Python OCR by itself.
`--annotations file.json` overrides automatic OCR with one object per selected page.
Java callers pass equivalent `SheetInterpreter.Annotations`.
All OCR boxes are normalized 0..1 coordinates on the source page, not crop-relative
coordinates. A page object can contain these optional lists:

```json
{
  "measureNumbers": [],
  "tempoNumbers": [],
  "restCounts": [],
  "words": [],
  "tabWords": [],
  "meters": [{"measureIndex": 0, "numerator": 4, "denominator": 4}]
}
```

Numbers have `value`, `left`, `top`, `right`, `bottom`, and optional `annotationLeft`.
For a tempo, `annotationLeft` identifies the beginning of its printed direction line
while the other coordinates bound the digits. A number alone does not establish
tempo: the detector also requires supporting printed symbol/equals-sign geometry.
Put verified measure-number tokens and multi-rest counts in their respective lists.

Words have `text`, `left`, `top`, `right`, `bottom`. They can carry recognized dynamics
and techniques such as `p`, `mf`, `pizz.` or `arco`; the decoder places supported tokens
using the staff and note geometry. The automatic Python path reads the whole analysis
page; it does not reproduce the Android app's repeated crop OCR strategy.
Use `tabWords` for native PDF tablature text. These tokens inform frets and tab effects,
but an isolated `P` from a lyric or tab marking cannot become a piano dynamic.

For desktop integrations that crop stacked printed meter digits, `MeterFontMatcher`
accepts a cleaned ARGB crop, its staff-line offset and spacing, and the bundled
`java/assets/Bravura.otf`. `MusicalOcrEvidence` accepts sufficiently strong paired
font readings without generic OCR, or compatible partial OCR evidence; conflicting
digits remain unresolved. This is a bounded fallback for music glyphs that text OCR misses; it does
not make the general Python page reader infer every printed meter automatically.

Java callers can opt into staff-local meter, dynamics and ornament OCR with
`new MusicalOcr(inference, meterFontMatcher)` and the six-argument
`SheetInterpreter.analyze(labels, gray, width, height, annotations, musicalOcr)`.
The supplied `PortableOcr.Inference` remains caller-owned, including its lifetime
and thread-safety. The decoder does not load OCR models or require ONNX itself.
This overload propagates OCR errors; it does not silently substitute empty results.
Explicit meter annotations remain authoritative. The normal overload uses supplied
whole-page words and the bundled glyph templates without starting another OCR engine.

The automatic offline OCR path now sends a bounded `= BPM` line reading to the
Java tempo detector. It still requires the printed equals sign and nearby
tempo note geometry, so an isolated number is not a tempo change. Supplied
`tempoNumbers` annotations remain authoritative.
Isolated OCR numerals from 2 to 32 are also offered as rest counts; the decoder
accepts one only above an otherwise note-free measure with a printed heavy
multi-measure-rest bar. Supplied `restCounts` annotations remain authoritative.

For PDFs with embedded tab text, the CLI supplies normalized tab search boxes and
also runs local OCR for other printed words. For scanned pages and images, it supplies
words from local OCR. An explicit
`--annotations` file remains authoritative. Compound fret tokens are retained,
overlapping embedded-text search results are deduplicated, and separated digits are
joined only when their tab-string geometry agrees.

Standalone tabs may contain six or seven strings. A `Tuning:` header lists the open strings
from lowest to highest; the header is carried from the first PDF page even when processing
a later page selection. Without printed octaves, conventional descending guitar registers
are inferred. Without a header, standard six-string or seven-string guitar tuning is used.
Reentrant tunings are not covered by this header parser.

Detached stems and beams, native rests, dots, triplets, grace frets and visible ties supply
written rhythm. A blank continuation needs both a stem and a connecting arc; a muted fret
stops the preceding string's sustain. H/P/T letters are performance marks when positioned
above frets, rather than duration labels. Native meter digits and quarter-note tempo marks
are read geometrically; a bare number is not a tempo. Glyph identities follow the
[SMuFL metronome table](https://smufl.formats.music/latest/tables/metronome-marks.html).
Graphical bends, whammy-bar directions and strum direction remain outside this extraction.

## MIDI preview limits

MIDI uses 480 ticks per quarter note, a fixed velocity and standard program 0. Recognized
ties join compatible same-pitch events when timing agrees. Simultaneous same-pitch voices
use separate non-percussion channels. The preview is not an expressive instrument engine.

Measure slots retain their full meter duration, including the first measure; pickup,
cadenza and unusual engraving timing can need correction. Navigation uses retained
decoded directions; missing signs still need recognition or editing. Ornament
realization and exact polyphonic voice separation are not fully handled.
Tempo/meter arguments are explicit fallbacks, not claims of automatic recognition.
Use the retained geometry and raw score events to implement editing and richer playback.

## Bounded navigation and projected dynamics

Java renderers can route already resolved dynamics with
`ScoreGainProjection.project(curves, plan, meter, sourceTimeline)`. Supply the
same authoritative `ScoreMeterMap` used to build the route, including proved
partial opening and closing bars. Curves use source quarter beats and lane
identity `staffCount * 16 + staffIndex`; projected pieces carry performed and
source boundaries, source identity and decibel endpoints. Hairpins interpolate
in active tempo time. A meter/route disagreement is rejected rather than silently
padding partial bars. This API does not resolve printed geometry, synthesize
audio or enable continuous dynamic curves in the CLI MIDI/MP3 writer.

`ScorePlaybackDirection` retains stable wire values `0` segno, `1` to-coda,
`2` D.S. al Coda, `3` coda. Values `4..13` add repeat-start, repeat-end, ending,
plain D.C., plain D.S., D.C. al Fine, D.S. al Fine, D.C. al Coda, Fine and
measure-repeat shorthand respectively. Rich details retain quarter-beat offsets,
source/target/repeat identities, pass lists, total plays, endpoints, printed text,
evidence and return-repeat policy. Offset musical anchors when assembling pages;
retain printed source separately from performance occurrences.

`ScoreNavigationPlan.create(count, directions, meter)` supports bounded repeat,
nested-repeat, ending, D.C./D.S., Fine and Coda traversal with region-owned pass
counters and explicit diagnostics. To Coda and Fine activate only in their armed
return phase. Ordinary returns skip repeats unless explicitly overridden.
Conflicting destinations are quarantined; an unrelated invalid mark does not
erase valid navigation. Measure-repeat shorthand is diagnosed as unsupported,
not expanded into invented notes.

`ScoreNavigationProjection.project` takes explicit opening key, quarter-note BPM
and meter defaults. It restores numeric source state, handles discontinuous ties,
projects dynamics, and retains occurrence-specific expressive records and terminal
releases. It currently requires whole-bar coverage and refuses actual partial-bar
jumps. Note/rest expression targets without a proved ownership map remain unresolved.
`ScoreNavigationPerformance.project` instead maps an already-resolved numeric
tempo/hold timeline through actual traversal segments, including partial bars and
curve phase at a return. Hold boundary ownership and sustain-target mapping are
explicit; it does not choose a textual ritardando's magnitude.

`ScoreNavigationNoteProjection.project` maps already-resolved sounding intervals
through that same route. Ordinary barlines do not reattack a resolved tie; jumps
and partial endings clip intervals and give each performed instance a distinct
identity. Entry into a held interval requires an explicit reject, omit or reattack
policy. Its target mapper supplies the numeric clock with actual performed-note
ownership. Callers must first resolve source pitches, ties and ornaments; this
helper does not infer them from destination geometry.

`ScoreMeterMap.fromPerformedDurations` retains exact quarter-beat lengths of
performed segments, including partial spans that do not correspond to a printed
time signature. Use `quarterBeatsInMeasure` for double-precision lengths and
`performedMeasureCount` to distinguish this explicit grid from a printed meter
map. The legacy meter constructor and its unbounded final-meter continuation
remain compatible. Invalid lengths and segments lost to floating-point precision
are rejected rather than rounded or stretched.

Python MIDI export executes retained directions through the actual Java navigation
kernel, without re-running inference. The bounded decoded-data bridge joins pages
using the shared arrangement rules, clips partial-bar endings, restores numeric
tempo at returns and retains trailing rests. Entering a sustained interval after a
jump deliberately reattacks it; incoming ties cannot reuse a skipped predecessor,
while internal ties in the visited source run remain eligible to join. Linear
scores retain the no-Java fast path. Textual tempo curves, holds, pass-specific voice
omissions and pedal are not yet realized by this exporter. MusicXML remains in
source reading order. Neither exporter automatically consumes a resolved Java
performance clock.

Dynamic records append two backward-compatible flags. Missing `fixedTarget`
defaults to false; missing `sharedTiming` defaults to `sharedStaffs`.
`fixedTarget=true` means exact projected staff-topology-addressed state: a hairpin's
`decibels` is its absolute endpoint, not a relative six-decibel change or a request
to search for the next printed level. Generated absolute resets use the same exact
staff identity. `sharedTiming` uses both staves' onset geometry independently of
gain ownership; it is always true for a shared-staff broadcast. Renderers must
preserve these flags and apply these semantics before consuming projected dynamics.

Double-sharp notes use `writtenAccidental: 3` (two sounding semitones); `2` remains the no-local-accidental sentinel. A treble clef with an 8 above uses `clefBottomDiatonic: 37` (E5), and ends at the next printed clef. MIDI events already include these pitch changes.
## Printed slur source identities

Use the explicit physical-page Java overload when retaining printed arc evidence:

```java
ScorePageInterpretation page = SheetInterpreter.analyze(
    labels, gray, width, height, annotations, ocr, physicalPageIndex);
```

`physicalPageIndex` is a nonnegative source-page index. Legacy overloads retain
unknown page ownership. The input pixels and retained note fields stay unchanged.

`page.slurSource().spans()` contains confirmed phrases. `candidates()` contains
geometrically owned arcs whose musical classification remains unresolved. Unknown
meter, key, pitch or timing does not supply invented musical evidence. Original
page/note anchors survive selections, reordered notes, page joins and deleted
endpoints; an inserted note cannot borrow an original identity.

Once score assembly has a consistent written tick clock, grace ordering, proved
sounding pitches and exact tie owners, `ScoreSlurClassification.resolve` can
promote candidates. Exact sounding ties are already represented; ambiguous
same-pitch reattacks, missing owners and unavailable context remain deferred.
Previously confirmed intent survives later pitch edits.

Guide285 appends bounded source framing after the semantic sections. SLT1 bytes
remain unchanged for confirmed-only data; SLT2 carries a separate deferred arc
section. The Python `guide_writer` accepts both source shapes, and older guide
formats reject a downgrade that would lose retained source evidence.

## Attack-local bow directions and Guide291

Complete connected raw up-bow and down-bow signs attach to one exact written
attack on their own staff. They use technique values6 and7 in the existing
five-field record and never become persistent instrument or character state.
Contradictory directions remain unresolved. Android rendering uses the owning
rendered head/onset; Java and Python MusicXML emit technical bow signs only on
the attack fragment, once per shared chord.

Guide291 explicitly distinguishes these semantics: older-format writers refuse
bow entries, and legacy-version readers reject6/7. Existing values0..5 retain
their behavior. Exact source-attack and count validation applies across app,
Hub and both writers. Recognition85 invalidates derived recognition separately;
audio96 and model weights remain unchanged. The standalone caller preserves
its existing template/resource adapters. Original logical, licensed-font and
procedural controls contain no private score material.

Source qualification does not establish a deployed pair. Build the matching
app/Hub/page-worker/audio-worker, pass contract/drift and packaged synthetic
smoke checks, then verify installed capabilities and live processing.
