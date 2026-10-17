// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Phase-anchored printed rules for a compact pale fragment on shaded paper. */
final class CompactLedgerContrast {
    static boolean unsupported(
            byte[] g,
            int w,
            int h,
            int l,
            int t,
            int r,
            int b,
            int area,
            float cy,
            float bottom,
            float gap) {
        if (g == null
                || w < 1
                || h < 1
                || g.length != (long) w * h
                || area < 1
                || gap < 6
                || gap > Math.min(w, h) * .25f
                || !Float.isFinite(gap)
                || !Float.isFinite(cy)
                || !Float.isFinite(bottom)
                || l < 0
                || t < 0
                || r >= w
                || b >= h
                || r < l
                || b < t
                || r - l + 1 > gap * .85f
                || b - t + 1 > gap * .85f
                || area > gap * gap * .3f
                || cy - bottom < gap * 3.2f) return false;
        int lo = 255, hi = 0;
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                int z = g[y * w + x] & 255;
                lo = Math.min(lo, z);
                hi = Math.max(hi, z);
            }
        if (lo < 120 || hi > 230 || hi - lo > 32) return false;
        int radius = Math.max(4, Math.round(gap)), count = 0;
        int[] paper = new int[(r - l + 1 + 2 * radius) * (b - t + 1 + 2 * radius)];
        for (int y = Math.max(0, t - radius); y <= Math.min(h - 1, b + radius); y++)
            for (int x = Math.max(0, l - radius); x <= Math.min(w - 1, r + radius); x++)
                if (x < l || x > r || y < t || y > b) paper[count++] = g[y * w + x] & 255;
        Arrays.sort(paper, 0, count);
        if (count < 16 || paper[(count - 1) * 3 / 4] >= 220) return false;
        int probe = Math.max(2, Math.round(gap * .3f)),
                reach = Math.max(3, Math.round(gap * .5f)),
                phase = Math.max(1, Math.round(gap * .18f));
        for (float target = bottom + gap; cy - target > gap * .65f; target += gap) {
            boolean found = false;
            for (int y = Math.max(probe, Math.round(target) - phase);
                    y <= Math.min(h - probe - 1, Math.round(target) + phase);
                    y++) {
                int left = 0, right = 0;
                for (int d = 1; d <= reach; d++) {
                    int x = l - d;
                    if (x >= 0 && contrast(g, w, x, y, probe)) left++;
                    x = r + d;
                    if (x < w && contrast(g, w, x, y, probe)) right++;
                }
                if (left >= Math.max(2, Math.round(reach * .65f))
                        && right >= Math.max(2, Math.round(reach * .65f))) {
                    found = true;
                    break;
                }
            }
            if (!found) return true;
        }
        return false;
    }

    static boolean contrast(byte[] g, int w, int x, int y, int d) {
        int ink = g[y * w + x] & 255;
        return ink <= 246
                && (g[(y - d) * w + x] & 255) >= ink + 4
                && (g[(y + d) * w + x] & 255) >= ink + 4;
    }
}
