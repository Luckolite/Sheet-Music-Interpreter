# Legacy retained conversion meters

Older retained sources can contain a false OCR time signature without a captured
written or playback clock. Applying that signature to complete written bars can
produce fractional durations which cannot be engraved exactly.

`MeterChangeDetector.repairLegacyInterpretation(score, hasRetainedTiming)` applies
the existing bounded, multiple-bar contradiction check to these sources. It does
not invent a replacement signature, change notes, or change the OCR thresholds.
Genuine short meters and inconclusive passages remain unchanged. When timing has
already been captured, the original score is returned unchanged.

The Android adapter only calls this repair for an older saved rendering with no
captured note-placement or boundary fields. Reading remains non-mutating; a
successful normal regeneration saves the repaired source and existing cumulative
layout and octave edits. Android state serialization is outside the standalone API.

`LegacyMeterRepairTest` uses original synthetic bars to cover a contradicted
reading, genuine half time, insufficient evidence, ordinary meters, the next
change boundary, and authoritative retained timing. No private score or device
data is included. Model weights, wire layouts, and ordinary page recognition are
unchanged.
