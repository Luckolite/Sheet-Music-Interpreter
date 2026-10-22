// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Conservative preservation of an admitted filled head on a separately observed faded upshaft.
 * This certificate creates no head, erases no raster, and confers no rest/staff ownership.
 */
final class FadedUpStemNoteSupport {
    static final class Budget {
        private int remaining;

        Budget(int samples) {
            remaining = Math.max(0, samples);
        }

        private boolean debit(long n) {
            if (n < 0 || n > remaining) return false;
            remaining -= (int) n;
            return true;
        }
    }

    private FadedUpStemNoteSupport() {}

    static boolean proves(
            byte[] labels,
            byte[] gray,
            int w,
            int h,
            int l,
            int t,
            int r,
            int b,
            float gap,
            Budget budget) {
        if (labels == null
                || gray == null
                || budget == null
                || w <= 0
                || h <= 0
                || labels.length != (long) w * h
                || gray.length != labels.length
                || !Float.isFinite(gap)
                || gap < 6
                || gap > 64
                || l < 0
                || t < 0
                || r >= w
                || b >= h
                || l > r
                || t > b) return false;
        int ww = r - l + 1, hh = b - t + 1;
        if (ww < gap * .55f || ww > gap * 1.6f || hh < gap * .4f || hh > gap * 1.2f) return false;
        int reach = Math.round(gap * 4), radius = Math.max(1, Math.round(gap * .30f));
        int first = r - radius, last = r + radius, paperStep = Math.max(3, Math.round(gap * .4f));
        if (first - paperStep - 2 < 0 || last + paperStep + 2 >= w || t - reach < 0) return false;
        long columns = (long) last - first + 1;
        // Prepay every possible label/gray lookup, including roots and six exterior samples.
        long charge =
                (long) ww * hh * 2
                        + columns * ((long) hh * (2 * radius + 3) + (long) (reach + hh + 1) * 12);
        if (charge > 32768 || !budget.debit(charge)) return false;
        int semantic = 0, supported = 0;
        int[] quadrants = new int[4];
        int[] rootFrom = new int[hh];
        java.util.Arrays.fill(rootFrom, -1);
        int mx = (l + r) / 2, my = (t + b) / 2;
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++)
                if (labels[y * w + x] == 2) {
                    semantic++;
                    if ((gray[y * w + x] & 255) < 205) {
                        supported++;
                        quadrants[(y > my ? 2 : 0) + (x > mx ? 1 : 0)]++;
                        rootFrom[y - t] = x;
                    }
                }
        if (semantic < Math.max(8, Math.round(gap * gap * .11f)) || supported * 2 < semantic)
            return false;
        for (int q : quadrants) if (q < 2) return false;
        int minimumSegment = Math.max(3, Math.round(gap * .35f));
        int maximumGap = Math.max(1, Math.round(gap * .35f));
        for (int x = first; x <= last; x++) {
            int root = -1;
            for (int y = t; y <= b; y++) {
                int from = rootFrom[y - t];
                if (from < 0 || Math.abs(x - from) > radius * 2 + 2) continue;
                boolean connected = true;
                for (int xx = Math.min(from, x); xx <= Math.max(from, x); xx++)
                    if ((gray[y * w + xx] & 255) >= 225) {
                        connected = false;
                        break;
                    }
                if (connected) root = y;
            }
            if (root < 0) continue;
            int blanks = 0,
                    segment = 0,
                    segments = 0,
                    semanticRows = 0,
                    supportRows = 0,
                    lastInk = root;
            for (int y = root; y >= root - reach; y--) {
                int leftPaper =
                        median(
                                gray[y * w + x - paperStep - 1] & 255,
                                gray[y * w + x - paperStep] & 255,
                                gray[y * w + x - paperStep + 1] & 255);
                int rightPaper =
                        median(
                                gray[y * w + x + paperStep - 1] & 255,
                                gray[y * w + x + paperStep] & 255,
                                gray[y * w + x + paperStep + 1] & 255);
                int paper = Math.max(leftPaper, rightPaper);
                boolean ink = false, model = false;
                for (int xx = x - 1; xx <= x + 1; xx++) {
                    int value = gray[y * w + xx] & 255;
                    boolean stem = labels[y * w + xx] == 1;
                    model |= stem;
                    if (paper >= 210
                            && ((value < 205 && paper - value >= 24)
                                    || (stem && paper - value >= 12))) ink = true;
                }
                if (ink) {
                    supportRows++;
                    if (model) semanticRows++;
                    lastInk = y;
                    segment++;
                    blanks = 0;
                } else {
                    segments += segment / minimumSegment;
                    segment = 0;
                    if (++blanks > maximumGap) break;
                }
                int complete = segments + segment / minimumSegment;
                if (root - lastInk >= gap * 2.5f
                        && complete >= 3
                        && supportRows >= gap * 1.8f
                        && semanticRows >= Math.max(6, Math.round(gap * .72f))) return true;
            }
        }
        return false;
    }

    private static int median(int a, int b, int c) {
        return a + b + c - Math.min(a, Math.min(b, c)) - Math.max(a, Math.max(b, c));
    }
}
