// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Places recognized rests in the existing silent slots without changing note or measure timing. */
final class ScoreRestTiming {
    static double beatInMeasure(
            ScoreRestEvent target,
            List<ScoreRestEvent> rests,
            List<ScoreNoteEvent> notes,
            float beatsPerMeasure) {
        if (target.isFullMeasure())
            return Double.isFinite(target.resolvedDurationBeats(beatsPerMeasure)) ? 0 : Double.NaN;
        ScoreNoteEvent before = null, after = null;
        if (notes != null)
            for (ScoreNoteEvent note : notes) {
                if (note.measureIndex() != target.measureIndex()
                        || note.staffIndex() != target.staffIndex()
                        || note.staffCount() != target.staffCount()) continue;
                if (note.positionInMeasure() < target.positionInMeasure()
                        && (before == null
                                || note.positionInMeasure() > before.positionInMeasure()))
                    before = note;
                if (note.positionInMeasure() > target.positionInMeasure()
                        && (after == null || note.positionInMeasure() < after.positionInMeasure()))
                    after = note;
            }
        double start = 0;
        if (before != null) {
            double duration =
                    ScoreNoteTiming.resolvedWrittenDurationBeats(before, notes, beatsPerMeasure);
            if (!Double.isFinite(duration)) return Double.NaN;
            start = ScoreNoteTiming.beatInMeasure(before, notes, beatsPerMeasure) + duration;
        }
        double end =
                after == null
                        ? beatsPerMeasure
                        : ScoreNoteTiming.beatInMeasure(after, notes, beatsPerMeasure);
        double prior = 0, remaining = 0;
        for (ScoreRestEvent rest : rests) {
            if (rest.isFullMeasure()
                    || rest.measureIndex() != target.measureIndex()
                    || rest.staffIndex() != target.staffIndex()
                    || rest.staffCount() != target.staffCount()
                    || (before != null && rest.positionInMeasure() <= before.positionInMeasure())
                    || (after != null && rest.positionInMeasure() >= after.positionInMeasure()))
                continue;
            if (rest.positionInMeasure() < target.positionInMeasure())
                prior += rest.durationBeats();
            else remaining += rest.durationBeats();
        }
        // Do not light a rest over a sounding note when optical timing is uncertain.
        if (end - start + .03125 < prior + remaining) return Double.NaN;
        return before == null ? end - remaining : start + prior;
    }

    static boolean active(
            ScoreRestEvent rest,
            List<ScoreRestEvent> rests,
            List<ScoreNoteEvent> notes,
            int measure,
            float beat,
            float beatsPerMeasure) {
        if (rest.measureIndex() != measure) return false;
        double onset = beatInMeasure(rest, rests, notes, beatsPerMeasure);
        return Double.isFinite(onset)
                && beat >= onset
                && beat < onset + rest.resolvedDurationBeats(beatsPerMeasure);
    }
}
