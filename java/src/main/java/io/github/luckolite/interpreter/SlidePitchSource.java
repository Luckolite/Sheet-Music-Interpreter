// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Binds a connected slide to a unique preceding written attack on the same part. */
public final class SlidePitchSource {
    private SlidePitchSource() {}

    public static ScoreNoteEvent previous(ScoreNoteEvent target, List<ScoreNoteEvent> notes) {
        if (target == null
                || target.kind() != ScoreNoteEvent.Kind.PITCHED
                || notes == null
                || target.measureIndex() < 0
                || !Float.isFinite(target.positionInMeasure())
                || NoteOrnament.type(target.articulations()) != NoteOrnament.SLIDE
                || (target.articulations() & NoteOrnament.FROM_PREVIOUS) == 0) return null;
        ScoreNoteEvent latest = null;
        for (ScoreNoteEvent candidate : notes) {
            if (!samePart(target, candidate) || !Float.isFinite(candidate.positionInMeasure()))
                continue;
            boolean earlierHere =
                    candidate.measureIndex() == target.measureIndex()
                            && candidate.positionInMeasure() < target.positionInMeasure() - .001f;
            boolean precedingBar =
                    target.measureIndex() > 0
                            && candidate.measureIndex() == target.measureIndex() - 1;
            if (!earlierHere && !precedingBar) continue;
            if (latest == null
                    || candidate.measureIndex() > latest.measureIndex()
                    || (candidate.measureIndex() == latest.measureIndex()
                            && candidate.positionInMeasure() > latest.positionInMeasure()))
                latest = candidate;
        }
        if (latest == null || latest.kind() != ScoreNoteEvent.Kind.PITCHED) return null;
        for (ScoreNoteEvent candidate : notes) {
            if (candidate != latest
                    && samePart(target, candidate)
                    && candidate.measureIndex() == latest.measureIndex()
                    && Math.abs(candidate.positionInMeasure() - latest.positionInMeasure()) < .001f)
                return null;
        }
        // A bar-crossing link cannot jump a written rest. Same-bar recognition already
        // proves its printed endpoints and keeps its existing accidental/ornament scope.
        if (latest.measureIndex() != target.measureIndex()
                && (target.leadingRestBeats() > 0 || latest.followingRestBeats() > 0)) return null;
        return latest;
    }

    private static boolean samePart(ScoreNoteEvent target, ScoreNoteEvent candidate) {
        return candidate != null
                && candidate.staffIndex() == target.staffIndex()
                && candidate.staffCount() == target.staffCount()
                && candidate.clefBottomDiatonic() == target.clefBottomDiatonic();
    }
}
