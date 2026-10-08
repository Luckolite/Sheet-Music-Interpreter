// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Required short ledger stripes in an independently verified physical staff frame. */
final class TrackedLedgerMarkOwnership {
    private TrackedLedgerMarkOwnership() {}

    static boolean proved(
            byte[] gray,
            int w,
            int h,
            float nx,
            float ny,
            float gap,
            int left,
            int top,
            int right,
            int bottom,
            StaffPitchTrack track) {
        if (gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || !Float.isFinite(nx + ny + gap)
                || gap < 8
                || gap > h * .2f
                || left < 0
                || top < 0
                || right >= w
                || bottom >= h
                || right < left
                || bottom < top
                || track == null
                || !track.verified()) return false;
        float[] f = track.at(nx);
        if (!Float.isFinite(f[0] + f[1]) || f[1] < 8 || Math.abs(f[1] - gap) > gap * .2f)
            return false;
        gap = f[1];
        float cx = (left + right) * .5f, cy = (top + bottom) * .5f;
        if (bottom - top + 1 > gap * .35f
                || right - left + 1 < gap * .6f
                || Math.abs(cx - nx) > gap * .65f) return false;
        float distance = (cy - ny) / gap;
        if (Math.abs(distance) < .7f
                || Math.abs(distance) > 3.2f
                || Math.abs(distance * 2 - Math.round(distance * 2)) > .25f) return false;
        float headPhase = 2 * (f[0] - ny) / gap;
        int headStep = Math.round(headPhase);
        if (Math.abs(headPhase - headStep) > .25f) return false;
        int lineStep = 2 * Math.round((f[0] - cy) / gap);
        if (Math.abs(cy - (f[0] - lineStep * gap * .5f)) > gap * .2f) return false;
        if (headStep > 8) {
            if (lineStep < 10 || lineStep > headStep) return false;
        } else if (headStep < 0) {
            if (lineStep > -2 || lineStep < headStep) return false;
        } else return false;
        int x = Math.round(nx), y = Math.round(cy), radius = Math.round(gap);
        if (x - radius < 0 || x + radius >= w || y - radius < 0 || y + radius >= h) return false;
        int[] tones = new int[(2 * radius + 1) * (2 * radius + 1)];
        int n = 0;
        for (int yy = y - radius; yy <= y + radius; yy++)
            for (int xx = x - radius; xx <= x + radius; xx++) tones[n++] = gray[yy * w + xx] & 255;
        Arrays.sort(tones);
        int paper = tones[(n - 1) * 75 / 100];
        if (paper < 96 || paper >= 185) return false;
        int towardStaff = lineStep > 8 ? -2 : 2;
        for (int step = lineStep; step > 8 || step < 0; step += towardStaff)
            if (!shortRule(gray, w, h, nx, gap, step, track)) return false;
        for (int extent = 4; extent <= 5; extent++) {
            boolean complete = true;
            for (int step = 0; step <= 8; step += 2)
                if (!longRule(gray, w, h, nx, gap, extent, step, track)) {
                    complete = false;
                    break;
                }
            if (complete) return true;
        }
        return false;
    }

    private static boolean shortRule(
            byte[] gray, int w, int h, float nx, float gap, int step, StaffPitchTrack track) {
        int x = Math.round(nx), span = Math.round(gap * .75f), hits = 0;
        if (x - span < 1 || x + span >= w - 1) return false;
        for (int xx = x - span; xx <= x + span; xx++) {
            float[] f = track.at(xx);
            if (Float.isFinite(narrowAt(gray, w, h, xx, f[0] - step * f[1] * .5f, f[1]))) hits++;
        }
        return hits >= (span * 2 + 1) * .8f;
    }

    private static boolean longRule(
            byte[] gray,
            int w,
            int h,
            float nx,
            float gap,
            int extent,
            int step,
            StaffPitchTrack track) {
        int x = Math.round(nx), near = Math.round(gap * 1.5f), far = Math.round(gap * extent);
        if (x - far < 1 || x + far >= w - 1) return false;
        float[] residuals = new float[(far - near + 1) * 2];
        int n = 0;
        for (int side : new int[] {-1, 1}) {
            int hits = 0;
            for (int d = near; d <= far; d++) {
                int xx = x + side * d;
                float[] f = track.at(xx);
                float seed = f[0] - step * f[1] * .5f;
                float row = narrowAt(gray, w, h, xx, seed, f[1]);
                if (Float.isFinite(row)) {
                    residuals[n++] = (row - seed) / f[1];
                    hits++;
                }
            }
            if (hits < (far - near + 1) * .55f) return false;
        }
        Arrays.sort(residuals, 0, n);
        float center = residuals[n / 2];
        int agreement = 0;
        for (int i = 0; i < n; i++) if (Math.abs(residuals[i] - center) <= .15f) agreement++;
        return agreement >= n * .8f;
    }

    private static float narrowAt(byte[] gray, int w, int h, int x, float seed, float gap) {
        int flank = Math.max(3, Math.round(gap * .45f)), reach = Math.max(2, Math.round(gap * .2f));
        int lo = Math.round(seed) - reach, hi = Math.round(seed) + reach;
        if (x < 0 || x >= w || lo - flank < 0 || hi + flank >= h) return Float.NaN;
        float best = Float.NaN, distance = Float.POSITIVE_INFINITY;
        for (int y = lo; y <= hi; y++) {
            int paper = Math.max(gray[(y - flank) * w + x] & 255, gray[(y + flank) * w + x] & 255),
                    limit = paper - 12;
            if (paper < 60 || (gray[y * w + x] & 255) >= limit) continue;
            int first = y, last = y;
            while (first > y - flank && (gray[(first - 1) * w + x] & 255) < limit) first--;
            while (last < y + flank && (gray[(last + 1) * w + x] & 255) < limit) last++;
            float center = (first + last) * .5f;
            if (first <= y - flank
                    || last >= y + flank
                    || last - first + 1 > Math.ceil(gap * .4f)
                    || Math.abs(center - seed) > gap * .2f) continue;
            if (Math.abs(center - seed) < distance) {
                best = center;
                distance = Math.abs(center - seed);
            }
        }
        return best;
    }
}
