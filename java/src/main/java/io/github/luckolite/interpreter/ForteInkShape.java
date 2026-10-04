// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Shared topology for the upper hook, separate cross stroke and lower foot of an italic f. */
final class ForteInkShape {
    static boolean matches(
            byte[] ink, int width, int left, int right, int top, int bottom, float gap) {
        int h = bottom - top + 1, w = right - left + 1;
        if (h < gap * 1.8f || h > gap * 3.8f || w < gap || w > gap * 3 || h < 4) return false;
        int[] spans = new int[h], centers = new int[h];
        for (int y = 0; y < h; y++) {
            int lo = width, hi = -1;
            for (int x = left; x <= right; x++)
                if (ink[(top + y) * width + x] != 0) {
                    lo = Math.min(lo, x);
                    hi = x;
                }
            if (hi >= lo) {
                spans[y] = hi - lo + 1;
                centers[y] = lo + hi;
            }
        }
        int upper = 0, cross = Math.round(h * .2f), lower = Math.round(h * .75f);
        for (int y = 1; y < h * .25f; y++) if (spans[y] > spans[upper]) upper = y;
        for (int y = cross + 1; y < h * .45f; y++) if (spans[y] > spans[cross]) cross = y;
        for (int y = lower + 1; y < h; y++) if (spans[y] > spans[lower]) lower = y;
        int[] middle =
                java.util.Arrays.copyOfRange(spans, Math.round(h * .45f), Math.round(h * .75f));
        java.util.Arrays.sort(middle);
        int stem = middle[middle.length / 2];
        if (stem < 2
                || spans[upper] < stem * 1.4f
                || spans[cross] < stem * 1.65f
                || spans[lower] < stem * 1.5f
                || cross - upper < gap * .3f
                || centers[upper] - centers[cross] < gap * .4f
                || centers[cross] - centers[lower] < gap * 1.2f) return false;
        int valley = Integer.MAX_VALUE;
        for (int y = upper + 1; y < cross; y++) valley = Math.min(valley, spans[y]);
        return valley <= Math.min(spans[upper], spans[cross]) * .65f;
    }
}
