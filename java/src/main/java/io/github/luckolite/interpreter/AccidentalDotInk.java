// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Reject a dark crossbar only when two lighter connected vertical shafts survive. */
final class AccidentalDotInk {
    private record Shaft(int x, int top, int bottom) {}

    static boolean matches(
            byte[] gray, int w, int h, int left, int top, int right, int bottom, float gap) {
        if (gray == null || gray.length != w * h || gap < 6) return false;
        int pad = Math.max(1, Math.round(gap * .2f)), reach = Math.round(gap * 2.2f);
        int first = Math.max(1, left - pad),
                last = Math.min(w - 2, right + pad),
                cy = (top + bottom) / 2;
        int lower = Math.max(0, cy - reach), upper = Math.min(h - 1, cy + reach);
        int flank = Math.max(2, Math.round(gap * .25f));
        List<Shaft> shafts = new ArrayList<>();
        for (int x = first; x <= last; x++) {
            int a = cy, b = cy, blank = 0;
            while (a > lower) {
                a--;
                if (ink(gray, w, x, a)) {
                    blank = 0;
                } else if (++blank > 1) {
                    a += blank;
                    break;
                }
            }
            blank = 0;
            while (b < upper) {
                b++;
                if (ink(gray, w, x, b)) {
                    blank = 0;
                } else if (++blank > 1) {
                    b -= blank;
                    break;
                }
            }
            if (b - a < gap * 1.65f || a > top || b < bottom) continue;
            int hits = 0, contrasted = 0;
            boolean canContrast = x >= flank && x + flank < w;
            for (int y = a; y <= b; y++)
                if (ink(gray, w, x, y)) {
                    hits++;
                    if (canContrast
                            && Math.max(
                                            gray[y * w + x - flank] & 255,
                                            gray[y * w + x + flank] & 255)
                                    >= (gray[y * w + x] & 255) + 12) contrasted++;
                }
            if (hits >= (b - a + 1) * .9f && contrasted >= (b - a + 1) * .65f)
                shafts.add(new Shaft(x, a, b));
        }
        for (var a : shafts)
            for (var b : shafts) {
                int distance = b.x - a.x;
                if (distance < gap * .35f || distance > gap * .95f) continue;
                if (Math.min(a.top, b.top) > top - gap * .6f
                        || Math.max(a.bottom, b.bottom) < bottom + gap * .6f) continue;
                // The candidate itself must bridge the shafts, not sit independently beside them.
                if (a.x > left + pad || b.x < right - pad) continue;
                for (int y = top; y <= bottom; y++) {
                    int hits = 0;
                    for (int x = a.x; x <= b.x; x++) if ((gray[y * w + x] & 255) <= 210) hits++;
                    if (hits >= (distance + 1) * .85f) return true;
                }
            }
        return false;
    }

    private static boolean ink(byte[] g, int w, int x, int y) {
        return (g[y * w + x] & 255) <= 210;
    }
}
