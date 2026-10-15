// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Restricts textured margins only when sustained printed staff rules prove their extent. */
final class PrintedStaffExtent {
    interface Rule {
        float at(int line, int x);
    }

    static int left(byte[] gray, int width, int height, int left, int right, float gap, Rule rule) {
        return edge(gray, width, height, left, right, gap, rule, false);
    }

    static int right(
            byte[] gray, int width, int height, int left, int right, float gap, Rule rule) {
        return edge(gray, width, height, left, right, gap, rule, true);
    }

    private static int edge(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float gap,
            Rule rule,
            boolean fromRight) {
        int original = fromRight ? right : left;
        if (gray == null
                || gray.length != (long) width * height
                || gap < 6
                || left < 0
                || right >= width
                || right <= left) return original;
        int radius = Math.max(1, Math.round(gap * .15f)),
                flank = Math.max(2, Math.round(gap * .35f)),
                needed = Math.max(24, Math.round(gap * 4)),
                run = 0,
                first = -1;
        for (int offset = 0; offset <= right - left; offset++) {
            int x = fromRight ? right - offset : left + offset;
            int hits = 0;
            for (int line = 0; line < 5; line++) {
                int center = Math.round(rule.at(line, x));
                boolean found = false;
                for (int y = Math.max(flank, center - radius);
                        y <= Math.min(height - flank - 1, center + radius);
                        y++) {
                    int ink = gray[y * width + x] & 255;
                    if (ink <= 250
                            && (gray[(y - flank) * width + x] & 255) >= ink + 4
                            && (gray[(y + flank) * width + x] & 255) >= ink + 4) {
                        found = true;
                        break;
                    }
                }
                if (found) hits++;
            }
            run = hits >= 3 ? run + 1 : 0;
            if (run >= needed) {
                first = fromRight ? x + run - 1 : x - run + 1;
                break;
            }
        }
        int result = first + (fromRight ? 1 : -1) * Math.round(gap * 2);
        if (first < 0 || (fromRight ? right - result : result - left) < gap * 4) return original;
        // No negative conclusion from clean or merely faded paper. Require distributed texture.
        int size = Math.max(4, Math.round(gap)), total = 0, textured = 0;
        int marginLeft = fromRight ? result : left, marginRight = fromRight ? right : result;
        for (int x = marginLeft; x + size < marginRight; x += size) {
            int top = Math.round(rule.at(0, x)), bottom = Math.round(rule.at(4, x));
            for (int y = Math.max(0, top); y + size <= Math.min(height, bottom + 1); y += size) {
                long sum = 0, squares = 0;
                int count = 0;
                int[] tones = new int[size * size];
                for (int yy = y; yy < y + size; yy++)
                    for (int xx = x; xx < x + size; xx++) {
                        // Interrupted or faded staff rules are notation, not artwork texture.
                        // Measure distributed variation only between the predicted rule bands.
                        boolean onRule = false;
                        for (int line = 0; line < 5; line++)
                            if (Math.abs(yy - rule.at(line, xx)) <= radius + 1) {
                                onRule = true;
                                break;
                            }
                        if (onRule) continue;
                        int v = gray[yy * width + xx] & 255;
                        tones[count] = v;
                        sum += v;
                        squares += (long) v * v;
                        count++;
                    }
                if (count < size) continue;
                double mean = sum / (double) count;
                // Black notation on flat paper is not distributed artwork texture.
                // Require variation through the middle half of the paper tones.
                java.util.Arrays.sort(tones, 0, count);
                int lower = tones[(count - 1) / 4],
                        median = tones[(count - 1) / 2],
                        upper = tones[(count - 1) * 3 / 4];
                if (squares / (double) count - mean * mean > 25
                        && median < 245
                        && lower > 96
                        && upper - lower >= 4) textured++;
                total++;
            }
        }
        return textured >= 8 && textured >= total * .2 ? result : original;
    }
}
