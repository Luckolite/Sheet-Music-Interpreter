// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Short ledger stripes must lead into five independent narrow printed staff rules. */
final class ShadedLedgerMarkOwnership {
    private ShadedLedgerMarkOwnership() {}

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
            int bottom) {
        if (gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || !Float.isFinite(nx)
                || !Float.isFinite(ny)
                || !Float.isFinite(gap)
                || gap < 8
                || gap > h * .2f
                || left < 0
                || top < 0
                || right >= w
                || bottom >= h
                || right < left
                || bottom < top) return false;
        float cx = (left + right) * .5f, cy = (top + bottom) * .5f;
        if (bottom - top + 1 > gap * .35f
                || right - left + 1 < gap * .6f
                || Math.abs(cx - nx) > gap * .65f) return false;
        float distance = (cy - ny) / gap;
        if (Math.abs(distance) < .7f
                || Math.abs(distance) > 3.2f
                || Math.abs(distance * 2 - Math.round(distance * 2)) > .25f) return false;
        int x = Math.round(nx), y = Math.round(cy), radius = Math.round(gap);
        if (x - radius < 0 || x + radius >= w || y - radius < 0 || y + radius >= h) return false;
        int[] tones = new int[(2 * radius + 1) * (2 * radius + 1)];
        int n = 0;
        for (int yy = y - radius; yy <= y + radius; yy++)
            for (int xx = x - radius; xx <= x + radius; xx++) tones[n++] = gray[yy * w + xx] & 255;
        Arrays.sort(tones);
        int paper = tones[(n - 1) * 75 / 100];
        if (paper < 96 || paper >= 185) return false;
        int direction = distance < 0 ? -1 : 1;
        for (int first = 1; first <= 3; first++) {
            boolean complete = true;
            for (int k = 0; k < first; k++)
                if (!shortRule(gray, w, h, nx, cy + direction * k * gap, gap)) {
                    complete = false;
                    break;
                }
            if (!complete) continue;
            // Try two bounded widths, but keep all five rules in one common staff frame.
            for (int extent = 4; extent <= 5; extent++) {
                float previous = Float.NaN;
                complete = true;
                for (int line = 0; line < 5; line++) {
                    float row =
                            longRule(
                                    gray,
                                    w,
                                    h,
                                    nx,
                                    cy + direction * (first + line) * gap,
                                    gap,
                                    extent);
                    if (!Float.isFinite(row)
                            || Float.isFinite(previous)
                                    && Math.abs(Math.abs(row - previous) - gap) > gap * .15f) {
                        complete = false;
                        break;
                    }
                    previous = row;
                }
                if (complete) return true;
            }
        }
        return false;
    }

    private static boolean shortRule(byte[] gray, int w, int h, float cx, float seed, float gap) {
        int x = Math.round(cx), span = Math.round(gap * .75f), hits = 0;
        if (x - span < 1 || x + span >= w - 1) return false;
        for (int xx = x - span; xx <= x + span; xx++)
            if (Float.isFinite(narrowAt(gray, w, h, xx, seed, gap))) hits++;
        return hits >= (span * 2 + 1) * .8f;
    }

    private static float longRule(
            byte[] gray, int w, int h, float cx, float seed, float gap, int extent) {
        int x = Math.round(cx), near = Math.round(gap * 1.5f), far = Math.round(gap * extent);
        if (x - far < 1 || x + far >= w - 1) return Float.NaN;
        float[] rows = new float[(far - near + 1) * 2];
        int n = 0;
        for (int side : new int[] {-1, 1}) {
            int hits = 0;
            for (int d = near; d <= far; d++) {
                float row = narrowAt(gray, w, h, x + side * d, seed, gap);
                if (Float.isFinite(row)) {
                    rows[n++] = row;
                    hits++;
                }
            }
            if (hits < (far - near + 1) * .55f) return Float.NaN;
        }
        Arrays.sort(rows, 0, n);
        float center = rows[n / 2];
        int agreement = 0;
        for (int i = 0; i < n; i++) if (Math.abs(rows[i] - center) <= gap * .15f) agreement++;
        return agreement >= n * .8f ? center : Float.NaN;
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
            // The dark mark core remains bounded separately; this allows only its faint blur edge.
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
