// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A separated terminal stroke must end a previously proved octave dash chain. */
final class OctaveBracketEnd {
    private OctaveBracketEnd() {}

    static int direction(
            byte[] gray,
            int width,
            int height,
            float dashRight,
            float top,
            float bottom,
            float gap) {
        if (gray == null
                || width < 4
                || height < 4
                || gray.length != (long) width * height
                || !Float.isFinite(dashRight + top + bottom + gap)
                || gap < 6
                || dashRight < 0) return 0;
        int end = Math.round(dashRight - gap * .55f);
        int x0 = Math.max(1, end + 2), x1 = Math.min(width - 2, end + Math.round(gap * 1.6f));
        int y0 = Math.max(1, Math.round(top - gap * .15f)),
                y1 = Math.min(height - 2, Math.round(bottom + gap * .4f));
        if (end < 3 || end >= width || x1 <= x0 || y1 <= y0) return 0;
        int rw = x1 - x0 + 1, rh = y1 - y0 + 1;
        boolean[] seen = new boolean[rw * rh];
        int[] queue = new int[seen.length];
        int result = 0;
        for (int origin = 0; origin < seen.length; origin++) {
            if (seen[origin] || (gray[(y0 + origin / rw) * width + x0 + origin % rw] & 255) >= 165)
                continue;
            int at = 0, count = 1, l = rw, r = -1, t = rh, b = -1;
            boolean clipped = false;
            queue[0] = origin;
            seen[origin] = true;
            while (at < count) {
                int p = queue[at++], x = p % rw, y = p / rw;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                clipped |= x == 0 || x == rw - 1 || y == 0 || y == rh - 1;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= rw || yy < 0 || yy >= rh) continue;
                        int n = yy * rw + xx;
                        if (!seen[n] && (gray[(y0 + yy) * width + x0 + xx] & 255) < 165) {
                            seen[n] = true;
                            queue[count++] = n;
                        }
                    }
            }
            int cw = r - l + 1, ch = b - t + 1;
            if (clipped
                    || cw > Math.max(2, Math.round(gap * .3f))
                    || ch < Math.max(4, Math.ceil(gap * .35f))
                    || ch > gap * 1.3f) continue;
            int spine = 0;
            for (int x = l; x <= r; x++) {
                int n = 0;
                for (int y = t; y <= b; y++) if ((gray[(y0 + y) * width + x0 + x] & 255) < 165) n++;
                spine = Math.max(spine, n);
            }
            if (spine < ch * .75f) continue;
            boolean paper = true;
            // Keep one pixel for antialiasing; independently require a complete paper halo.
            for (int y = y0 + t - 2; y <= y0 + b + 2; y++)
                for (int x = x0 + l - 2; x <= x0 + r + 2; x++)
                    if ((x == x0 + l - 2 || x == x0 + r + 2 || y == y0 + t - 2 || y == y0 + b + 2)
                            && (gray[y * width + x] & 255) < 225) paper = false;
            if (!paper) continue;
            int direction = 0;
            for (int y = y0; y <= y1; y++) {
                if ((gray[y * width + end] & 255) >= 165
                        || (gray[y * width + end - 2] & 255) >= 165) continue;
                int up = y - (y0 + t), down = y0 + b - y;
                int d =
                        up >= Math.max(4, gap * .35f)
                                        && down >= -1
                                        && down <= Math.max(1, gap * .12f)
                                        && up > down * 2 + 1
                                ? -1
                                : down >= Math.max(4, gap * .35f)
                                                && up >= -1
                                                && up <= Math.max(1, gap * .12f)
                                                && down > up * 2 + 1
                                        ? 1
                                        : 0;
                if (d != 0) {
                    if (direction != 0 && direction != d) return 0;
                    direction = d;
                }
            }
            if (direction != 0) {
                if (result != 0) return 0;
                result = direction;
            }
        }
        return result;
    }
}
