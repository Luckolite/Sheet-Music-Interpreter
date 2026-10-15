// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves selected notes on the complete source clock before omitted parts lose timing evidence. */
public final class ScoreRhythmProjection {
    private ScoreRhythmProjection() {}

    public record Placement(double onsetBeats, double durationBeats, boolean independentDuration) {
        public Placement {
            if (!Double.isFinite(onsetBeats)
                    || onsetBeats < 0
                    || !Double.isFinite(durationBeats)
                    || durationBeats <= 0)
                throw new IllegalArgumentException("Invalid source rhythm placement");
        }
    }

    public static Map<Integer, Placement> resolve(
            ScorePageInterpretation source, List<ScoreNoteEvent> selected, float beatsPerMeasure) {
        try (var timing = ScoreNoteTiming.beginTimingSession(source)) {
            return resolve(source.notes(), selected, beatsPerMeasure, source.meterChanges());
        }
    }

    public static Map<Integer, Placement> resolve(
            List<ScoreNoteEvent> source,
            List<ScoreNoteEvent> selected,
            float beatsPerMeasure,
            List<ScoreMeterChange> meters) {
        var result = new LinkedHashMap<Integer, Placement>();
        var owners = new java.util.HashMap<ScoreNoteEvent, ScoreNoteEvent>();
        for (ScoreNoteEvent note : source) owners.putIfAbsent(note, note);
        var meter = new ScoreMeterMap(beatsPerMeasure, meters);
        try (var session = ScoreNoteTiming.beginTimingSession()) {
            for (int i = 0; i < selected.size(); i++) {
                ScoreNoteEvent note = owners.get(selected.get(i));
                // A newly combined unison record has no single source owner; retain its own clock.
                if (note == null) continue;
                float beats = meter.beatsInMeasure(note.measureIndex());
                result.put(
                        i,
                        new Placement(
                                ScoreNoteTiming.beatInMeasure(note, source, beats),
                                ScoreNoteTiming.resolvedWrittenDurationBeats(note, source, beats),
                                ScoreNoteTiming.hasIndependentDuration(note, source)));
            }
        }
        return Map.copyOf(result);
    }
}
