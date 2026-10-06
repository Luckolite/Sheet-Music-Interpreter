// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Bounded raw accent and down-bow shapes whose tips were labeled as heads. */
final class DetachedAnnotationInk {
    private DetachedAnnotationInk() {}

    static boolean accent(
            byte[] gray, int width, int height, int minX, int minY, int maxX, int maxY, float gap) {
        return matches(gray, width, height, minX, minY, maxX, maxY, gap, false);
    }

    static boolean downBow(
            byte[] gray, int width, int height, int minX, int minY, int maxX, int maxY, float gap) {
        return matches(gray, width, height, minX, minY, maxX, maxY, gap, true);
    }

    private static boolean matches(
            byte[] gray,
            int width,
            int height,
            int minX,
            int minY,
            int maxX,
            int maxY,
            float gap,
            boolean bow) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || gap < 5
                || !Float.isFinite(gap)
                || minX < 0
                || minY < 0
                || maxX >= width
                || maxY >= height) return false;
        int left = Math.max(0, Math.round(minX - gap * (bow ? 4 : 3))),
                right = Math.min(width - 1, Math.round(maxX + gap * (bow ? 4 : 3)));
        int top = Math.max(0, Math.round(minY - gap * (bow ? 5 : 2))),
                bottom = Math.min(height - 1, Math.round(maxY + gap * (bow ? .4f : 2)));
        int w = right - left + 1, h = bottom - top + 1;
        int[] queue = null;
        for (int threshold : new int[] {80, 110, 140, 180, 205}) {
            boolean[] seen = new boolean[w * h], rules = new boolean[h];
            if (queue == null) queue = new int[w * h];
            if (!bow)
                for (int y = 0; y < h; y++) {
                    int count = 0;
                    for (int x = left; x <= right; x++)
                        if ((gray[(top + y) * width + x] & 255) <= threshold) count++;
                    rules[y] = count >= w * .9f;
                }
            for (int y = 0; y < h; ) {
                int start = y;
                while (y < h && rules[y]) y++;
                if (y - start > gap * .25f) Arrays.fill(rules, start, y, false);
                if (y == start) y++;
            }
            for (int sy = minY; sy <= maxY; sy++)
                for (int sx = minX; sx <= maxX; sx++) {
                    int seed = (sy - top) * w + sx - left;
                    if (seen[seed]
                            || rules[sy - top]
                            || !ink(gray, width, height, sx, sy, threshold, bow)) continue;
                    int size = 1, take = 0;
                    queue[0] = seed;
                    seen[seed] = true;
                    int l = w, r = -1, t = h, b = -1;
                    while (take < size) {
                        int at = queue[take++], x = at % w, y = at / w;
                        l = Math.min(l, x);
                        r = Math.max(r, x);
                        t = Math.min(t, y);
                        b = Math.max(b, y);
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++) {
                                int xx = x + dx, yy = y + dy;
                                if (xx < 0 || xx >= w || yy < 0 || yy >= h || rules[yy]) continue;
                                int next = yy * w + xx;
                                if (!seen[next]
                                        && ink(
                                                gray, width, height, left + xx, top + yy, threshold,
                                                bow)) {
                                    seen[next] = true;
                                    queue[size++] = next;
                                }
                            }
                    }
                    float gw = r - l + 1, gh = b - t + 1;
                    if (l == 0
                            || r == w - 1
                            || t == 0
                            || b == h - 1
                            || gw < gap * .65f
                            || gw > gap * (bow ? 4.2f : 2.4f)
                            || gh < gap * (bow ? 1.2f : .25f)
                            || gh > gap * (bow ? 4.8f : 1.25f)) continue;
                    if (bow) {
                        if ((minY + maxY) * .5f - top < t + gh * .75f) continue;
                        int hits = 0, interior = 0;
                        boolean[] cap = new boolean[10],
                                legL = new boolean[10],
                                legR = new boolean[10];
                        for (int i = 0; i < size; i++) {
                            float x = (queue[i] % w - l) / Math.max(1, gw - 1),
                                    y = (queue[i] / w - t) / Math.max(1, gh - 1);
                            if (y < .2f || x < .22f || x > .78f) hits++;
                            else interior++;
                            if (y < .2f) cap[Math.min(9, (int) (x * 10))] = true;
                            if (x < .22f) legL[Math.min(9, (int) (y * 10))] = true;
                            if (x > .78f) legR[Math.min(9, (int) (y * 10))] = true;
                        }
                        if (hits >= size * .9f
                                && covered(cap) >= 9
                                && covered(legL) >= 9
                                && covered(legR) >= 9
                                && interior < size * .08f) return true;
                    } else {
                        if (gw < gh * 1.4f) continue;
                        int hits = 0;
                        float low = 1, high = 0;
                        boolean[] coverage = new boolean[10];
                        for (int i = 0; i < size; i++) {
                            float x = (queue[i] % w - l) / Math.max(1, gw - 1),
                                    y = (queue[i] / w - t) / Math.max(1, gh - 1);
                            if (x < .2f) {
                                low = Math.min(low, y);
                                high = Math.max(high, y);
                            }
                            if (Math.abs(Math.abs(y - .5f) - .5f * (1 - x))
                                    < .13f + .6f / Math.min(gw, gh)) {
                                hits++;
                                coverage[Math.min(9, (int) (x * 10))] = true;
                            }
                        }
                        if (high - low > .6f && hits >= size * .8f && covered(coverage) >= 9)
                            return true;
                    }
                }
        }
        return false;
    }

    private static boolean ink(
            byte[] gray, int width, int height, int x, int y, int threshold, boolean thick) {
        if ((gray[y * width + x] & 255) > threshold) return false;
        if (!thick) return true;
        // A thin engraved slur may touch the bottom of a much thicker handwritten
        // bow. Its one-pixel centerline must not enlarge either vertical leg.
        return x > 0
                && x < width - 1
                && y > 0
                && y < height - 1
                && (gray[y * width + x - 1] & 255) <= threshold
                && (gray[y * width + x + 1] & 255) <= threshold
                && (gray[(y - 1) * width + x] & 255) <= threshold
                && (gray[(y + 1) * width + x] & 255) <= threshold;
    }

    private static int covered(boolean[] bins) {
        int result = 0;
        for (boolean b : bins) if (b) result++;
        return result;
    }
}
