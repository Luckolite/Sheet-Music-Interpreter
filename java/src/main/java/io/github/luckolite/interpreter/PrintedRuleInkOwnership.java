// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;

/** A locally wider printed rule retains the contrast and phase of its continuous source rail. */
final class PrintedRuleInkOwnership {
    private PrintedRuleInkOwnership() {}

    static boolean matches(
            byte[] gray,
            int w,
            int h,
            int x,
            int first,
            int last,
            float top,
            float gap,
            float slope,
            StaffPitchTrack track) {
        return matches(gray, w, h, x, first, last, top, gap, slope, track, false);
    }

    static boolean matchesWithNarrowReference(
            byte[] gray,
            int w,
            int h,
            int x,
            int first,
            int last,
            float top,
            float gap,
            float slope,
            StaffPitchTrack track) {
        return matches(gray, w, h, x, first, last, top, gap, slope, track, true);
    }

    private static boolean matches(
            byte[] gray,
            int w,
            int h,
            int x,
            int first,
            int last,
            float top,
            float gap,
            float slope,
            StaffPitchTrack track,
            boolean narrowReference) {
        if (track == null
                || !track.verified()
                || gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || !Float.isFinite(gap)
                || gap < 8
                || gap > Math.min(w, h) * .25f
                || !Float.isFinite(top)
                || first < 0
                || last < first
                || last >= h
                || x < 1
                || x >= w - 1) return false;
        float[] local = frame(x, x, top, gap, slope, track);
        float center = (first + last) * .5f;
        int line = Math.round((center - local[0]) / local[1]);
        if (line < 0 || line > 4 || Math.abs(center - local[0] - line * local[1]) > local[1] * .25f)
            return false;
        float phase = center - local[0] - line * local[1];
        var near = new ArrayList<float[]>();
        int leftNear = 0, rightNear = 0;
        for (float off : new float[] {-.9f, -.65f, -.4f, .4f, .65f, .9f}) {
            int xx = x + Math.round(off * gap);
            float[] f = frame(xx, x, top, gap, slope, track);
            float[] s = sample(gray, w, h, xx, f[0] + line * f[1] + phase, f[1], .55f);
            if (s == null) continue;
            if (Math.abs(s[0] - f[0] - line * f[1] - phase) > Math.max(1, gap * .1f)) return false;
            if (off < 0) leftNear++;
            else rightNear++;
            near.add(s);
        }
        if (leftNear < 2 || rightNear < 2) return false;
        var continued = new ArrayList<float[]>();
        int witnesses = 0;
        float referenceContrast = 0, referenceMass = narrowReference ? Float.POSITIVE_INFINITY : 0;
        for (int side : new int[] {-1, 1}) {
            int present = 0, total = 0;
            for (int dx = Math.round(gap * 2); dx <= Math.round(gap * 5); dx++) {
                int xx = x + side * dx;
                float[] f = frame(xx, x, top, gap, slope, track);
                float[] s = sample(gray, w, h, xx, f[0] + line * f[1] + phase, f[1], .4f);
                total++;
                if (s == null) continue;
                float delta = s[0] - f[0] - line * f[1] - phase;
                if (Math.abs(delta) > Math.max(1, gap * .12f)) continue;
                present++;
                continued.add(s);
            }
            if (present < total * .7f) return false;
        }
        // Neighboring source rails corroborate the already verified five-rule frame.
        for (int other = 0; other < 5; other++) {
            if (other == line) continue;
            int hits = 0, total = 0;
            var ref = new ArrayList<float[]>();
            for (int side : new int[] {-1, 1})
                for (int dx = Math.round(gap * 2); dx <= Math.round(gap * 5); dx++) {
                    int xx = x + side * dx;
                    float[] f = frame(xx, x, top, gap, slope, track);
                    total++;
                    float[] s = sample(gray, w, h, xx, f[0] + other * f[1] + phase, f[1], .4f);
                    if (s != null
                            && Math.abs(s[0] - f[0] - other * f[1] - phase)
                                    <= Math.max(1, gap * .12f)) {
                        hits++;
                        ref.add(s);
                    }
                }
            if (hits >= total * .6f) {
                witnesses++;
                float[] rc = new float[ref.size()], rm = new float[ref.size()];
                for (int i = 0; i < ref.size(); i++) {
                    rc[i] = ref.get(i)[1];
                    rm[i] = ref.get(i)[2];
                }
                Arrays.sort(rc);
                Arrays.sort(rm);
                if (narrowReference) {
                    // One neighboring beam cannot raise a narrow-rule reference.
                    if (rm[rm.length / 2] < referenceMass) {
                        referenceContrast = rc[rc.length / 2];
                        referenceMass = rm[rm.length / 2];
                    }
                } else {
                    referenceContrast = Math.max(referenceContrast, rc[rc.length / 2]);
                    referenceMass = Math.max(referenceMass, rm[rm.length / 2]);
                }
            }
        }
        if (witnesses < 2 || continued.isEmpty()) return false;
        float[] contrasts = new float[continued.size()], masses = new float[continued.size()];
        for (int i = 0; i < continued.size(); i++) {
            contrasts[i] = continued.get(i)[1];
            masses[i] = continued.get(i)[2];
        }
        Arrays.sort(contrasts);
        Arrays.sort(masses);
        float contrast = contrasts[(contrasts.length - 1) * 3 / 4],
                mass = masses[(masses.length - 1) * 3 / 4];
        if (contrast > referenceContrast * 1.35f || mass > referenceMass * 2f) return false;
        for (float[] s : near) if (s[1] > contrast * 1.35f || s[2] > mass * 2f) return false;
        return true;
    }

    private static float[] frame(
            int x, int origin, float top, float gap, float slope, StaffPitchTrack track) {
        if (track != null) {
            float[] f = track.at(x);
            return new float[] {f[0] - 4 * f[1], f[1]};
        }
        return new float[] {top + slope * (x - origin), gap};
    }

    /** Center, maximum contrast, and ink deficit, using only local raw paper. */
    private static float[] sample(
            byte[] gray, int w, int h, int x, float seed, float gap, float maximum) {
        if (x < 0 || x >= w) return null;
        int lo = Math.max(1, Math.round(seed - gap * .55f)),
                hi = Math.min(h - 2, Math.round(seed + gap * .55f));
        if (hi <= lo) return null;
        int[] values = new int[hi - lo + 1];
        int darkest = 255, yDark = -1;
        for (int y = lo; y <= hi; y++) {
            int v = gray[y * w + x] & 255;
            values[y - lo] = v;
            if (Math.abs(y - seed) <= gap * .25f && v < darkest) {
                darkest = v;
                yDark = y;
            }
        }
        Arrays.sort(values);
        int paper = values[(values.length - 1) * 85 / 100];
        if (paper - darkest < 24) return null;
        int threshold = paper - Math.max(20, Math.round((paper - darkest) * .28f));
        if (yDark < 0) return null;
        int first = yDark, last = yDark;
        while (first > lo && (gray[(first - 1) * w + x] & 255) < threshold) first--;
        while (last < hi && (gray[(last + 1) * w + x] & 255) < threshold) last++;
        float cy = (first + last) * .5f;
        if (first == lo
                || last == hi
                || last - first + 1 > Math.ceil(gap * maximum)
                || Math.abs(cy - seed) > gap * .25f) return null;
        int mass = 0;
        for (int y = first; y <= last; y++) mass += Math.max(0, paper - (gray[y * w + x] & 255));
        return new float[] {cy, paper - darkest, mass};
    }
}
