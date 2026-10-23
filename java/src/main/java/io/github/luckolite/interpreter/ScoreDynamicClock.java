// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A snapped absolute dynamic belongs to its exact retained written note column. */
public final class ScoreDynamicClock {
    private ScoreDynamicClock() {}

    public static OptionalDouble onset(
            ScoreDynamicChange change,
            List<ScoreNoteEvent> notes,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> retained,
            float beatsInMeasure) {
        Objects.requireNonNull(change);
        Objects.requireNonNull(notes);
        if (change.direction() != 0 || !Float.isFinite(beatsInMeasure) || beatsInMeasure <= 0)
            return OptionalDouble.empty();
        var checked = ScorePlacementMap.validatedWritten(retained, notes);
        var measureNotes = new ArrayList<ScoreNoteEvent>();
        for (var note : notes) {
            if (note == null) return OptionalDouble.empty();
            if (note.measureIndex() == change.measureIndex()) measureNotes.add(note);
        }
        double onset = Double.NaN;
        try (var session = ScoreNoteTiming.beginTimingSession()) {
            for (int i = 0; i < notes.size(); i++) {
                var note = notes.get(i);
                if (note.measureIndex() != change.measureIndex()
                        || note.positionInMeasure() != change.positionInMeasure()
                        || (change.sharedTiming()
                                ? note.staffCount() != change.staffCount()
                                : note.staffIndex() != change.staffIndex())) continue;
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
                if (Double.isFinite(onset) && onset != placement.onsetBeats())
                    return OptionalDouble.empty();
                onset = placement.onsetBeats();
            }
        }
        return Double.isFinite(onset) ? OptionalDouble.of(onset) : OptionalDouble.empty();
    }
}
