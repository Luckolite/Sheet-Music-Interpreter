// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Independent continuing rules corroborate a local beam-probe slope. */
final class BeamRuleLocalSlope {
    private BeamRuleLocalSlope() {}

    static Float find(
            byte[] gray,
            int width,
            int height,
            int x,
            float top,
            float gap,
            int threshold,
            float bandCenter) {
        return findAtDistance(gray, width, height, x, top, gap, threshold, bandCenter, 1);
    }

    static Float findBeyondStem(
            byte[] gray,
            int width,
            int height,
            int x,
            float top,
            float gap,
            int threshold,
            float bandCenter) {
        return findAtDistance(gray, width, height, x, top, gap, threshold, bandCenter, 2);
    }

    private static Float findAtDistance(
            byte[] gray,
            int width,
            int height,
            int x,
            float top,
            float gap,
            int threshold,
            float bandCenter,
            int firstGaps) {
        if (gap < 4 || x < 0 || x >= width) return null;
        int bandLine = Math.round((bandCenter - top) / gap);
        if (bandLine < 0 || bandLine > 4 || Math.abs(bandCenter - top - bandLine * gap) > gap * .3f)
            return null;
        int first = Math.round(gap * firstGaps), span = Math.round(gap * 2);
        if (x - first - span < 0 || x + first + span >= width) return null;
        float[] slopes = new float[4];
        int count = 0;
        for (int line = 0; line < 5; line++) {
            if (line == bandLine) continue;
            float seed = top + line * gap;
            double sx = 0, sy = 0, sxx = 0, sxy = 0;
            int n = 0, left = 0, right = 0;
            float[] rows = new float[span * 2];
            Arrays.fill(rows, Float.NaN);
            for (int side = -1; side <= 1; side += 2)
                for (int i = 0; i < span; i++) {
                    int dx = side * (first + i);
                    float row = center(gray, width, height, x + dx, seed, gap, threshold);
                    if (!Float.isFinite(row)) continue;
                    rows[(side < 0 ? 0 : span) + i] = row;
                    sx += dx;
                    sy += row;
                    sxx += (double) dx * dx;
                    sxy += dx * row;
                    n++;
                    if (side < 0) left++;
                    else right++;
                }
            if (left < span * .85f || right < span * .85f) continue;
            double denominator = n * sxx - sx * sx;
            if (denominator <= 0) continue;
            float slope = (float) ((n * sxy - sx * sy) / denominator);
            float intercept = (float) ((sy - slope * sx) / n);
            if (Math.abs(intercept - seed) > gap * .2f || Math.abs(slope) * 3 > .35f) continue;
            double residual = 0;
            for (int side = -1; side <= 1; side += 2)
                for (int i = 0; i < span; i++) {
                    float row = rows[(side < 0 ? 0 : span) + i];
                    if (Float.isFinite(row))
                        residual += Math.abs(row - intercept - slope * side * (first + i));
                }
            if (residual / n > Math.max(.65f, gap * .06f)) continue;
            slopes[count++] = slope;
        }
        if (count < 3) return null;
        Arrays.sort(slopes, 0, count);
        float median = slopes[count / 2];
        int agreeing = 0;
        for (int i = 0; i < count; i++) if (Math.abs(slopes[i] - median) * 3 <= .15f) agreeing++;
        return agreeing >= 3 ? median : null;
    }

    private static float center(
            byte[] gray, int width, int height, int x, float seed, float gap, int threshold) {
        // Include the rule's whole blurred body before fitting its center. The
        // independently fitted intercept still must agree with the seed frame.
        int lo = Math.max(1, (int) Math.floor(seed - gap * .7f));
        int hi = Math.min(height - 2, (int) Math.ceil(seed + gap * .7f));
        float best = Float.NaN, distance = Float.POSITIVE_INFINITY;
        int run = 0;
        for (int y = lo; y <= hi + 1; y++) {
            if (y <= hi && (gray[y * width + x] & 255) < threshold) {
                run++;
                continue;
            }
            if (run > 0) {
                int first = y - run, last = y - 1;
                if (first > lo && last < hi && run <= Math.max(2, Math.ceil(gap * .4f))) {
                    float row = (first + last) * .5f;
                    // Other notation can occupy the middle of the staff space.
                    // Prove the narrow rule's own boundaries, not blank space
                    // half a gap away where a neighboring head may be printed.
                    int fringe = Math.max(1, Math.round(gap * .15f));
                    int above = Math.max(0, first - fringe);
                    int below = Math.min(height - 1, last + fringe);
                    if ((gray[above * width + x] & 255) >= threshold + 8
                            && (gray[below * width + x] & 255) >= threshold + 8
                            && Math.abs(row - seed) <= gap * .5f
                            && Math.abs(row - seed) < distance) {
                        best = row;
                        distance = Math.abs(row - seed);
                    }
                }
                run = 0;
            }
        }
        return best;
    }
}
