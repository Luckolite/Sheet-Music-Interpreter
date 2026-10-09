# Typed unpitched events

Legacy JSON events without `kind` are PITCHED. UNPITCHED events carry explicit
`kind: "UNPITCHED"`, written `displayStep`/`displayOctave`, finite written timing
and independent source occurrence indices. Display position describes notation;
it is not a MIDI pitch, frequency or recovered instrument. Existing pitched JSON
fields and bytes remain compatible.

For example, a written cross head can have this event:

```json
{"kind":"UNPITCHED","displayStep":"E","displayOctave":4,"clefBottomDiatonic":30,"sourceNoteIndex":0,"startBeat":0,"durationBeats":1,"staffIndex":0,"staffCount":1,"tiedFromPrevious":false}
```

JSON retains U kind and written clocks. MusicXML writes `<unpitched>`, display
position and an x notehead, retaining mixed/coincident heads and written bar spans.
Standalone MusicXML does not serialize sourceNoteIndex/sourceEventId. It makes no
new ownership metadata claim. Guide282 appends one kind byte to each complete
note record: PITCHED=0, UNPITCHED=1. Earlier guide layouts reject U rather than
discard its kind; recognition revision 4 introduced that layout. Guide 283 and
analysis marker -24 retain these note records and add separate typed rest
records. See the [current rest contract](native-decoder.md#typed-rest-records-0110).

Default CLI MIDI and MP3 exports reject U clearly. Programmatic preview APIs
require the caller to choose an explicit policy:

```python
from sheet_interpreter.musicxml import write_musicxml
from sheet_interpreter.midi import write_midi, UnpitchedMidiPreview
from sheet_interpreter.audio import write_mp3, UnpitchedAudioPreview

write_musicxml(decoded, "written.musicxml")

# These three values are chosen by the caller for this preview.
policy = UnpitchedMidiPreview(
    percussion_note=chosen_address,
    velocity=chosen_velocity,
    gate_beats=chosen_maximum_gate,
)
write_midi(decoded, "preview.mid", unpitched_preview=policy)

# Explicitly opt into a short deterministic aperiodic preview; FFmpeg is required.
write_mp3(decoded, "preview.mp3", unpitched_preview=UnpitchedAudioPreview())
```

The MIDI address belongs to the preview policy, not the source. No default
percussion mapping or instrument identity is inferred. Overlapping independent
U attacks on the single selected address require an explicit multi-address
routing decision and are rejected by this policy. Repeated visits remain distinct
attacks; contiguous bar fragments continue only their explicitly owned source.

The audio policy produces a finite short aperiodic burst without inventing a
tonal endpoint. Exact neutral DEAD evidence is allowed, with zero semitone delta,
no vibrato and no palm mute. It uses the existing finite gain/gate policy. Raw U
ties, boundary pitch evidence, octave shifts, tonal ornaments/tremolo and other
tab effects reject before tonal processing. Written notation is not lengthened
by preview gates, pedal or release tails. These examples do not define real
unpitched instrument timbre or tie semantics.

Raw unpitched grace notes and grace groups with an unpitched borrowed principal
require an explicit instrument realization. The shared timing API and JSON export
reject those source combinations; neutral DEAD evidence does not establish ownership.
