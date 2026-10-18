// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Requires two separately inked straight rules through all four staff spaces. */
final class ParallelStaffRules {
    static boolean proved(
            byte[] gray, int width, int height, int column, int top, int bottom, float gap) {
        if (gray == null || gap < 4 || bottom - top < gap * 3.5f) return false;
        int radius = Math.max(2, Math.round(gap * 1.2f));
        for (int a = Math.max(0, column - radius); a <= Math.min(width - 1, column + 2); a++) {
            if (!full(gray, width, height, a, top, bottom, gap)) continue;
            for (int b = a + Math.max(3, Math.round(gap * .3f));
                    b <= Math.min(width - 1, a + radius);
                    b++) {
                if (Math.min(Math.abs(a - column), Math.abs(b - column)) > 2
                        || !full(gray, width, height, b, top, bottom, gap)) continue;
                int samples = 0, clear = 0;
                for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++) {
                    if (ruleRow(y, top, gap)) continue;
                    samples++;
                    int longest = 0, run = 0;
                    for (int x = a + 1; x < b; x++) {
                        if ((gray[y * width + x] & 255) >= 200) {
                            run++;
                            longest = Math.max(longest, run);
                        } else run = 0;
                    }
                    if (longest >= Math.max(2, Math.round(gap * .12f))) clear++;
                }
                if (samples >= gap && clear >= samples * .85f) return true;
            }
        }
        return false;
    }

    static boolean proved(
            byte[] gray,
            int width,
            int height,
            int column,
            int top,
            int bottom,
            float gap,
            float[] physicalRows) {
        if (gray == null || gap < 4 || bottom - top < gap * 3.5f) return false;
        if (!registeredFrame(physicalRows, top, bottom, gap, height)) return false;
        int radius = Math.max(2, Math.round(gap * 1.2f));
        for (int a = Math.max(0, column - radius); a <= Math.min(width - 1, column + 2); a++) {
            if (!registeredFull(gray, width, height, a, physicalRows)) continue;
            for (int b = a + Math.max(3, Math.round(gap * .3f));
                    b <= Math.min(width - 1, a + radius);
                    b++) {
                if (Math.min(Math.abs(a - column), Math.abs(b - column)) > 2
                        || !registeredFull(gray, width, height, b, physicalRows)) continue;
                int samples = 0, clear = 0;
                for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++) {
                    if (registeredRuleRow(y, physicalRows)) continue;
                    samples++;
                    int longest = 0, run = 0;
                    for (int x = a + 1; x < b; x++) {
                        if ((gray[y * width + x] & 255) >= 200) {
                            run++;
                            longest = Math.max(longest, run);
                        } else run = 0;
                    }
                    if (longest >= Math.max(2, Math.round(gap * .12f))) clear++;
                }
                if (samples >= gap && clear >= samples * .85f) return true;
            }
        }
        return false;
    }

    private static boolean registeredFrame(float[] r, int top, int bottom, float gap, int height) {
        if (r == null || r.length != 5) return false;
        for (int i = 0; i < 5; i++)
            if (!Float.isFinite(r[i]) || i > 0 && r[i] <= r[i - 1]) return false;
        return Math.max(0, Math.round(r[0] - gap * .12f)) == top
                && Math.min(height - 1, Math.round(r[4] + gap * .12f)) == bottom;
    }

    static boolean registeredRuleRow(int y, float[] r) {
        int i = 0;
        while (i < 3 && y > r[i + 1]) i++;
        float phase = i + (y - r[i]) / (r[i + 1] - r[i]);
        return Math.abs(phase - Math.round(phase)) <= .27f;
    }

    private static boolean registeredFull(byte[] g, int w, int h, int x, float[] r) {
        for (int space = 0; space < 4; space++) {
            int samples = 0, ink = 0;
            float delta = r[space + 1] - r[space];
            for (int y = Math.max(0, Math.round(r[space] + .28f * delta));
                    y <= Math.min(h - 1, Math.round(r[space] + .72f * delta));
                    y++) {
                samples++;
                if ((g[y * w + x] & 255) <= 160) ink++;
            }
            if (samples < 2 || ink < samples * .9f) return false;
        }
        return true;
    }

    private static boolean ruleRow(int y, int top, float gap) {
        float phase = (y - top) / gap;
        return Math.abs(phase - Math.round(phase)) <= .27f;
    }

    private static boolean full(byte[] g, int w, int h, int x, int top, int bottom, float gap) {
        for (int space = 0; space < 4; space++) {
            int samples = 0, ink = 0;
            for (int y = Math.max(0, Math.round(top + (space + .28f) * gap));
                    y <= Math.min(h - 1, Math.round(top + (space + .72f) * gap));
                    y++) {
                samples++;
                if ((g[y * w + x] & 255) <= 160) ink++;
            }
            if (samples < 2 || ink < samples * .9f) return false;
        }
        return true;
    }
}
