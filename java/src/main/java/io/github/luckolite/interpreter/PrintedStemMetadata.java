// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Source-only shaft direction for serialization; never selects a rhythmic or performance voice. */
final class PrintedStemMetadata {
    private PrintedStemMetadata() {}

    static int detect(
            byte[] gray,
            int w,
            int h,
            int left,
            int right,
            int top,
            int bottom,
            float centerX,
            float centerY,
            float gap,
            float staffSlope) {
        if (gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || left < 0
                || right >= w
                || top < 0
                || bottom >= h
                || right < left
                || bottom < top
                || !Float.isFinite(centerX)
                || !Float.isFinite(centerY)
                || centerX < left
                || centerX > right
                || centerY < top
                || centerY > bottom
                || !Float.isFinite(gap)
                || gap < 4
                || gap > Math.min(w, h) * .25f
                || !Float.isFinite(staffSlope)
                || Math.abs(staffSlope) > .35f) return 0;
        boolean up = shaft(gray, w, h, right, centerY, gap, staffSlope, -1),
                down = shaft(gray, w, h, left, centerY, gap, staffSlope, 1);
        return up == down ? 0 : up ? 1 : -1;
    }

    private static boolean shaft(
            byte[] gray, int w, int h, int edge, float y, float gap, float slope, int direction) {
        int margin = Math.max(2, Math.round(gap * .22f)),
                flank = Math.max(3, Math.round(gap * .4f));
        int start = Math.max(2, Math.round(gap * .25f)), length = Math.round(gap * 2.25f);
        for (int origin = edge - margin; origin <= edge + margin; origin++) {
            int hits = 0, narrow = 0, total = 0, blank = 0;
            boolean disconnected = false;
            for (int d = start; d <= length; d++) {
                int row = Math.round(y + direction * d),
                        x = Math.round(origin - slope * direction * d);
                if (row < 1 || row >= h - 1 || x - flank < 0 || x + flank >= w) {
                    disconnected = true;
                    break;
                }
                int radius = Math.max(4, Math.round(gap * .65f)),
                        half = Math.max(2, Math.round(gap * .3f));
                int[] values = new int[(radius * 2 + 1) * (half * 2 + 1)];
                int n = 0;
                for (int yy = Math.max(0, row - half); yy <= Math.min(h - 1, row + half); yy++)
                    for (int xx = Math.max(0, x - radius); xx <= Math.min(w - 1, x + radius); xx++)
                        values[n++] = gray[yy * w + xx] & 255;
                Arrays.sort(values, 0, n);
                int paper = values[(n - 1) * 85 / 100], core = 255;
                for (int xx = x - 1; xx <= x + 1; xx++)
                    core = Math.min(core, gray[row * w + xx] & 255);
                total++;
                boolean ink = paper - core >= 24;
                if (ink) {
                    hits++;
                    blank = 0;
                    int l = gray[row * w + x - flank] & 255, r = gray[row * w + x + flank] & 255;
                    int contrast = Math.max(16, Math.round((paper - core) * .3f));
                    if (l - core >= contrast && r - core >= contrast) narrow++;
                } else if (++blank > Math.max(1, Math.round(gap * .12f))) {
                    disconnected = true;
                    break;
                }
            }
            if (!disconnected
                    && total >= gap * 1.9f
                    && hits >= total * .92f
                    && narrow >= total * .6f) return true;
        }
        return false;
    }
}
