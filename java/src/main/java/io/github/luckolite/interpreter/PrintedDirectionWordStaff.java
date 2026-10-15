// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Bounded word-interior fallback; both positions must independently prove the same five rails. */
final class PrintedDirectionWordStaff {
    static PlayingTechniqueDetector.Staff find(
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height,
            PlayingTechniqueDetector.Word word) {
        float left = word.left() * width, right = word.right() * width;
        if (!Float.isFinite(left) || !Float.isFinite(right) || right <= left) return null;
        var center =
                PrintedDirectionStaff.at(
                        staffs,
                        measures,
                        notes,
                        gray,
                        width,
                        height,
                        (left + right) * .5f,
                        word.top() * height,
                        word.bottom() * height);
        if (center == null || right - left > center.gap() * 6 || right - left < center.gap() * .5f)
            return null;
        var edge =
                PrintedDirectionStaff.at(
                        staffs,
                        measures,
                        notes,
                        gray,
                        width,
                        height,
                        right,
                        word.top() * height,
                        word.bottom() * height);
        if (edge == null
                || center.index() != edge.index()
                || center.count() != edge.count()
                || Math.abs(center.bottom() - edge.bottom()) > center.gap() * .2f
                || Math.abs(center.gap() - edge.gap()) > center.gap() * .08f) return null;
        return center;
    }
}
