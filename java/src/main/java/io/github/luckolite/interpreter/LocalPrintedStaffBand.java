// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Other printed rules supply the local thickness and ink budget of a blurred rule. */
final class LocalPrintedStaffBand {
    private LocalPrintedStaffBand() {}

    static boolean matches(
            byte[] gray,
            int w,
            int h,
            int x,
            float top,
            float gap,
            int threshold,
            int first,
            int last) {
        if (gray == null
                || gap < 8
                || x < 1
                || x >= w - 1
                || first < 0
                || last >= h
                || last < first) return false;
        float cy = (first + last) * .5f;
        int bandLine = Math.round((cy - top) / gap);
        if (bandLine < 0 || bandLine > 4 || Math.abs(cy - top - bandLine * gap) > gap * .3f)
            return false;
        float[] near = sample(gray, w, h, x, cy, gap, threshold);
        if (near == null || near[2] < 60 || near[2] >= 185) return false;
        int start = Math.round(gap), span = Math.round(gap * 2);
        if (x - start - span < 1 || x + start + span >= w - 1) return false;
        int references = 0;
        float largestMass = 0;
        float[] phases = new float[4];
        for (int line = 0; line < 5; line++) {
            if (line == bandLine) continue;
            float seed = top + line * gap;
            float[] masses = new float[span * 2], offsets = new float[span * 2];
            int n = 0, left = 0, right = 0;
            for (int side : new int[] {-1, 1})
                for (int i = 0; i < span; i++) {
                    var a = sample(gray, w, h, x + side * (start + i), seed, gap, threshold);
                    if (a == null) continue;
                    masses[n] = a[1];
                    offsets[n++] = a[0] - seed;
                    if (side < 0) left++;
                    else right++;
                }
            if (n < span * 2 * .55f || left < span * .4f || right < span * .4f) continue;
            Arrays.sort(masses, 0, n);
            Arrays.sort(offsets, 0, n);
            float phase = offsets[n / 2];
            int agreeing = 0;
            for (int i = 0; i < n; i++)
                if (Math.abs(offsets[i] - phase) <= Math.max(1, gap * .1f)) agreeing++;
            if (agreeing < n * .85f) continue;
            phases[references++] = phase;
            largestMass = Math.max(largestMass, masses[n / 2]);
        }
        if (references < 3 || largestMass <= 0) return false;
        Arrays.sort(phases, 0, references);
        float phase = phases[references / 2];
        int agreeing = 0;
        for (int i = 0; i < references; i++)
            if (Math.abs(phases[i] - phase) <= gap * .15f) agreeing++;
        return agreeing >= 3
                && Math.abs(cy - top - bandLine * gap - phase) <= gap * .2f
                && near[1] <= largestMass * 1.4f;
    }

    /** Center, ink deficit, and local paper level of a narrow isolated source rule. */
    private static float[] sample(
            byte[] gray, int w, int h, int x, float seed, float gap, int threshold) {
        if (x < 0 || x >= w) return null;
        int lo = Math.max(1, Math.round(seed - gap * .6f)),
                hi = Math.min(h - 2, Math.round(seed + gap * .6f));
        if (hi <= lo) return null;
        int[] values = new int[hi - lo + 1];
        for (int y = lo; y <= hi; y++) values[y - lo] = gray[y * w + x] & 255;
        Arrays.sort(values);
        int paper = values[(values.length - 1) * 85 / 100];
        float[] best = null;
        float distance = Float.POSITIVE_INFINITY;
        int start = -1;
        for (int y = lo; y <= hi + 1; y++) {
            boolean ink = y <= hi && (gray[y * w + x] & 255) < threshold;
            if (ink && start < 0) start = y;
            if (!ink && start >= 0) {
                int length = y - start;
                float center = (start + y - 1) * .5f;
                if (start > lo
                        && y <= hi
                        && length <= Math.ceil(gap * .4f)
                        && Math.abs(center - seed) <= gap * .3f
                        && (gray[(start - 1) * w + x] & 255) >= threshold
                        && (gray[y * w + x] & 255) >= threshold
                        && Math.abs(center - seed) < distance) {
                    int mass = 0, dark = 255;
                    for (int yy = start - 1; yy <= y; yy++) {
                        int value = gray[yy * w + x] & 255;
                        mass += Math.max(0, paper - value);
                        dark = Math.min(dark, value);
                    }
                    if (paper - dark >= 24) {
                        best = new float[] {center, mass, paper};
                        distance = Math.abs(center - seed);
                    }
                }
                start = -1;
            }
        }
        return best;
    }
}
