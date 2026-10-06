// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

/** Complete long return curves, used only for tiny stemless fragments beside a real note. */
final class LongSlurFragmentInk {
    private LongSlurFragmentInk() {}

    static boolean matches(
            byte[] gray,
            int width,
            int height,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int area,
            float gap) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || gap < 5
                || !Float.isFinite(gap)
                || minX < 0
                || minY < 0
                || maxX >= width
                || maxY >= height
                || area <= 0) return false;
        float cx = (minX + maxX) * .5f, cy = (minY + maxY) * .5f;
        int left = Math.max(0, Math.round(cx - gap * 12)),
                right = Math.min(width - 1, Math.round(cx + gap * 12));
        int top = Math.max(0, Math.round(cy - gap * 4)),
                bottom = Math.min(height - 1, Math.round(cy + gap * 4));
        int w = right - left + 1, h = bottom - top + 1;
        int[] queue = null;
        for (int threshold : new int[] {80, 110, 140}) {
            boolean[] rules = new boolean[h], seen = new boolean[w * h];
            if (queue == null) queue = new int[w * h];
            for (int y = 0; y < h; y++) {
                int count = 0;
                for (int x = left; x <= right; x++)
                    if ((gray[(top + y) * width + x] & 255) <= threshold) count++;
                rules[y] = count >= w * .9f;
            }
            for (int y = 0; y < h; ) {
                int start = y;
                while (y < h && rules[y]) y++;
                if (y - start > Math.max(2, Math.round(gap * .3f)))
                    Arrays.fill(rules, start, y, false);
                if (y == start) y++;
            }
            for (int sy = minY; sy <= maxY; sy++)
                for (int sx = minX; sx <= maxX; sx++) {
                    int seed = (sy - top) * w + sx - left;
                    if (seen[seed] || rules[sy - top] || (gray[sy * width + sx] & 255) > threshold)
                        continue;
                    int end = 1, take = 0;
                    queue[0] = seed;
                    seen[seed] = true;
                    int l = w, r = -1, t = h, b = -1, overlap = 0;
                    int[] counts = new int[w], sums = new int[w];
                    while (take < end) {
                        int at = queue[take++], x = at % w, y = at / w;
                        l = Math.min(l, x);
                        r = Math.max(r, x);
                        t = Math.min(t, y);
                        b = Math.max(b, y);
                        counts[x]++;
                        sums[x] += y;
                        if (x + left >= minX
                                && x + left <= maxX
                                && y + top >= minY
                                && y + top <= maxY) overlap++;
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++) {
                                int xx = x + dx, yy = y + dy;
                                if (xx < 0 || xx >= w || yy < 0 || yy >= h || rules[yy]) continue;
                                int next = yy * w + xx;
                                if (!seen[next]
                                        && (gray[(top + yy) * width + left + xx] & 255)
                                                <= threshold) {
                                    seen[next] = true;
                                    queue[end++] = next;
                                }
                            }
                    }
                    int span = r - l + 1, rise = b - t + 1;
                    if (l == 0
                            || r == w - 1
                            || t == 0
                            || b == h - 1
                            || overlap < area * .4f
                            || span < gap * 3
                            || span > gap * 20
                            || span < rise * 2
                            || rise < gap * .5f
                            || rise > gap * 3.5f) continue;
                    float relative = (cx - left - l) / span;
                    if (relative > .22f && relative < .78f) continue;
                    float[] means = new float[5];
                    int[] bins = new int[5], thickness = new int[span];
                    int used = 0;
                    for (int x = l; x <= r; x++)
                        if (counts[x] > 0) {
                            int bin = Math.min(4, (x - l) * 5 / span);
                            means[bin] += sums[x] / (float) counts[x];
                            bins[bin]++;
                            thickness[used++] = counts[x];
                        }
                    boolean complete = true;
                    for (int i = 0; i < 5; i++) {
                        if (bins[i] == 0) {
                            complete = false;
                            break;
                        }
                        means[i] /= bins[i];
                    }
                    if (!complete) continue;
                    Arrays.sort(thickness, 0, used);
                    if (thickness[used / 2] > gap * .65f
                            || thickness[used * 3 / 4] > gap * .8f
                            || thickness[used - 1] > gap * 1.3f
                            || Math.abs(means[0] - means[4]) > gap * .8f) continue;
                    float bend = means[2] - (means[0] + means[4]) * .5f;
                    if (Math.abs(bend) < gap * .45f) continue;
                    int direction = bend > 0 ? 1 : -1;
                    if ((means[1] - means[0]) * direction < gap * .10f
                            || (means[2] - means[1]) * direction < 0
                            || (means[3] - means[4]) * direction < gap * .10f
                            || (means[2] - means[3]) * direction < 0) continue;
                    return true;
                }
        }
        return false;
    }
}
