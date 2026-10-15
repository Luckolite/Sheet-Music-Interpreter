// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Local meter geometry requires five parallel thin printed rails on the same clear strip. */
final class LocalMeterStaffWindows {
    record Window(int left, int right, int shift) {}

    static List<Window> detect(
            byte[] gray, int width, int height, int[] rows, float gap, float slope) {
        var out = new ArrayList<Window>();
        if (gray == null
                || gray.length != width * height
                || rows.length != 5
                || gap < 6
                || !Float.isFinite(gap)) return out;
        int radius = Math.max(1, Math.round(gap * .55f)),
                flank = Math.max(2, Math.round(gap * .24f));
        int[] shifts = new int[width];
        Arrays.fill(shifts, Integer.MIN_VALUE);
        for (int x = 0; x < width; x++) {
            int best = 0, selected = Integer.MIN_VALUE;
            int tilt = Math.round(slope * (x - width * .5f));
            for (int d = -radius; d <= radius; d++) {
                int score = 0;
                boolean valid = true;
                for (int row : rows) {
                    int y = row + d + tilt;
                    if (y - flank < 0 || y + flank >= height) {
                        valid = false;
                        break;
                    }
                    int c = gray[y * width + x] & 255,
                            a = gray[(y - flank) * width + x] & 255,
                            b = gray[(y + flank) * width + x] & 255;
                    if (c > 190 || a < 200 || b < 200 || Math.min(a, b) - c < 35) {
                        valid = false;
                        break;
                    }
                    score += Math.min(a, b) - c;
                }
                if (valid && score > best) {
                    best = score;
                    selected = d;
                }
            }
            shifts[x] = selected;
        }
        int minimum = Math.max(5, Math.round(gap * .65f)), pad = Math.round(gap * 3);
        for (int x = 0; x < width; ) {
            if (shifts[x] == Integer.MIN_VALUE) {
                x++;
                continue;
            }
            int left = x, shift = shifts[x], total = 0, n = 0;
            while (x < width
                    && shifts[x] != Integer.MIN_VALUE
                    && Math.abs(shifts[x] - shift) <= 1) {
                total += shifts[x];
                n++;
                x++;
            }
            int mean = Math.round(total / (float) n);
            if (n >= minimum && Math.abs(mean) > Math.max(1, Math.round(gap * .12f)))
                out.add(new Window(Math.max(0, left - pad), Math.min(width, x + pad), mean));
        }
        return out;
    }
}
