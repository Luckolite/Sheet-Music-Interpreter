// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Raw compact rising grace hook. Call only after a stemmed grace on its left and
 * independently stemmed principal note on its right have established ownership. */
final class GraceConnectorInk {
    private GraceConnectorInk() {}

    static boolean isHook(
            byte[] gray,
            int width,
            int height,
            int minX,
            int minY,
            int maxX,
            int maxY,
            float centerX,
            float centerY,
            int area,
            float gap,
            int inkThreshold) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || gap < 5
                || !Float.isFinite(gap)
                || area <= 0
                || minX < 0
                || minY < 0
                || maxX >= width
                || maxY >= height) return false;
        int left = Math.max(0, Math.round(centerX - gap * 2)),
                right = Math.min(width - 1, Math.round(centerX + gap * 2));
        int top = Math.max(0, Math.round(centerY - gap * 1.2f)),
                bottom = Math.min(height - 1, Math.round(centerY + gap * 1.2f));
        int w = right - left + 1, h = bottom - top + 1;
        boolean[] rules = new boolean[h];
        for (int y = 0; y < h; y++) {
            int count = 0;
            for (int x = left; x <= right; x++)
                if ((gray[(top + y) * width + x] & 255) <= inkThreshold) count++;
            rules[y] = count >= w * .9f;
        }
        for (int y = 0; y < h; ) {
            int start = y;
            while (y < h && rules[y]) y++;
            if (y - start > Math.max(2, Math.round(gap * .3f))) Arrays.fill(rules, start, y, false);
            if (y == start) y++;
        }
        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        for (int seed = 0; seed < seen.length; seed++) {
            int sx = seed % w, sy = seed / w;
            if (seen[seed]
                    || rules[sy]
                    || (gray[(top + sy) * width + left + sx] & 255) > inkThreshold) continue;
            int end = 1;
            stack[0] = seed;
            seen[seed] = true;
            int l = w, r = -1, t = h, b = -1, overlap = 0;
            int[] counts = new int[w], sums = new int[w];
            while (end > 0) {
                int at = stack[--end], x = at % w, y = at / w;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                counts[x]++;
                sums[x] += y;
                if (x + left >= minX && x + left <= maxX && y + top >= minY && y + top <= maxY)
                    overlap++;
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
                                && (gray[(top + yy) * width + left + xx] & 255) <= inkThreshold) {
                            seen[next] = true;
                            stack[end++] = next;
                        }
                    }
            }
            int span = r - l + 1, rise = b - t + 1;
            if (l == 0
                    || r == w - 1
                    || t == 0
                    || b == h - 1
                    || overlap < area * .45f
                    || span < gap * .75f
                    || span > gap * 1.8f
                    || span < (maxX - minX + 1) * .95f
                    || rise < gap * .4f
                    || rise > gap * 1.2f) continue;
            float[] means = new float[3];
            int[] bins = new int[3], thickness = new int[span];
            int used = 0;
            for (int x = l; x <= r; x++)
                if (counts[x] > 0) {
                    int bin = Math.min(2, (x - l) * 3 / span);
                    means[bin] += sums[x] / (float) counts[x];
                    bins[bin]++;
                    thickness[used++] = counts[x];
                }
            if (bins[0] == 0 || bins[1] == 0 || bins[2] == 0) continue;
            for (int i = 0; i < 3; i++) means[i] /= bins[i];
            Arrays.sort(thickness, 0, used);
            // A thin hook rises toward its principal note and curves more sharply
            // at that end. Filled/hollow ovals and straight strokes do not have
            // this asymmetric centreline even when their mask is fragmented.
            if (thickness[used / 2] > gap * .45f
                    || thickness[used * 3 / 4] > gap * .55f
                    || means[0] - means[2] < gap * .18f
                    || means[0] <= means[1]
                    || means[1] <= means[2]
                    || means[1] - (means[0] + means[2]) * .5f < Math.max(.35f, gap * .025f))
                continue;
            return true;
        }
        return false;
    }
}
