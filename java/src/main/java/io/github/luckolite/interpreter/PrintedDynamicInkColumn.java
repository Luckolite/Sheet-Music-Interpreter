// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A compact typed dynamic's connected raw body can own a single covered note column. */
final class PrintedDynamicInkColumn {
    private PrintedDynamicInkColumn() {}

    static float anchor(
            PlayingTechniqueDetector.Word word,
            PlayingTechniqueDetector.Staff staff,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height,
            float previous,
            int establishedMeasure) {
        if (!(word.text().equals("f") || word.text().equals("p"))
                || gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || !Float.isFinite(staff.gap())
                || staff.gap() <= 0
                || !Float.isFinite(word.left() + word.right() + word.top() + word.bottom())
                || word.left() < 0
                || word.right() > 1
                || word.top() < 0
                || word.bottom() > 1
                || word.right() <= word.left()
                || word.bottom() <= word.top()
                || (word.right() - word.left()) * width > staff.gap() * 4
                || (word.bottom() - word.top()) * height > staff.gap() * 3) return previous;
        int left = Math.round(word.left() * width), right = Math.round(word.right() * width);
        int top = Math.round(word.top() * height), bottom = Math.round(word.bottom() * height);
        long area = (long) (right - left) * (bottom - top);
        if (area <= 0 || area > 100000) return previous;
        float selected = Float.NaN;
        int selectedMeasure = -1;
        float row = (staff.top() + staff.bottom()) * .5f / height;
        for (var note : notes) {
            if (note.staffIndex() != staff.index()
                    || note.measureIndex() != establishedMeasure
                    || note.measureIndex() < 0
                    || note.measureIndex() >= measures.size()) continue;
            var region = measures.get(note.measureIndex());
            if (row < region.top() - staff.gap() / height
                    || row > region.bottom() + staff.gap() / height) continue;
            float x = region.left() + note.positionInMeasure() * (region.right() - region.left());
            if (x < word.left() || x > word.right()) continue;
            if (note.staffCount() != staff.count()) return previous;
            // Distinct physical columns inside one body cannot be ranked by closeness.
            if (Float.isFinite(selected)
                    && (selectedMeasure != note.measureIndex()
                            || Math.abs(x - selected) * width > staff.gap() * .1f)) return previous;
            if (!Float.isFinite(selected)) {
                selected = x;
                selectedMeasure = note.measureIndex();
            }
        }
        if (!Float.isFinite(selected)) return previous;
        int column = (int) Math.floor(selected * width);
        if (column < left || column >= right) return previous;
        int w = right - left, h = bottom - top;
        var seen = new boolean[(int) area];
        var queue = new int[(int) area];
        int qualifyingComponents = 0;
        for (int start = 0; start < seen.length; start++) {
            if (seen[start] || (gray[(top + start / w) * width + left + start % w] & 255) >= 145)
                continue;
            int count = 1, cursor = 0;
            queue[0] = start;
            seen[start] = true;
            int minX = start % w, maxX = minX, minY = start / w, maxY = minY;
            boolean ownsColumn = false, leavesBox = false;
            while (cursor < count) {
                int p = queue[cursor++], x = p % w, y = p / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (x + left == column) ownsColumn = true;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                            int px = left + nx, py = top + ny;
                            if (px >= 0
                                    && px < width
                                    && py >= 0
                                    && py < height
                                    && (gray[py * width + px] & 255) < 145) leavesBox = true;
                            continue;
                        }
                        int q = ny * w + nx;
                        if (!seen[q] && (gray[(ny + top) * width + nx + left] & 255) < 145) {
                            seen[q] = true;
                            queue[count++] = q;
                        }
                    }
            }
            // A point, rule or narrow stem cannot substitute for the connected letter body.
            if (!leavesBox
                    && ownsColumn
                    && maxY - minY + 1 >= h * .5f
                    && maxX - minX + 1 >= w * .25f) {
                qualifyingComponents++;
            }
        }
        return qualifyingComponents == 1 ? selected : previous;
    }
}
