// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Rejects artwork and text that segmentation mistook for a score page. */
final class PrintedPageEvidence {
    private PrintedPageEvidence() { }

    static ScorePageInterpretation rejectStafflessPage(ScorePageInterpretation score,
            byte[] gray, int width, int height, boolean hasTabRows) {
        if (score == null || score.measures().isEmpty() || !score.notes().isEmpty()
                || hasTabRows || gray == null || width <= 0 || height <= 0
                || gray.length != (long) width * height) return score;
        // An empty measure or full-measure rest is legitimate when it sits on a printed staff.
        // A cover or title page with neither notes, tabs nor five printed rules is not a bar.
        return RawStaffLineDetector.detect(gray, width, height).isEmpty()
                ? new ScorePageInterpretation(List.of(), List.of()) : score;
    }
}
