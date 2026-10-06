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
        float minimumShaftLength = gap * 1.65f,
                minimumDistance = gap * .35f,
                maximumDistance = gap * .95f,
                extension = gap * .6f;
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
            if (b - a < minimumShaftLength || a > top || b < bottom) continue;
            int hits = 0, contrasted = 0;
            boolean canContrast = x >= flank && x + flank < w;
            for (int y = a; y <= b; y++) {
                int at = y * w + x;
                if ((gray[at] & 255) <= 210) {
                    hits++;
                    if (canContrast
                            && Math.max(gray[at - flank] & 255, gray[at + flank] & 255)
                                    >= (gray[at] & 255) + 12) contrasted++;
                }
            }
            if (hits >= (b - a + 1) * .9f && contrasted >= (b - a + 1) * .65f)
                shafts.add(new Shaft(x, a, b));
        }
        for (var a : shafts)
            for (var b : shafts) {
                int distance = b.x - a.x;
                if (distance < minimumDistance || distance > maximumDistance) continue;
                if (Math.min(a.top, b.top) > top - extension
                        || Math.max(a.bottom, b.bottom) < bottom + extension) continue;
                // The candidate itself must bridge the shafts, not sit independently beside them.
                if (a.x > left + pad || b.x < right - pad) continue;
                for (int y = top; y <= bottom; y++) {
                    int hits = 0;
                    int row = y * w;
                    for (int x = a.x; x <= b.x; x++) if ((gray[row + x] & 255) <= 210) hits++;
                    if (hits >= (distance + 1) * .85f) return true;
                }
            }
        return false;
    }

    private static boolean ink(byte[] g, int w, int x, int y) {
        return (g[y * w + x] & 255) <= 210;
    }
}
