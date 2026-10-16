// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Shared topology for the upper hook, separate cross stroke and lower foot of an italic f. */
final class ForteInkShape {
    static boolean matches(
            byte[] ink, int width, int left, int right, int top, int bottom, float gap) {
        return matches(ink, width, left, right, top, bottom, gap, false);
    }

    // A connected preceding letter may widen the full component. Its separated
    // rows permit checking the terminal f contour with the same hook/bar/foot proof.
    static boolean rightContour(
            byte[] ink, int width, int left, int right, int top, int bottom, float gap) {
        return matches(ink, width, left, right, top, bottom, gap, true);
    }

    private static boolean matches(
            byte[] ink,
            int width,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            boolean contour) {
        int splitRows = 0;
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
            if (contour && hi >= lo) {
                int run = hi;
                while (run > lo && ink[(top + y) * width + run - 1] != 0) run--;
                if (y >= h * .3f && y < h * .8f && run - lo >= gap * .65f) splitRows++;
                lo = run;
            }
            if (hi >= lo) {
                spans[y] = hi - lo + 1;
                centers[y] = lo + hi;
            }
        }
        if (contour && splitRows < Math.max(3, Math.round(h * .2f))) return false;
        int upper = 0, cross = Math.round(h * .2f), lower = Math.round(h * .75f);
        for (int y = 1; y < h * .25f; y++) if (spans[y] > spans[upper]) upper = y;
        for (int y = cross + 1; y < h * .45f; y++) if (spans[y] > spans[cross]) cross = y;
        for (int y = lower + 1; y < h; y++) if (spans[y] > spans[lower]) lower = y;
        int[] middle =
                java.util.Arrays.copyOfRange(spans, Math.round(h * .45f), Math.round(h * .75f));
        java.util.Arrays.sort(middle);
        int stem = middle[middle.length / 2];
        // A joining arch may be the widest row; require a separately valid cross stroke.
        if (contour) {
            if (stem < 2 || spans[upper] < stem * 1.4f || spans[lower] < stem * 1.5f) return false;
            for (int c = Math.round(h * .2f); c < h * .45f; c++) {
                if (spans[c] < stem * 1.65f
                        || c - upper < gap * .3f
                        || centers[upper] - centers[c] < gap * .4f
                        || centers[c] - centers[lower] < gap * 1.2f) continue;
                int valley = Integer.MAX_VALUE;
                for (int y = upper + 1; y < c; y++) valley = Math.min(valley, spans[y]);
                if (valley <= Math.min(spans[upper], spans[c]) * .65f) return true;
            }
            return false;
        }
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
