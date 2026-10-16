// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Raw continuity from a dot-sized island into an independently proved grace head. */
final class AttachedGraceDotInk {
    static boolean matches(
            byte[] gray,
            int width,
            int height,
            int hl,
            int ht,
            int hr,
            int hb,
            int headArea,
            int dl,
            int dt,
            int dr,
            int db,
            int dotArea,
            float gap,
            int threshold) {
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || !Float.isFinite(gap)
                || gap < 5
                || gap > Math.min(width, height) * .25f
                || headArea < 1
                || dotArea < 1
                || hl < 0
                || ht < 0
                || hr < hl
                || hb < ht
                || hr >= width
                || hb >= height
                || dl < 0
                || dt < 0
                || dr < dl
                || db < dt
                || dr >= width
                || db >= height
                || dl <= hr
                || dl - hr > gap * 1.8f
                || threshold < 1
                || threshold > 180) return false;
        int left = Math.max(0, hl - Math.round(gap * .6f)),
                right = Math.min(width - 1, dr + Math.round(gap * .6f));
        int top = Math.max(0, Math.min(ht, dt) - Math.round(gap * .7f)),
                bottom = Math.min(height - 1, Math.max(hb, db) + Math.round(gap * .7f));
        int w = right - left + 1, h = bottom - top + 1;
        boolean[] rules = new boolean[h];
        for (int y = 0; y < h; y++) {
            int n = 0;
            for (int x = left; x <= right; x++)
                if ((gray[(y + top) * width + x] & 255) < threshold) n++;
            rules[y] = n >= w * .9f;
        }
        for (int y = 0; y < h; ) {
            int start = y;
            while (y < h && rules[y]) y++;
            if (y - start > Math.max(2, Math.round(gap * .3f)))
                java.util.Arrays.fill(rules, start, y, false);
            if (y == start) y++;
        }
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int sy = dt; sy <= db; sy++)
            for (int sx = dl; sx <= dr; sx++) {
                int seed = (sy - top) * w + sx - left;
                if (seen[seed] || rules[sy - top] || (gray[sy * width + sx] & 255) >= threshold)
                    continue;
                int read = 0, write = 1, headInk = 0, dotInk = 0;
                queue[0] = seed;
                seen[seed] = true;
                while (read < write) {
                    int at = queue[read++], x = at % w, y = at / w, px = x + left, py = y + top;
                    if (px >= hl && px <= hr && py >= ht && py <= hb) headInk++;
                    if (px >= dl && px <= dr && py >= dt && py <= db) dotInk++;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (dy != 0)
                                while (xx >= 0 && xx < w && yy >= 0 && yy < h && rules[yy]) {
                                    xx += dx;
                                    yy += dy;
                                }
                            if (xx < 0 || xx >= w || yy < 0 || yy >= h || rules[yy]) continue;
                            int next = yy * w + xx;
                            if (!seen[next]
                                    && (gray[(yy + top) * width + xx + left] & 255) < threshold) {
                                seen[next] = true;
                                queue[write++] = next;
                            }
                        }
                }
                if (headInk >= Math.max(3, Math.round(headArea * .25f))
                        && dotInk >= Math.max(3, Math.round(dotArea * .4f))) return true;
            }
        return false;
    }
}
