// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

final class ShadedLedgerRule {
    static boolean shaded(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float gap) {
        int[] values = new int[256];
        int count = 0, r = Math.max(3, Math.round(gap));
        for (int y = Math.max(0, top - r); y <= Math.min(height - 1, bottom + r); y += 3)
            for (int x = Math.max(0, left - r); x <= Math.min(width - 1, right + r); x += 3) {
                values[gray[y * width + x] & 255]++;
                count++;
            }
        int seen = 0;
        for (int i = 0; i < 256; i++) if ((seen += values[i]) >= count * .9f) return i < 185;
        return false;
    }

    static boolean thin(byte[] gray, int width, int height, int x, int y, float gap) {
        int probe = Math.max(2, Math.round(gap * .32f));
        if (y - probe < 0 || y + probe >= height) return false;
        int ink = 255;
        for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++)
            ink = Math.min(ink, gray[yy * width + x] & 255);
        return (gray[(y - probe) * width + x] & 255) >= ink + 8
                && (gray[(y + probe) * width + x] & 255) >= ink + 8;
    }
}
