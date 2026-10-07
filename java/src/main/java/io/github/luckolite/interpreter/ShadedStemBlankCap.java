// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** A complete local-paper cap separates a shaft from nearby detached printed ink. */
final class ShadedStemBlankCap {
    private ShadedStemBlankCap() {}

    static int endpoint(
            byte[] gray,
            int width,
            int height,
            int x,
            float headY,
            int end,
            int side,
            float gap,
            int inkLimit) {
        if (gray == null
                || gray.length != (long) width * height
                || gap < 8
                || !Float.isFinite(gap)
                || !Float.isFinite(headY)
                || side != -1 && side != 1) return end;
        int origin = Math.round(headY), length = (end - origin) * side;
        int half = Math.max(1, Math.round(gap * .12f)), margin = Math.round(gap * 1.2f);
        if (length < gap * 2.7f
                || length > gap * 6f
                || x - margin < 0
                || x + margin >= width
                || origin < 0
                || origin >= height
                || end < 0
                || end >= height) return end;
        int[] row = new int[margin * 2 + 1];
        int strong = 0, examined = 0, cap = 0;
        for (int distance = 0; distance <= length; distance++) {
            int y = origin + side * distance;
            for (int i = 0; i < row.length; i++) row[i] = gray[y * width + x - margin + i] & 255;
            Arrays.sort(row);
            int paper = row[(row.length - 1) * 3 / 4];
            int minimum = 255;
            boolean clear = paper >= 96 && paper <= 200;
            for (int xx = x - half; xx <= x + half; xx++) {
                int value = gray[y * width + xx] & 255;
                minimum = Math.min(minimum, value);
                if (value < 90 || paper - value > 8) clear = false;
            }
            if (distance < gap * 2.3f) {
                examined++;
                if (minimum < Math.min(inkLimit, paper - 20) || paper < 96 && minimum < inkLimit)
                    strong++;
            }
            if (distance < gap * 2.3f || !clear) {
                cap = 0;
                continue;
            }
            cap++;
            if (cap < 2 || strong < examined * .7f) continue;
            int before = origin + side * (distance - cap);
            int separation = (end - before) * side;
            if (separation < gap * .35f || separation > gap * 1.2f) continue;
            int detached = 0;
            for (int d = distance + 1; d <= length; d++) {
                int yy = origin + side * d;
                int dark = 0;
                for (int xx = x - half; xx <= x + half; xx++)
                    if ((gray[yy * width + xx] & 255) < inkLimit) dark++;
                if (dark >= half * 2) detached++;
            }
            if (detached >= 2) return before;
        }
        return end;
    }
}
