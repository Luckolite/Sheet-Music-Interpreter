// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Written clocks for admitted hairpin columns, without treating page spacing as beat fractions. */
public final class ScoreHairpinClock {
    private ScoreHairpinClock() {}

    public static OptionalDouble start(
            ScoreDynamicChange change,
            List<ScoreNoteEvent> notes,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> retained,
            float beats) {
        Objects.requireNonNull(change);
        Objects.requireNonNull(notes);
        if (change.direction() == 0 || !Float.isFinite(beats) || beats <= 0)
            return OptionalDouble.empty();
        var exact =
                column(
                        change,
                        change.measureIndex(),
                        change.positionInMeasure(),
                        notes,
                        retained,
                        beats);
        if (exact.isPresent()) return exact;
        // A whole-bar held column starts at zero even when its centered whole heads
        // sit to the right of the printed wedge opening. Never infer this from silence.
        var checked = ScorePlacementMap.validatedWritten(retained, notes);
        var local = new ArrayList<ScoreNoteEvent>();
        for (var note : notes) {
            if (note == null) return OptionalDouble.empty();
            if (note.measureIndex() == change.measureIndex()) local.add(note);
        }
        boolean found = false;
        for (int i = 0; i < notes.size(); i++) {
            var note = notes.get(i);
            if (note.measureIndex() != change.measureIndex()
                    || note.staffCount() != change.staffCount()
                    || !change.sharedTiming() && note.staffIndex() != change.staffIndex()) continue;
            if (note.positionInMeasure() <= change.positionInMeasure()
                    || (note.articulations() & NoteOrnament.GRACE) != 0
                    || note.leadingRestBeats() != 0
                    || note.followingRestBeats() != 0) return OptionalDouble.empty();
            var p = checked.get(i);
            if (p == null) p = ScoreNoteTiming.writtenPlacement(note, local, beats).orElse(null);
            if (p == null || p.grace() || p.onsetBeats() != 0 || p.durationBeats() != beats)
                return OptionalDouble.empty();
            found = true;
        }
        return found ? OptionalDouble.of(0) : OptionalDouble.empty();
    }

    public static OptionalDouble end(
            ScoreDynamicChange change,
            List<ScoreNoteEvent> notes,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> retained,
            float beats) {
        Objects.requireNonNull(change);
        Objects.requireNonNull(notes);
        if (change.direction() == 0 || !Float.isFinite(beats) || beats <= 0)
            return OptionalDouble.empty();
        if (change.endPosition() == 1) return OptionalDouble.of(beats);
        if (change.endPosition() == 0) return OptionalDouble.of(0);
        return column(
                change, change.endMeasureIndex(), change.endPosition(), notes, retained, beats);
    }

    private static OptionalDouble column(
            ScoreDynamicChange c,
            int measure,
            float position,
            List<ScoreNoteEvent> notes,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> retained,
            float beats) {
        var at =
                new ScoreDynamicChange(
                        measure,
                        position,
                        c.staffIndex(),
                        c.staffCount(),
                        measure,
                        position,
                        c.decibels(),
                        0,
                        c.sharedStaffs(),
                        c.fixedTarget(),
                        c.sharedTiming());
        return ScoreDynamicClock.onset(at, notes, retained, beats);
    }
}
