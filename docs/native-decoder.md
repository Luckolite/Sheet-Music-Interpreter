# Optional native Java decoding service

The Java core can run directly on a desktop JVM. `NativeDecoderStages` preserves
the existing geometry preparation sequence, and `NativeDecoderWire` transports
its geometry and the complete analysis records without Java object serialization.
This does not replace image rasterization, segmentation or a caller's OCR engine.

`scripts/build_java.py` generates a fingerprint of the standalone Java sources.
The optional `io.github.luckolite.interpreter.NativeDecoderServer` entry point
requires a 64-hex-character credential as its first standard-input line. It binds
only to loopback (default port 45924, or a port supplied as its first argument).
Keep standard input open for the service lifetime; closing it stops the process.
Do not put credentials in command-line arguments or logs.

Clients must present the same credential and exact source fingerprint. The
standalone fingerprint is deliberately different from an application build:
matching class names do not establish matching interpretation behavior. A caller
must retain local processing when the service is unavailable, mismatched or busy.
Three requests can execute and three more can wait; further connections close.
Packet sizes, dimensions, record counts and decompressed input are bounded.
After flushing a reply, the server waits for the client to close its output side
before closing the connection. This avoids truncating large replies through a
guest network transport. The existing socket timeout bounds clients that do not
finish. That response-lifetime repair changes no protocol, recognition, model
or result format; synthetic tests verify both geometry and analysis responses.

## Typed rest records (0.1.10)

Recognition revision 25 selects the current derived interpretation; it is separate
from guide format 283 and native analysis marker -24. Transport protocol 1, note
records, model weights and production dependencies are unchanged.

Both current formats append one unsigned kind byte to the existing rest record:

| Field | Wire type | Byte offset |
| --- | --- | --- |
| measureIndex | int32 | 0 |
| positionInMeasure | float32 | 4 |
| pageY | float32 | 8 |
| pageHeight | float32 | 12 |
| staffIndex | int32 | 16 |
| staffCount | int32 | 20 |
| durationBeats | float64 | 24 |
| kind | uint8: LITERAL=0, FULL_MEASURE=1 | 32 |

Each typed rest is 33 bytes, using big-endian fields. Guides 260–282 and analysis
markers -23/-22 or the unmarked legacy count format retain 32-byte literal rests.
Their original fixtures and bytes remain unchanged. Guide 283 and analysis -24
retain guide 282/-23 note-kind records; they do not add another note byte.
Unknown rest kinds and future layouts reject. Writing FULL_MEASURE to a legacy
format rejects rather than discarding its meaning.

JSON without a rest `kind` remains LITERAL. A FULL_MEASURE rest keeps raw
`durationBeats: 4` as an undotted whole-glyph base. For public JSON performance
and MusicXML consumers, its performed onset is 0 and its extent is the
authoritative `page.measureBeats[measureIndex]`, including proved pickups and
meter changes. Missing or invalid performed spans reject.
Independent silent voices may coexist with moving notes. Owned rest fermatas
keep that span; ordinary gap sums exclude FULL_MEASURE. MusicXML writes a
separate silent voice with `<rest measure="yes"/>`, a whole type and the actual
performed duration, preserving physical staff identity and rest-only parts.
Literal-only reconstruction remains compatible.

Printed full-rest classification requires a complete undotted hanging plate and
verified physical staff/voice ownership. Tab evidence uses explicit decoded
whole-rest glyph tokens; plain duration text alone does not establish FULL.
Original scalar/pixel regressions establish these contracts, not whole-library
accuracy or fresh OCR performance.

## Review and verification boundary

The app-side client, managed-worker selection, Hub process ownership and Android
OCR prefetch are platform adapters and are not copied into this standalone
package. OCR prefetch uses an owned raster and retains the normal fallback; it
does not replace OCR or alter detection thresholds. The native core model weights
are unchanged. The standalone build fingerprint helper is original integration
code, not an Android service.

An experiment with a bounded `ExactRasterMemo` for small OCR crops found only
one identical crop among approximately 200 OCR calls on the measured page, with
no end-to-end benefit. That adapter was not retained. Existing standalone
exact-raster regressions remain available to integrations with substantial reuse.

The app worktree has pre-existing differences in `MeterChangeDetector`,
`OmrScoreInterpreter` and `ScorePageTimeline`. This service addition does not
overwrite those classes or update their provenance hashes. Each build hashes
its own sources so the two revisions cannot be silently interchanged.

Shareable tests cover complete note/rest/key and geometry record round-trips,
malformed records, authentication, fingerprint mismatch, concurrent requests and
parent lifetime. Private-page comparisons remain outside this repository. Passing
synthetic transport tests is not a claim of whole-library recognition accuracy or
a guaranteed end-to-end speedup.
