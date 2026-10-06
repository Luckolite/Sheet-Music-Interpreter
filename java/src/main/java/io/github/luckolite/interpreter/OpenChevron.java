// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A right-pointing accent has an open left side and two arms joining at its right tip. */
final class OpenChevron {
    static boolean matches(int[] pixels, int stride, int left, int top, int right, int bottom) {
        return matches(pixels, stride, left, top, right, bottom, -1);
    }

    /** Classify the current owned queue prefix; -1 retains the full-array API. */
    static boolean matches(
            int[] pixels, int stride, int left, int top, int right, int bottom, int pixelCount) {
        int w = right - left + 1, h = bottom - top + 1;
        if (w < 8 || h < 5 || w < h * 1.25 || w > h * 4) return false;
        boolean[][] ink = new boolean[w][h];
        int limit = pixelCount == -1 ? pixels.length : pixelCount;
        for (int i = 0; i < limit; i++) {
            int p = pixels[i];
            int x = p % stride - left, y = p / stride - top;
            if (x < 0 || x >= w || y < 0 || y >= h) return false;
            ink[x][y] = true;
        }
        int[] first = new int[w], last = new int[w], runs = new int[w];
        java.util.Arrays.fill(first, h);
        java.util.Arrays.fill(last, -1);
        int split = 0;
        double upper = 0, lower = 0;
        for (int x = 0; x < w; x++) {
            int firstEnd = -1, secondStart = -1;
            for (int y = 0; y < h; y++)
                if (ink[x][y]) {
                    first[x] = Math.min(first[x], y);
                    last[x] = y;
                    if (y == 0 || !ink[x][y - 1]) {
                        runs[x]++;
                        if (runs[x] == 2) secondStart = y;
                    }
                    if (runs[x] == 1) firstEnd = y;
                }
            if (runs[x] > 2 || x >= w * .10 && x <= w * .90 && runs[x] == 0) return false;
            if (x <= w * .40
                    && runs[x] == 2
                    && secondStart - firstEnd - 1 >= Math.max(1, h * .15)
                    && firstEnd - first[x] + 1 <= h * .45
                    && last[x] - secondStart + 1 <= h * .45) {
                split++;
                upper += (first[x] + firstEnd) * .5;
                lower += (secondStart + last[x]) * .5;
            }
        }
        if (split < Math.max(2, Math.ceil(w * .18))) return false;
        upper /= split;
        lower /= split;
        double tip = 0;
        int count = 0;
        for (int x = (int) Math.ceil(w * .75); x < w; x++) {
            if (runs[x] != 1 || last[x] - first[x] + 1 > h * .65) return false;
            tip += (first[x] + last[x]) * .5;
            count++;
        }
        tip /= count;
        if (tip < h * .25 || tip > h * .75 || tip - upper < h * .18 || lower - tip < h * .18)
            return false;
        int reverse = 0, total = 0;
        for (int x = Math.max(1, (int) (w * .15)); x < w; x++) {
            if (runs[x] == 0 || runs[x - 1] == 0) continue;
            total++;
            if (first[x] < first[x - 1] - 1 || last[x] > last[x - 1] + 1) reverse++;
        }
        return reverse <= total * .15;
    }
}
