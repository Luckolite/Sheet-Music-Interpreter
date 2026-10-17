// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Compact shaded centers require surrounding paper and nearly uniform interior ink. */
final class CompactPaleHeadCore {
    static boolean proved(
            byte[] gray, int w, int h, int l, int t, int r, int b, float cx, float cy, float gap) {
        if (gray == null
                || gray.length != (long) w * h
                || w <= 0
                || h <= 0
                || !Float.isFinite(gap)
                || gap < 6
                || l < 0
                || t < 0
                || r >= w
                || b >= h
                || !Float.isFinite(cx)
                || !Float.isFinite(cy)) return false;
        int ww = r - l + 1, hh = b - t + 1;
        if (ww < gap
                || ww > gap * 1.4f
                || hh < gap * .55f
                || hh > gap
                || cx < l
                || cx > r
                || cy < t
                || cy > b) return false;
        int[] shades = new int[256];
        int count = 0, margin = Math.round(gap);
        for (int y = Math.max(0, t - margin); y <= Math.min(h - 1, b + margin); y++)
            for (int x = Math.max(0, l - margin); x <= Math.min(w - 1, r + margin); x++) {
                if (x >= l && x <= r && y >= t && y <= b) continue;
                shades[gray[y * w + x] & 255]++;
                count++;
            }
        int paper = 255, seen = 0;
        for (int v = 0; v < 256; v++)
            if ((seen += shades[v]) >= count * .8f) {
                paper = v;
                break;
            }
        if (paper < 210) return false;
        count = 0;
        int filled = 0, pale = 0;
        int coreRadius = Math.max(1, Math.round(gap * .08f));
        for (int y = Math.max(t, Math.round(cy - hh * .22f));
                y <= Math.min(b, Math.round(cy + hh * .22f));
                y++)
            for (int x = Math.max(l, Math.round(cx) - coreRadius);
                    x <= Math.min(r, Math.round(cx) + coreRadius);
                    x++) {
                int v = gray[y * w + x] & 255;
                count++;
                if (v <= paper - 30) filled++;
                if (v >= 145 && v <= paper - 30) pale++;
            }
        return count >= 12 && filled >= count * .95f && pale >= count * .75f;
    }
}
