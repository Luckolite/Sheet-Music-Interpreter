// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Positive contrast of a ledger rule protruding on both sides of a predicted head. */
final class LedgerRuleContrast {
    static boolean proved(byte[] g, int w, int h, int left, int right, float cy, float gap) {
        if (g == null || g.length != (long) w * h || gap < 6 || !Float.isFinite(gap)) return false;
        int flank = Math.max(2, Math.round(gap * .35f)),
                reach = Math.max(3, Math.round(gap * .65f));
        for (int y = Math.max(flank, Math.round(cy - gap * .9f));
                y <= Math.min(h - flank - 1, Math.round(cy + gap * .9f));
                y++) {
            int a = 0, b = 0;
            for (int d = 1; d <= reach; d++) {
                int x = left - d;
                if (x >= 0 && contrast(g, w, x, y, flank)) a++;
                x = right + d;
                if (x < w && contrast(g, w, x, y, flank)) b++;
            }
            if (a >= Math.max(2, Math.round(gap * .18f))
                    && b >= Math.max(2, Math.round(gap * .18f))) return true;
        }
        return false;
    }

    private static boolean contrast(byte[] g, int w, int x, int y, int f) {
        int ink = g[y * w + x] & 255;
        return ink <= 245
                && (g[(y - f) * w + x] & 255) >= ink + 8
                && (g[(y + f) * w + x] & 255) >= ink + 8;
    }
}
