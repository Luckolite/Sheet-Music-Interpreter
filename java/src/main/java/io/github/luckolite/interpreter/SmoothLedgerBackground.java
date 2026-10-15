// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Positive broad shading evidence; callers must separately exclude stem and ledger ownership. */
final class SmoothLedgerBackground {
    static boolean matches(byte[] g, int w, int h, int l, int t, int r, int b) {
        if (g == null
                || g.length != (long) w * h
                || l < 0
                || t < 0
                || r >= w
                || b >= h
                || r - l < 5
                || b - t < 5) return false;
        int min = 255, max = 0;
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                int v = g[y * w + x] & 255;
                min = Math.min(min, v);
                max = Math.max(max, v);
                if (x > l && Math.abs(v - (g[y * w + x - 1] & 255)) > 20) return false;
                if (y > t && Math.abs(v - (g[(y - 1) * w + x] & 255)) > 20) return false;
            }
        return min >= 70 && max <= 230 && max - min >= 35;
    }
}
