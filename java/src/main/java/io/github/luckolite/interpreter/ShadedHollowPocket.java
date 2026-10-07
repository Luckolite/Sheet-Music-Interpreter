// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Closed central oval pockets can remain dark gray in a shaded photograph. */
final class ShadedHollowPocket {
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
                || gray.length != (long) width * height
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || gap < 3) return false;
        int w = right - left + 1, h = bottom - top + 1;
        if (w < gap * .85f
                || w > gap * 1.8f
                || h < gap * .55f
                || h > gap * 1.35f
                || (long) w * h > 4096) return false;
        int[] paper = new int[256], ink = new int[256];
        int np = 0, ni = 0, radius = Math.max(3, Math.round(gap));
        for (int y = Math.max(0, top - radius); y <= Math.min(height - 1, bottom + radius); y++)
            for (int x = Math.max(0, left - radius);
                    x <= Math.min(width - 1, right + radius);
                    x++) {
                int value = gray[y * width + x] & 255;
                if (x >= left && x <= right && y >= top && y <= bottom) {
                    ink[value]++;
                    ni++;
                } else {
                    paper[value]++;
                    np++;
                }
            }
        int background = percentile(paper, np, 85), dark = percentile(ink, ni, 10);
        if (background < 60 || background >= 185 || background - dark < 35) return false;
        int originalArea = w * h;
        if (closedPocket(
                gray, width, left, top, right, bottom, (background + dark) / 2, originalArea))
            return true;
        int threshold = background - Math.max(12, Math.round((background - dark) * .25f));
        int fringe = Math.max(1, Math.round(gap * .12f));
        left = Math.max(0, left - fringe);
        right = Math.min(width - 1, right + fringe);
        top = Math.max(0, top - fringe);
        bottom = Math.min(height - 1, bottom + fringe);
        return closedPocket(gray, width, left, top, right, bottom, threshold, originalArea);
    }

    private static boolean closedPocket(
            byte[] gray,
            int width,
            int left,
            int top,
            int right,
            int bottom,
            int threshold,
            int originalArea) {
        int w = right - left + 1, h = bottom - top + 1;
        boolean[] visited = new boolean[w * h];
        int[] queue = new int[w * h];
        int enclosed = 0, central = 0;
        boolean[] pocketRows = new boolean[h];
        for (int first = 0; first < w * h; first++) {
            if (visited[first]
                    || (gray[(top + first / w) * width + left + first % w] & 255) < threshold)
                continue;
            int size = 1;
            queue[0] = first;
            visited[first] = true;
            boolean edge = false;
            for (int at = 0; at < size; at++) {
                int index = queue[at], x = index % w, y = index / w;
                edge |= x == 0 || y == 0 || x == w - 1 || y == h - 1;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                        int next = yy * w + xx;
                        if (!visited[next]
                                && (gray[(top + yy) * width + left + xx] & 255) >= threshold) {
                            visited[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (edge || size < 2) continue;
            enclosed += size;
            for (int i = 0; i < size; i++) {
                int x = queue[i] % w, y = queue[i] / w;
                if (Math.abs(x - (w - 1) * .5f) <= w * .3f
                        && Math.abs(y - (h - 1) * .5f) <= h * .35f) {
                    central++;
                    pocketRows[y] = true;
                }
            }
        }
        int rows = 0;
        for (boolean row : pocketRows) if (row) rows++;
        return enclosed >= Math.max(4, Math.round(originalArea * .03f))
                && central >= 3
                && rows >= 2;
    }

    private static int percentile(int[] values, int total, int percent) {
        int seen = 0;
        for (int i = 0; i < 256; i++)
            if ((seen += values[i]) >= Math.max(1, total * percent / 100)) return i;
        return 255;
    }
}
