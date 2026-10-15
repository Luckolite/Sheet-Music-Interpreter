// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** A coda has a crossed ring enclosing four curved white quadrants. */
final class CodaGlyphInk {
    static boolean matches(
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            float gap) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || right <= left
                || bottom <= top
                || !Float.isFinite(gap)
                || gap < 5) return false;
        int w = right - left + 1, h = bottom - top + 1;
        if (w < gap
                || w > gap * 3.5f
                || h < gap * 1.6f
                || h > gap * 4
                || w / (float) h < .65f
                || w / (float) h > 1.15f
                || (long) w * h > 65536) return false;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        int holes = 0, quadrants = 0;
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || (gray[(top + seed / w) * width + left + seed % w] & 255) < 175)
                continue;
            int count = 1, take = 0, l = w, r = 0, t = h, b = 0;
            boolean border = false;
            long sx = 0, sy = 0;
            seen[seed] = true;
            queue[0] = seed;
            while (take < count) {
                int k = queue[take++], x = k % w, y = k / w;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                sx += x;
                sy += y;
                border |= x == 0 || y == 0 || x == w - 1 || y == h - 1;
                for (int dir = 0; dir < 4; dir++) {
                    int xx = x + (dir == 0 ? -1 : dir == 1 ? 1 : 0),
                            yy = y + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                    int n = yy * w + xx;
                    if (!seen[n] && (gray[(top + yy) * width + left + xx] & 255) >= 175) {
                        seen[n] = true;
                        queue[count++] = n;
                    }
                }
            }
            if (border) continue;
            if (count < w * h * .015f) return false;
            if (++holes > 4
                    || count > w * h * .15f
                    || l < w * .15f
                    || r > w * .85f
                    || t < h * .15f
                    || b > h * .85f) return false;
            float fill = count / (float) ((r - l + 1) * (b - t + 1));
            if (fill < .35f || fill > .94f) return false;
            float cx = sx / (float) count, cy = sy / (float) count;
            if (Math.abs(cx - (w - 1) * .5f) < w * .035f
                    || Math.abs(cy - (h - 1) * .5f) < h * .035f) return false;
            int quadrant = (cx > (w - 1) * .5f ? 1 : 0) + (cy > (h - 1) * .5f ? 2 : 0);
            if ((quadrants & (1 << quadrant)) != 0) return false;
            quadrants |= 1 << quadrant;
        }
        if (holes != 4 || quadrants != 15) return false;
        int vertical = 0, horizontal = 0;
        for (int x = Math.max(0, w / 2 - 2); x <= Math.min(w - 1, w / 2 + 2); x++) {
            int ink = 0;
            for (int y = 0; y < h; y++) if ((gray[(top + y) * width + left + x] & 255) < 175) ink++;
            vertical = Math.max(vertical, ink);
        }
        for (int y = Math.max(0, h / 2 - 2); y <= Math.min(h - 1, h / 2 + 2); y++) {
            int ink = 0;
            for (int x = 0; x < w; x++) if ((gray[(top + y) * width + left + x] & 255) < 175) ink++;
            horizontal = Math.max(horizontal, ink);
        }
        return vertical >= h * .85f && horizontal >= w * .85f;
    }
}
