// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Raw five-rule phase anchored by an isolated staff-height closing bar. */
final class ClosedStaffBarPhase {
    private static final class OuterRules {
        private static final int[] VALUES = {-1, 5};
    }

    private ClosedStaffBarPhase() {}

    private record Bar(int x, int top, int bottom, int hits) {}

    private record Fit(float bottom, float gap, int score) {}

    static float[] resolve(
            byte[] gray,
            int w,
            int h,
            float x,
            float y,
            int headLeft,
            int headRight,
            float reference,
            float gap) {
        if (gray == null
                || w < 1
                || h < 1
                || (long) w * h > gray.length
                || !Float.isFinite(x)
                || !Float.isFinite(y)
                || !Float.isFinite(reference)
                || !Float.isFinite(gap)
                || gap < 6
                || gap > 40
                || headLeft < 0
                || headRight >= w
                || headLeft > headRight
                || x < headLeft
                || x > headRight
                || y < 0
                || y >= h
                || reference < 0
                || reference >= h) return null;
        int flank = Math.max(2, Math.round(gap * .32f));
        int first = Math.max(flank + 2, headRight + Math.round(gap * .5f));
        int last = Math.min(w - flank - 3, Math.round(x + gap * 6));
        int top = Math.max(flank + 3, Math.round(reference - gap * 6));
        int bottom = Math.min(h - flank - 4, Math.round(reference + gap * 1.5f));
        List<Bar> bars = new ArrayList<>();
        for (int bx = first; bx <= last; bx++) {
            int start = -1, previous = -1, hits = 0;
            for (int row = top; row <= bottom + 1; row++) {
                boolean hit = row <= bottom && vertical(gray, w, row, bx, flank);
                if (start >= 0
                        && (!hit && row - previous > Math.max(3, Math.round(gap * .4f))
                                || row > bottom)) {
                    int span = previous - start + 1;
                    if (span >= gap * 3.6f && span <= gap * 4.6f && hits >= span * .65f)
                        bars.add(new Bar(bx, start, previous, hits));
                    start = -1;
                    hits = 0;
                }
                if (hit) {
                    if (start < 0) start = row;
                    previous = row;
                    hits++;
                }
            }
        }
        // Adjacent pixels of the same stroke supply one anchor, not extra votes.
        List<Bar> anchors = new ArrayList<>();
        bars.sort(Comparator.comparingInt(Bar::x));
        for (int i = 0; i < bars.size(); ) {
            Bar best = bars.get(i++);
            int end = best.x;
            while (i < bars.size() && bars.get(i).x <= end + 2) {
                Bar next = bars.get(i++);
                end = next.x;
                if (next.hits > best.hits) best = next;
            }
            anchors.add(best);
        }
        List<Fit> fits = new ArrayList<>();
        for (Bar bar : anchors) {
            int left = Math.max(2, Math.round(x - gap * 3.5f));
            int leftEnd = headLeft - Math.round(gap * .45f);
            int right = headRight + Math.round(gap * .45f);
            int rightEnd = Math.min(Math.round(x + gap * 3.5f), bar.x - 2);
            if (leftEnd - left + 1 < 8 || rightEnd - right + 1 < 8) continue;
            for (int dy = -2; dy <= 3; dy++)
                for (int gs = -5; gs <= 7; gs++) {
                    float spacing = (bar.bottom - bar.top) / 4f + gs * .125f;
                    float barBottom = bar.bottom + dy;
                    if (spacing < gap * .9f
                            || spacing > gap * 1.1f
                            || !closing(gray, w, h, bar.x, barBottom, spacing)) continue;
                    if (!joinedRules(gray, w, h, bar.x, barBottom, spacing, flank)) continue;
                    for (int ss = -16; ss <= 16; ss++) {
                        float slope = ss * .02f, local = barBottom + slope * (x - bar.x);
                        if (Math.abs(local - reference) > gap * 1.6f) continue;
                        int score = 0;
                        boolean valid = true;
                        for (int line = 0; line < 5 && valid; line++)
                            for (int side = 0; side < 2; side++) {
                                int from = side == 0 ? left : right,
                                        to = side == 0 ? leftEnd : rightEnd,
                                        hits = 0;
                                for (int xx = from; xx <= to; xx++) {
                                    int yy =
                                            Math.round(
                                                    barBottom
                                                            - line * spacing
                                                            + slope * (xx - bar.x));
                                    if (thin(gray, w, h, xx, yy, flank)) hits++;
                                }
                                if (hits < (to - from + 1) * .55f) {
                                    valid = false;
                                    break;
                                }
                                score += hits;
                            }
                        if (valid) {
                            Fit refined =
                                    refine(
                                            gray, w, h, x, bar.x, barBottom, spacing, slope, left,
                                            leftEnd, right, rightEnd, flank, score);
                            if (refined != null
                                    && refined.gap >= gap * .9f
                                    && refined.gap <= gap * 1.1f) fits.add(refined);
                        }
                    }
                }
        }
        if (fits.isEmpty()) return null;
        fits.sort(Comparator.comparingInt(Fit::score).reversed());
        Fit best = fits.get(0);
        int pitch = Math.round((best.bottom - y) * 2 / best.gap);
        List<Fit> agreed = new ArrayList<>();
        for (Fit fit : fits) {
            if (fit.score < best.score * .98f) break;
            if (Math.round((fit.bottom - y) * 2 / fit.gap) != pitch
                    || Math.abs(fit.bottom - best.bottom) > gap * .3f) {
                return null;
            }
            agreed.add(fit);
        }
        agreed.sort(Comparator.comparingDouble(Fit::bottom));
        Fit selected = agreed.get(agreed.size() / 2);
        return new float[] {selected.bottom, selected.gap};
    }

