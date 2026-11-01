// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Whole connected two-counter ink with a complete paper-cell waist on both sides. */
final class ClefEightWaist {
    private ClefEightWaist() {}

    static boolean proves(byte[] gray, int width, int height, int left, int top, int w, int h) {
        long area = (long) w * h;
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || left < 0
                || top < 0
                || w < 1
                || h < 1
                || (long) left + w > width
                || (long) top + h > height
                || area > 65_536) return false;
        boolean[] ink = new boolean[(int) area], seen = new boolean[(int) area];
        int[] queue = new int[(int) area];
        int components = 0;
        for (int i = 0; i < ink.length; i++)
            ink[i] = (gray[(top + i / w) * width + left + i % w] & 255) <= 175;
        for (int seed = 0; seed < ink.length; seed++) {
            if (!ink[seed] || seen[seed]) continue;
            if (++components > 1) return false;
            int take = 0, count = 1;
            queue[0] = seed;
            seen[seed] = true;
            while (take < count) {
                int p = queue[take++], x = p % w, y = p / w;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                        int n = yy * w + xx;
                        if (ink[n] && !seen[n]) {
                            seen[n] = true;
                            queue[count++] = n;
                        }
                    }
            }
        }
        if (components != 1) return false;
        java.util.Arrays.fill(seen, false);
        int holes = 0;
        int[] centreRows = new int[2];
        int[] offsets = {-1, 1, -w, w};
        for (int seed = 0; seed < ink.length; seed++) {
            if (ink[seed] || seen[seed]) continue;
            int take = 0, count = 1;
            long sumY = 0;
            boolean edge = false;
            queue[0] = seed;
            seen[seed] = true;
            while (take < count) {
                int p = queue[take++], x = p % w, y = p / w;
                sumY += y;
                edge |= x == 0 || x == w - 1 || y == 0 || y == h - 1;
                for (int d : offsets) {
                    int n = p + d;
                    if (n < 0
                            || n >= ink.length
                            || Math.abs(n % w - x) + Math.abs(n / w - y) != 1
                            || ink[n]
                            || seen[n]) continue;
                    seen[n] = true;
                    queue[count++] = n;
                }
            }
            if (!edge && count >= 2) {
                if (holes == 2) return false;
                // Exact positive-coordinate Math.round of the counter's mean row.
                centreRows[holes++] = (int) ((2 * sumY + count) / (2L * count));
            }
        }
        if (holes != 2) return false;
        int upper = Math.min(centreRows[0], centreRows[1]);
        int lower = Math.max(centreRows[0], centreRows[1]);
        if (upper == lower) return false;
        int[] lo = new int[h], hi = new int[h];
        java.util.Arrays.fill(lo, w);
        java.util.Arrays.fill(hi, -1);
        for (int i = 0; i < ink.length; i++)
            if (ink[i]) {
                int y = i / w, x = i % w;
                lo[y] = Math.min(lo[y], x);
                hi[y] = Math.max(hi[y], x);
            }
        if (hi[upper] < 0 || hi[lower] < 0) return false;
        int denominator = lower - upper;
        for (int y = upper + 1; y < lower; y++) {
            if (hi[y] < 0) continue;
            long leftNumerator = (long) lo[upper] * (lower - y) + (long) lo[lower] * (y - upper);
            long rightNumerator = (long) hi[upper] * (lower - y) + (long) hi[lower] * (y - upper);
            if (retreats(lo[y], hi[y], leftNumerator, rightNumerator, denominator)) return true;
        }
        return false;
    }

    /** One full background cell must fit, so a slanted spine's raster step is insufficient. */
    static boolean retreats(
            int left, int right, long leftNumerator, long rightNumerator, int denominator) {
        if (denominator <= 0) return false;
        long leftCeil = -Math.floorDiv(-leftNumerator, denominator);
        long rightFloor = Math.floorDiv(rightNumerator, denominator);
        return left > leftCeil && right < rightFloor;
    }
}
