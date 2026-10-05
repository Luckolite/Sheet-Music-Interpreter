// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** A detected gliss starts on its source note and ends at the next unique written attack. */
public final class GlissPitchTarget {
    private GlissPitchTarget() {}

    public static ScoreNoteEvent next(ScoreNoteEvent source, List<ScoreNoteEvent> notes) {
        if (source == null
                || source.kind() != ScoreNoteEvent.Kind.PITCHED
                || notes == null
                || NoteOrnament.type(source.articulations()) != NoteOrnament.GLISSANDO
                || source.followingRestBeats() > 0) return null;
        ScoreNoteEvent first = null;
        for (var note : notes) {
            if (note.measureIndex() != source.measureIndex()
                    || note.staffIndex() != source.staffIndex()
                    || note.staffCount() != source.staffCount()
                    || note.clefBottomDiatonic() != source.clefBottomDiatonic()
                    || note.positionInMeasure() <= source.positionInMeasure() + .018f) continue;
            if (first == null || note.positionInMeasure() < first.positionInMeasure()) first = note;
        }
        if (first == null
                || first.kind() != ScoreNoteEvent.Kind.PITCHED
                || first.leadingRestBeats() > 0) return null;
        for (var note : notes)
            if (note != first
                    && note.measureIndex() == first.measureIndex()
                    && note.staffIndex() == first.staffIndex()
                    && note.staffCount() == first.staffCount()
                    && Math.abs(note.positionInMeasure() - first.positionInMeasure()) <= .018f)
                return null;
        return first;
    }
}
