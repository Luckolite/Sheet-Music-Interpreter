// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Physical proof used only after independent key and meter recognition in an empty terminal region. */
final class CourtesyBoundaryInk {
    static boolean matches(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            float gap) {
        if (labels == null
                || gray == null
                || labels.length != (long) width * height
                || gray.length != labels.length
                || !Float.isFinite(gap)
                || gap < 6
                || left < 0
                || right >= width
                || right - left < gap
                || right - left > 6 * gap) return false;
        int bottom = Math.round(top + 4 * gap),
                hi = Math.max(0, Math.round(top - 2 * gap)),
                lo = Math.min(height - 1, Math.round(bottom + 2 * gap));
        if (top < 0 || bottom >= height) return false;
        int bands = 0, first = -1, last = -1, lastEnd = -1;
        boolean in = false;
        for (int x = Math.max(0, Math.round(left - 1.3f * gap));
                x <= Math.min(width - 1, Math.round(left + .3f * gap));
                x++) {
            int hits = 0;
            for (int y = top; y <= bottom; y++) if ((gray[y * width + x] & 255) < 165) hits++;
            boolean bar = hits >= (bottom - top + 1) * .94f;
            if (bar && !in) {
                bands++;
                if (first < 0) first = x;
                last = x;
            }
            if (bar) lastEnd = x;
            in = bar;
        }
        if (bands != 2 || last - first < gap * .2f || last - first > gap * 1.3f) return false;
        int key = 0, symbol = 0, unowned = 0;
        int start = Math.max(left, Math.round(lastEnd + gap * .25f));
        for (int y = hi; y <= lo; y++)
            for (int x = start; x < width; x++) {
                byte label = labels[y * width + x];
                if (label == 2) return false;
                if (label == 3) key++;
                if (label == 5) symbol++;
                if ((gray[y * width + x] & 255) >= 180) continue;
                boolean owned = false;
                for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1) && !owned; yy++)
                    for (int xx = Math.max(0, x - 1); xx <= Math.min(width - 1, x + 1); xx++) {
                        byte q = labels[yy * width + xx];
                        if (q == 3 || q == 4 || q == 5) {
                            owned = true;
                            break;
                        }
                    }
                if (!owned && ++unowned > gap * gap * .15f) return false;
            }
        return key >= gap * gap * .5f && symbol >= gap * gap;
    }
}