    private static boolean vertical(byte[] gray, int w, int y, int x, int flank) {
        int ink = 255;
        for (int dx = -1; dx <= 1; dx++) ink = Math.min(ink, gray[y * w + x + dx] & 255);
        return ink < 170
                && (gray[y * w + x - flank] & 255) >= ink + 16
                && (gray[y * w + x + flank] & 255) >= ink + 16;
    }

    private static Fit refine(
            byte[] gray,
            int w,
            int h,
            float headX,
            int barX,
            float bottom,
            float gap,
            float slope,
            int left,
            int leftEnd,
            int right,
            int rightEnd,
            int flank,
            int score) {
        double[][] matrix = new double[3][4];
        List<double[]> points = new ArrayList<>();
        for (int line = 0; line < 5; line++)
            for (int side = 0; side < 2; side++) {
                int from = side == 0 ? left : right, to = side == 0 ? leftEnd : rightEnd;
                for (int x = from; x <= to; x++) {
                    int row = Math.round(bottom - line * gap + slope * (x - barX));
                    if (row - flank - 2 < 0 || row + flank + 2 >= h) continue;
                    double weighted = 0, total = 0;
                    for (int dy = -2; dy <= 2; dy++) {
                        int y = row + dy, ink = gray[y * w + x] & 255;
                        int contrast =
                                Math.min(
                                                gray[(y - flank) * w + x] & 255,
                                                gray[(y + flank) * w + x] & 255)
                                        - ink;
                        if (ink <= 180 && contrast >= 20) {
                            weighted += y * (contrast - 19);
                            total += contrast - 19;
                        }
                    }
                    if (total == 0) continue;
                    double observed = weighted / total;
                    double[] v = {1, -line, x - barX};
                    points.add(new double[] {line, x - barX, observed});
                    for (int a = 0; a < 3; a++) {
                        for (int b = 0; b < 3; b++) matrix[a][b] += v[a] * v[b];
                        matrix[a][3] += v[a] * observed;
                    }
                }
            }
        for (int a = 0; a < 3; a++) {
            int pivot = a;
            for (int i = a + 1; i < 3; i++)
                if (Math.abs(matrix[i][a]) > Math.abs(matrix[pivot][a])) pivot = i;
            double[] swap = matrix[a];
            matrix[a] = matrix[pivot];
            matrix[pivot] = swap;
            double divisor = matrix[a][a];
            if (Math.abs(divisor) < .000001) return null;
            for (int b = a; b < 4; b++) matrix[a][b] /= divisor;
            for (int i = 0; i < 3; i++)
                if (i != a) {
                    double factor = matrix[i][a];
                    for (int b = a; b < 4; b++) matrix[i][b] -= factor * matrix[a][b];
                }
        }
        double b = matrix[0][3], g = matrix[1][3], s = matrix[2][3], error = 0;
        for (double[] p : points) {
            double d = b - p[0] * g + p[1] * s - p[2];
            error += d * d;
        }
        if (Math.abs(s) > .35 || Math.sqrt(error / points.size()) > gap * .12) return null;
        return new Fit((float) (b + s * (headX - barX)), (float) g, score);
    }

