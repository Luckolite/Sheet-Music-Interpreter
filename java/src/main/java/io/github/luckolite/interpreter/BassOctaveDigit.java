// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A small two-counter 8 may close against the top staff rule above a bass-clef body. */
final class BassOctaveDigit {
    static boolean above(
            byte[] gray, int width, int height, int left, int right, float staffTop, float gap) {
        return boundsAbove(gray, width, height, left, right, staffTop, gap) != null;
    }

    static int[] boundsAbove(
            byte[] gray, int width, int height, int left, int right, float staffTop, float gap) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || !Float.isFinite(staffTop)
                || !Float.isFinite(gap)
                || gap < 6) return null;
        int x0 = Math.max(0, left),
                x1 = Math.min(width - 1, right),
                y0 = Math.max(0, Math.round(staffTop - gap * 1.8f)),
                y1 = Math.min(height - 1, Math.round(staffTop - gap * .2f));
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        if (w < 1 || h < 1) return null;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || (gray[(y0 + seed / w) * width + x0 + seed % w] & 255) > 175) continue;
            int take = 0, size = 1, minX = w, maxX = -1, minY = h, maxY = -1;
            queue[0] = seed;
            seen[seed] = true;
            while (take < size) {
                int n = queue[take++], x = n % w, y = n / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int at = ny * w + nx;
                        if (!seen[at] && (gray[(y0 + ny) * width + x0 + nx] & 255) <= 175) {
                            seen[at] = true;
                            queue[size++] = at;
                        }
                    }
            }
            int gw = maxX - minX + 1, top = y0 + minY;
            if (gw < gap * .3f || gw > gap * 1.1f || size < gap * gap * .12f || top < y0 + 1)
                continue;
            for (int bottom = y0 + maxY;
                    bottom <= Math.min(height - 1, Math.round(staffTop + gap * .12f));
                    bottom++) {
                int gh = bottom - top + 1;
                if (gh < gw * 1.1f || gh > gap * 2.2f) continue;
                if (OctaveClefDigit.holes(gray, width, x0 + minX, top, gw, gh) == 2
                        && !neighbor(
                                gray,
                                width,
                                height,
                                x0 + minX,
                                top,
                                gw,
                                Math.min(gh, y1 - top + 1),
                                gap)) return new int[] {x0 + minX, top, gw, gh};
            }
        }
        return null;
    }

    private static boolean neighbor(
            byte[] g, int w, int h, int left, int top, int gw, int gh, float gap) {
        gh = Math.min(gh, Math.max(4, Math.round(gap * .6f)));
        for (int side : new int[] {-1, 1}) {
            int lo = side < 0 ? Math.max(0, Math.round(left - gap * 1.6f)) : left + gw + 1;
            int hi = side < 0 ? left - 2 : Math.min(w - 1, Math.round(left + gw + gap * 1.6f));
            int rows = 0, area = 0;
            for (int y = top; y < Math.min(h, top + gh); y++) {
                boolean ink = false;
                for (int x = lo; x <= hi; x++)
                    if ((g[y * w + x] & 255) < 175) {
                        ink = true;
                        area++;
                    }
                if (ink) rows++;
            }
            if (rows >= gh * .7f && area >= gap * gap * .15f) return true;
        }
        return false;
    }
}
