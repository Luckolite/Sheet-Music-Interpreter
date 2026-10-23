// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Resolve a persistent instruction at its first affected written attack, never by page width. */
public final class ScoreTechniqueClock {
    private ScoreTechniqueClock() {}

    public static OptionalDouble onset(
            ScoreTechniqueChange change,
            List<ScoreNoteEvent> notes,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> retained,
            float beatsInMeasure) {
        Objects.requireNonNull(change);
        Objects.requireNonNull(notes);
        if (ScoreTechniqueChange.isBow(change.technique())
                || !Float.isFinite(beatsInMeasure)
                || beatsInMeasure <= 0) return OptionalDouble.empty();
        var checked = ScorePlacementMap.validatedWritten(retained, notes);
        float first = Float.POSITIVE_INFINITY;
        var measureNotes = new ArrayList<ScoreNoteEvent>();
        for (var note : notes) {
            if (note == null) return OptionalDouble.empty();
            if (note.measureIndex() != change.measureIndex()) continue;
            measureNotes.add(note);
            if (note.staffIndex() != change.staffIndex()) continue;
            // This is the existing persistent-instruction activation law used by audio.
            // It selects a note column; this small geometric tolerance never supplies time.
            if (change.positionInMeasure() > note.positionInMeasure() + .001f) continue;
            first = Math.min(first, note.positionInMeasure());
        }
        if (!Float.isFinite(first)) return OptionalDouble.empty();
        double onset = Double.NaN, earliest = Double.POSITIVE_INFINITY;
        try (var session = ScoreNoteTiming.beginTimingSession()) {
            for (int i = 0; i < notes.size(); i++) {
                var note = notes.get(i);
                if (note.measureIndex() != change.measureIndex()
                        || note.staffIndex() != change.staffIndex()
                        || change.positionInMeasure() > note.positionInMeasure() + .001f) continue;
                if (note.staffCount() != change.staffCount()) return OptionalDouble.empty();
                var placement = checked.get(i);
                if (placement == null)
                    placement =
                            ScoreNoteTiming.writtenPlacement(note, measureNotes, beatsInMeasure)
                                    .orElse(null);
                if (placement == null
                        || !Double.isFinite(placement.onsetBeats())
                        || placement.onsetBeats() < 0
                        || placement.onsetBeats() > beatsInMeasure
                        || placement.onsetBeats() == beatsInMeasure && !placement.grace())
                    return OptionalDouble.empty();
                earliest = Math.min(earliest, placement.onsetBeats());
                if (note.positionInMeasure() == first) {
                    if (Double.isFinite(onset) && onset != placement.onsetBeats())
                        return OptionalDouble.empty();
                    onset = placement.onsetBeats();
                }
            }
        }
        // A later affected physical column cannot secretly precede this one in musical time.
        return Double.isFinite(onset) && onset == earliest
                ? OptionalDouble.of(onset)
                : OptionalDouble.empty();
    }
}