    private static boolean thin(byte[] gray, int w, int h, int x, int row, int flank) {
        if (x < 0 || x >= w || row - flank - 2 < 0 || row + flank + 2 >= h) return false;
        for (int dy = -2; dy <= 2; dy++) {
            int y = row + dy, ink = gray[y * w + x] & 255;
            if (ink <= 180
                    && (gray[(y - flank) * w + x] & 255) >= ink + 20
                    && (gray[(y + flank) * w + x] & 255) >= ink + 20) return true;
        }
        return false;
    }

    private static boolean joinedRules(
            byte[] gray, int w, int h, int x, float bottom, float gap, int flank) {
        int from = Math.max(2, x - Math.round(gap)), to = x - 3;
        if (to - from < 4) return false;
        for (int line = 0; line < 5; line++) {
            int hits = 0;
            int row = Math.round(bottom - line * gap);
            for (int xx = from; xx <= to; xx++) if (thin(gray, w, h, xx, row, flank)) hits++;
            if (hits < (to - from + 1) * .65f) return false;
        }
        // A sixth rule joined to the same stroke leaves the group ambiguous.
        for (int line : OuterRules.VALUES) {
            int hits = 0;
            int row = Math.round(bottom - line * gap);
            for (int xx = from; xx <= to; xx++) if (thin(gray, w, h, xx, row, flank)) hits++;
            if (hits > (to - from + 1) * .4f) return false;
        }
        return true;
    }

    private static boolean closing(byte[] gray, int w, int h, int x, float bottom, float gap) {
        int top = Math.round(bottom - 4 * gap), last = Math.round(bottom), hit = 0, spaces = 0;
        int radius = Math.max(1, Math.round(gap * .20f)), flank = radius + 3;
        if (top - gap < 0 || last + gap >= h || x < flank || x + flank >= w) return false;
        for (int y = top; y <= last; y++) {
            float phase = (bottom - y) / gap;
            if (Math.abs(phase - Math.round(phase)) < .22f) continue;
            spaces++;
            int ink = 255;
            for (int dx = -radius; dx <= radius; dx++)
                ink = Math.min(ink, gray[y * w + x + dx] & 255);
            if (ink < 170
                    && (gray[y * w + x - flank] & 255) > ink + 16
                    && (gray[y * w + x + flank] & 255) > ink + 16) hit++;
        }
        if (spaces < 8 || hit < spaces * .9f) return false;
        int outside = 0, samples = 0;
        for (int d = Math.max(3, Math.round(gap * .35f)); d <= gap; d++)
            for (int y : new int[] {top - d, last + d}) {
                samples++;
                int ink = 255;
                for (int dx = -radius; dx <= radius; dx++)
                    ink = Math.min(ink, gray[y * w + x + dx] & 255);
                if (ink < 170
                        && (gray[y * w + x - flank] & 255) > ink + 16
                        && (gray[y * w + x + flank] & 255) > ink + 16) outside++;
            }
        return outside <= samples * .15f;
    }
}
