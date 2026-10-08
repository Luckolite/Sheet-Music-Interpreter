// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Removes only detector evidence owned by the pixels of an independently read direction word. */
final class PrintedWordArticulations {
    private PrintedWordArticulations() {}

    static List<ScoreNoteEvent> apply(
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] labels,
            byte[] gray,
            int width,
            int height) {
        if (words == null || words.isEmpty() || notes.isEmpty() || staffs.isEmpty()) return notes;
        List<NoteArticulationDetector.Anchor> anchors = new ArrayList<>();
        for (var note : notes) {
            if (note.measureIndex() < 0 || note.measureIndex() >= measures.size()) return notes;
            var measure = measures.get(note.measureIndex());
            float x =
                    (measure.left() + note.positionInMeasure() * (measure.right() - measure.left()))
                            * width;
            int owner = -1;
            float distance = Float.POSITIVE_INFINITY;
            for (int i = 0; i < staffs.size(); i++) {
                var staff = staffs.get(i);
                if (staff.index() != note.staffIndex()
                        || !Float.isFinite(staff.gap())
                        || staff.gap() < 3) continue;
                float d = Math.abs(note.pageY() * height - (staff.top() + staff.bottom()) * .5f);
                if (d < distance) {
                    distance = d;
                    owner = i;
                }
            }
            if (owner < 0) return notes;
            anchors.add(
                    new NoteArticulationDetector.Anchor(
                            x,
                            note.pageY() * height,
                            owner < 0 ? 0 : staffs.get(owner).gap(),
                            owner));
        }
        var bodies = ExpressiveWordBodyInk.detect(words, labels, gray, width, height, anchors);
        if (bodies.isEmpty()) return notes;
        int[] before = NoteArticulationDetector.detect(labels, gray, width, height, anchors);
        int[] after =
                NoteArticulationDetector.detectWithWordBodies(
                        labels, gray, width, height, anchors, bodies);
        List<ScoreNoteEvent> result = new ArrayList<>(notes.size());
        for (int i = 0; i < notes.size(); i++) {
            var note = notes.get(i);
            int marks = note.articulations();
            // A reconstructed anchor may differ from core geometry. In that case it cannot
            // establish that the current low bits came from these same candidate bodies.
            if (before[i] == (marks & NoteArticulation.ARTICULATIONS)) {
                int removed =
                        before[i]
                                & ~after[i]
                                & (NoteArticulation.STACCATO
                                        | NoteArticulation.STACCATISSIMO
                                        | NoteArticulation.TENUTO);
                marks &= ~removed;
            }
            result.add(marks == note.articulations() ? note : note.withArticulations(marks));
        }
        return List.copyOf(result);
    }
}
