// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Necessary rhythm proof for coalescing already-proved simultaneous pitched attacks. */
public final class ScoreUnisonRhythm {
    private ScoreUnisonRhythm() {}

    /** Pitch, source-part identity, location and neighboring attacks remain caller proofs. */
    public static boolean sameWrittenDuration(ScoreNoteEvent first, ScoreNoteEvent second) {
        if (first == null
                || second == null
                || first.kind() != ScoreNoteEvent.Kind.PITCHED
                || second.kind() != ScoreNoteEvent.Kind.PITCHED
                || ((first.articulations() | second.articulations()) & NoteOrnament.GRACE) != 0)
            return false;
        double a = ScoreNoteTiming.writtenDurationBeats(first);
        double b = ScoreNoteTiming.writtenDurationBeats(second);
        return Double.isFinite(a)
                && Double.isFinite(b)
                && a > 0
                && b > 0
                && Double.compare(a, b) == 0;
    }
}
