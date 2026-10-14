// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Geometric evidence that two proposed staff rules are the arms of a hairpin. */
final class ConvergingStaffStrokes {
    static boolean matches(byte[] gray, int width, int height, int[] rows, float gap) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || rows == null
                || rows.length != 5
                || !Float.isFinite(gap)
                || gap < 4) return false;
        int flank = Math.max(2, Math.round(gap * .25f)),
                wide = Math.max(flank + 1, Math.round(gap * .4f));
        for (int pair = 0; pair < 4; pair++) {
            int top = Math.max(wide, Math.round(rows[pair] - gap * .5f));
            int bottom = Math.min(height - wide - 1, Math.round(rows[pair + 1] + gap * .5f));
            if (top >= bottom) continue;
            int[] upper = new int[width], lower = new int[width];
            Arrays.fill(upper, -1);
            Arrays.fill(lower, -1);
            for (int x = 0; x < width; x++)
                for (int y = top; y <= bottom; y++) {
                    int ink = gray[y * width + x] & 255;
                    // Near the apex, the other arm can occupy one contrast sample.
                    if (ink <= 230
                            && ((gray[(y - flank) * width + x] & 255) >= ink + 12
                                            && (gray[(y + flank) * width + x] & 255) >= ink + 12
                                    || (gray[(y - wide) * width + x] & 255) >= ink + 12
                                            && (gray[(y + wide) * width + x] & 255) >= ink + 12)) {
                        if (upper[x] < 0) upper[x] = y;
                        lower[x] = y;
                    }
                }
            for (int units : new int[] {16, 24, 36, 48}) {
                int span = Math.round(gap * units);
                for (int left = 0; left + span <= width; left += Math.max(1, Math.round(gap)))
                    if (wedge(upper, lower, left, span, gap)) return true;
            }
        }
        return false;
    }

    private static boolean wedge(int[] upper, int[] lower, int left, int span, float gap) {
        double[] a = new double[8], b = new double[8];
        for (int bin = 0; bin < 8; bin++) {
            int first = left + span * bin / 8, last = left + span * (bin + 1) / 8, count = 0;
            for (int x = first; x < last; x++)
                if (upper[x] >= 0) {
                    a[bin] += upper[x];
                    b[bin] += lower[x];
                    count++;
                }
            if (count < (last - first) * .9) return false;
            a[bin] /= count;
            b[bin] /= count;
        }
        double start = b[0] - a[0], end = b[7] - a[7];
        if (Math.min(start, end) > gap * .35
                || Math.max(start, end) < gap * .65
                || Math.abs(start - end) < gap * .6) return false;
        for (int i = 1; i < 7; i++) {
            if (Math.abs(a[i] - (a[0] + (a[7] - a[0]) * i / 7)) > gap * .15
                    || Math.abs(b[i] - (b[0] + (b[7] - b[0]) * i / 7)) > gap * .15) return false;
        }
        // Both arms must move toward the same apex; a staff rule beside an oblique mark is
        // insufficient.
        return (a[7] - a[0]) * (b[7] - b[0]) < 0
                && Math.abs(a[7] - a[0]) > gap * .12
                && Math.abs(b[7] - b[0]) > gap * .12;
    }
}
