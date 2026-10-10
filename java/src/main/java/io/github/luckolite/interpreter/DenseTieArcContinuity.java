// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Original-column connectivity inside an already qualified tie curve. */
final class DenseTieArcContinuity {
    static boolean proved(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float centerY,
            float gap,
            int side,
            float offset,
            float bend,
            boolean flat,
            int inkLimit,
            boolean contrast,
            boolean[] straightRows,
            int[] contrastOffsets) {
        if (left < 0
                || right >= width
                || right <= left
                || gap < 2
                || gray == null
                || labels == null
                || gray.length != (long) width * height
                || labels.length != gray.length) return false;
        int radius = Math.max(1, Math.round(gap * .1f));
        int top = Math.max(0, (int) Math.floor(centerY - gap * 3.3f - radius));
        int bottom = Math.min(height - 1, (int) Math.ceil(centerY + gap * 3.3f + radius));
        if (bottom < top) return false;
        int count = bottom - top + 1, rise = Math.max(1, (int) Math.ceil(gap * .1f) + 1);
        boolean[] previous = new boolean[count], skipped = new boolean[count];
        boolean[] next = new boolean[count], nextSkipped = new boolean[count];
        int covered = 0;
        int span = right - left + 1;
        int edgeSlack = Math.min(Math.round(gap * 1.4f), (int) Math.floor(span * .15f));
        boolean started = false;
        for (int x = left; x <= right; x++) {
            java.util.Arrays.fill(next, false);
            java.util.Arrays.fill(nextSkipped, false);
            float t = (x - left) / (float) (right - left);
            float profile = flat ? flatProfile(t) : 4 * t * (1 - t);
            int requestedY = Math.round(centerY + side * gap * (offset + bend * profile));
            boolean connected = false, canSkip = false;
            for (int y = Math.max(top, requestedY - radius);
                    y <= Math.min(bottom, requestedY + radius);
                    y++) {
                int at = y * width + x, shade = gray[at] & 255;
                if (shade > inkLimit || labels[at] == OmrMeasurePostProcessor.NOTEHEAD) continue;
                if (contrast && shade > 125) {
                    int paper = 0;
                    for (int distance : contrastOffsets) {
                        if (y >= distance)
                            paper = Math.max(paper, gray[(y - distance) * width + x] & 255);
                        if (y + distance < height)
                            paper = Math.max(paper, gray[(y + distance) * width + x] & 255);
                    }
                    if (paper < shade + 20) continue;
                }
                // A staff crossing may be obscured only by real raw ink at this column.
                // Outside a rule, thick vertical objects cannot supply the curve corridor.
                if (!straightRows[y]) {
                    int reach = Math.max(2, Math.round(gap * .4f));
                    int a = y, b = y;
                    while (a > Math.max(0, y - reach)
                            && (gray[(a - 1) * width + x] & 255) <= inkLimit) a--;
                    while (b < Math.min(height - 1, y + reach)
                            && (gray[(b + 1) * width + x] & 255) <= inkLimit) b++;
                    if (b - a > gap * .6f) continue;
                }
                int index = y - top;
                if (!started
                        || reaches(previous, index, rise)
                        || reaches(skipped, index, rise * 2)) {
                    next[index] = true;
                    connected = true;
                }
            }
            // One column of missing raster ink is tolerated; two consecutive empty
            // columns cannot inherit a frontier. No semantic label invents pixels.
            for (int i = 0; i < count; i++) {
                nextSkipped[i] = previous[i];
                canSkip |= previous[i];
            }
            if (!connected && !canSkip) {
                if (!started) {
                    if (x - left >= edgeSlack) return false;
                    continue;
                }
                // The existing head-clearance proofs allow detached printed ends.
                // Trim only the bounded outer shoulder; never restart after an interior gap.
                return right - x <= edgeSlack && covered >= span * .8f;
            }
            started |= connected;
            if (connected) covered++;
            boolean[] swap = previous;
            previous = next;
            next = swap;
            swap = skipped;
            skipped = nextSkipped;
            nextSkipped = swap;
        }
        return covered >= (right - left + 1) * .8f;
    }

    private static boolean reaches(boolean[] frontier, int index, int radius) {
        for (int i = Math.max(0, index - radius);
                i <= Math.min(frontier.length - 1, index + radius);
                i++) if (frontier[i]) return true;
        return false;
    }

    private static float flatProfile(float t) {
        double low = 0, high = 1;
        for (int step = 0; step < 16; step++) {
            double u = (low + high) * .5, v = 1 - u;
            double x = 3 * .05 * v * v * u + 3 * .95 * v * u * u + u * u * u;
            if (x < t) low = u;
            else high = u;
        }
        double u = (low + high) * .5;
        return (float) (4 * u * (1 - u));
    }
}
