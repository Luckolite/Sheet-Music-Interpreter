// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Complete broad handwritten two: upper arch, right middle, and lower returning diagonal. */
final class HandwrittenTwoHeadInk {
    private HandwrittenTwoHeadInk() {}

    static boolean matches(
            byte[] gray, int width, int height, int minX, int minY, int maxX, int maxY, float gap) {
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
        int left = Math.max(0, Math.round(minX - gap * 4)),
                right = Math.min(width - 1, Math.round(maxX + gap * 4));
        int top = Math.max(0, Math.round(minY - gap * 4)),
                bottom = Math.min(height - 1, Math.round(maxY + gap * 2));
        int w = right - left + 1, h = bottom - top + 1;
        int[] queue = null;
        for (int threshold : new int[] {65, 80, 110, 140}) {
            boolean[] seen = new boolean[w * h];
            if (queue == null) queue = new int[w * h];
            for (int sy = minY; sy <= maxY; sy++)
                for (int sx = minX; sx <= maxX; sx++) {
                    int seed = (sy - top) * w + sx - left;
                    if (seen[seed] || (gray[sy * width + sx] & 255) > threshold) continue;
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
                                if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                                int next = yy * w + xx;
                                if (!seen[next]
                                        && (gray[(top + yy) * width + left + xx] & 255)
                                                <= threshold) {
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
                            || gw < gap * 1.4f
                            || gw > gap * 4
                            || gh < gap * 1.6f
                            || gh > gap * 4.5f
                            || gw / gh < .65f
                            || gw / gh > 1.4f
                            || size > gw * gh * .7f
                            || (minY + maxY) * .5f - top < t + gh * .6f) continue;
                    float[] means = new float[5], lo = {1, 1, 1, 1, 1}, hi = new float[5];
                    int[] counts = new int[5];
                    for (int i = 0; i < size; i++) {
                        int at = queue[i], bin = Math.min(4, (at / w - t) * 5 / (b - t + 1));
                        float x = (at % w - l) / gw;
                        means[bin] += x;
                        counts[bin]++;
                        lo[bin] = Math.min(lo[bin], x);
                        hi[bin] = Math.max(hi[bin], x);
                    }
                    boolean complete = true;
                    for (int i = 0; i < 5; i++) {
                        if (counts[i] == 0) {
                            complete = false;
                            break;
                        }
                        means[i] /= counts[i];
                    }
                    if (!complete
                            || hi[0] - lo[0] < .6f
                            || lo[0] > .3f
                            || lo[2] < .4f
                            || lo[3] > .3f
                            || hi[4] - lo[4] < .75f) continue;
                    if (means[1] - means[0] > .18f
                            && means[1] > .65f
                            && means[2] > .56f
                            && means[2] - means[3] > .13f
                            && means[4] < .64f) return true;
                }
        }
        return false;
    }
}
