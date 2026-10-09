// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Temporary raw shaft evidence for whole-rest classification; original records are retained. */
final class RawWholeRestVoiceEvidence {
    record Head(
            int left,
            int right,
            int top,
            int bottom,
            float x,
            float y,
            float directionGap,
            float metadataGap,
            float metadataSlope) {}

    static List<ScoreNoteEvent> directions(
            byte[] gray, int w, int h, List<ScoreNoteEvent> notes, List<Head> heads) {
        var result = new ArrayList<ScoreNoteEvent>(notes.size());
        for (int i = 0; i < notes.size(); i++) {
            var note = notes.get(i);
            Head head = heads != null && i < heads.size() ? heads.get(i) : null;
            int direction = 0;
            if (valid(head, w, h)
                    && note.kind() == ScoreNoteEvent.Kind.PITCHED
                    && note.unbeamedDurationBeats() != ScoreNoteEvent.DURATION_WHOLE) {
                direction =
                        PrintedStemDirection.detect(
                                gray, w, h, head.x(), head.y(), head.directionGap());
                if (direction == 0)
                    direction =
                            PrintedStemMetadata.detect(
                                    gray,
                                    w,
                                    h,
                                    head.left(),
                                    head.right(),
                                    head.top(),
                                    head.bottom(),
                                    head.x(),
                                    head.y(),
                                    head.metadataGap(),
                                    head.metadataSlope());
            }
            result.add(note.withStemDirection(direction));
        }
        return List.copyOf(result);
    }

    static List<ScoreNoteEvent> unknown(List<ScoreNoteEvent> notes) {
        return notes.stream().map(n -> n.withStemDirection(0)).toList();
    }

    private static boolean valid(Head head, int w, int h) {
        return head != null
                && head.left() >= 0
                && head.right() < w
                && head.top() >= 0
                && head.bottom() < h
                && head.left() <= head.right()
                && head.top() <= head.bottom()
                && Float.isFinite(head.x())
                && Float.isFinite(head.y())
                && head.x() >= head.left()
                && head.x() <= head.right()
                && head.y() >= head.top()
                && head.y() <= head.bottom()
                && Float.isFinite(head.directionGap())
                && head.directionGap() >= 4;
    }
}
