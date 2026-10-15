// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Repeated curved enclosed counters identify rounded stacked meter digits, not a double bar. */
final class RoundedMeterCounters {
    private record Pocket(float x, float y, int left, int right, boolean curved) {}

    static boolean matches(
            byte[] gray, int width, int height, int column, int top, int bottom, float gap) {
        if (gray == null || gray.length != (long) width * height || gap < 6) return false;
        int left = Math.max(0, Math.round(column - gap * 1.5f)),
                right = Math.min(width - 1, Math.round(column + gap * 1.5f));
        int first = Math.max(0, Math.round(top - gap * .2f)),
                last = Math.min(height - 1, Math.round(bottom + gap * .2f));
        int w = right - left + 1, h = last - first + 1;
        if (w < 5 || h < 10) return false;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        var pockets = new ArrayList<Pocket>();
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || (gray[(first + seed / w) * width + left + seed % w] & 255) <= 160)
                continue;
            int read = 0, write = 1;
            queue[0] = seed;
            seen[seed] = true;
            int minX = w, maxX = -1, minY = h, maxY = -1;
            int[] rows = new int[h];
            while (read < write) {
                int at = queue[read++], x = at % w, y = at / w;
                rows[y]++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                        int n = yy * w + xx;
                        if (!seen[n] && (gray[(first + yy) * width + left + xx] & 255) > 160) {
                            seen[n] = true;
                            queue[write++] = n;
                        }
                    }
            }
            int cw = maxX - minX + 1, ch = maxY - minY + 1;
            if (minX == 0
                    || maxX == w - 1
                    || minY == 0
                    || maxY == h - 1
                    || cw < gap * .2f
                    || cw > gap * 1.1f
                    || ch < gap * .25f
                    || ch > gap * .9f
                    || write < gap * gap * .06f
                    || write > gap * gap * .65f) continue;
            int peak = 0;
            for (int y = minY + 1; y < maxY; y++) peak = Math.max(peak, rows[y]);
            boolean curved =
                    peak - Math.min(rows[minY], rows[maxY]) >= Math.max(3, Math.round(gap * .18f));
            pockets.add(
                    new Pocket(
                            left + (minX + maxX) * .5f,
                            first + (minY + maxY) * .5f,
                            left + minX,
                            left + maxX,
                            curved));
        }
        if (pockets.size() < 3
                || pockets.size() > 5
                || pockets.stream().filter(Pocket::curved).count() < 3) return false;
        float minX = Float.MAX_VALUE, maxX = -1, minY = Float.MAX_VALUE, maxY = -1;
        for (var p : pockets) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minY = Math.min(minY, p.y());
            maxY = Math.max(maxY, p.y());
        }
        float middle = (top + bottom) * .5f;
        return maxX - minX <= gap * .5f
                && maxY - minY >= gap * 2
                && minY < middle - gap * .4f
                && maxY > middle + gap * .4f
                && Math.abs((minX + maxX) * .5f - column) <= gap * .85f;
    }
}
