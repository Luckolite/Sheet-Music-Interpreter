// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A whole oval has an upright, forward-leaning enclosed opening. */
final class WholeHeadPocketEvidence {
    static boolean proved(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float gap) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || !Float.isFinite(gap)
                || gap < 6
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || right < left
                || bottom < top) return false;
        int w = right - left + 1, h = bottom - top + 1;
        if (w < gap * 1.35f || w > gap * 2.4f || h < gap * .65f || h > gap * 1.4f) return false;
        return pocket(gray, width, left, top, w, h, gap, 170)
                && pocket(gray, width, left, top, w, h, gap, 205);
    }

    private static boolean pocket(
            byte[] gray, int stride, int left, int top, int w, int h, float gap, int threshold) {
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        int total = 0, parts = 0;
        for (int seed = 0; seed < w * h; seed++) {
            if (seen[seed] || (gray[(top + seed / w) * stride + left + seed % w] & 255) < threshold)
                continue;
            int read = 0, end = 1;
            queue[0] = seed;
            seen[seed] = true;
            boolean edge = false;
            while (read < end) {
                int p = queue[read++], x = p % w, y = p / w;
                edge |= x == 0 || x == w - 1 || y == 0 || y == h - 1;
                for (int delta : new int[] {-1, 1, -w, w}) {
                    int n = p + delta;
                    if (n < 0
                            || n >= w * h
                            || Math.abs(n % w - x) + Math.abs(n / w - y) != 1
                            || seen[n]) continue;
                    if ((gray[(top + n / w) * stride + left + n % w] & 255) >= threshold) {
                        seen[n] = true;
                        queue[end++] = n;
                    }
                }
            }
            if (edge || end < Math.max(2, gap * gap * .025f)) continue;
            parts++;
            for (int i = 0; i < end; i++) {
                int x = queue[i] % w, y = queue[i] / w;
                total++;
                sx += x;
                sy += y;
                sxx += x * x;
                syy += y * y;
                sxy += x * y;
            }
        }
        if (parts < 1 || parts > 3 || total < gap * gap * .12f || total > gap * gap * .9f)
            return false;
        double mx = sx / total,
                my = sy / total,
                vx = sxx / total - mx * mx,
                vy = syy / total - my * my,
                cov = sxy / total - mx * my;
        return mx > w * .25
                && mx < w * .75
                && my > h * .2
                && my < h * .8
                && vx > 0
                && vy >= vx * .7
                && cov > Math.sqrt(vx * vy) * .12;
    }
}
