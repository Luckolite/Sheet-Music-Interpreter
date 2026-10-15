// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Reject horizontal staff-rule edges as evidence that a pale flag touches its stem. */
final class FlagRootStaffRule {
    static boolean matches(
            byte[] gray, int width, int height, int y, int stemX, float gap, int threshold) {
        if (gray == null || gap < 4) return false;
        int radius = Math.max(1, (int) Math.floor(gap * .08f));
        for (int direction : new int[] {-1, 1}) {
            int samples = 0, ink = 0;
            for (int dx = Math.round(gap * 2); dx <= Math.round(gap * 4); dx++) {
                int x = stemX + direction * dx;
                if (x < 0 || x >= width) continue;
                samples++;
                boolean dark = false;
                for (int row = Math.max(0, y - radius);
                        row <= Math.min(height - 1, y + radius);
                        row++)
                    if ((gray[row * width + x] & 255) <= threshold) {
                        dark = true;
                        break;
                    }
                if (dark) ink++;
            }
            if (samples < Math.max(6, Math.round(gap)) || ink < samples * .85f) return false;
        }
        return true;
    }
}
