// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Single-digit tuplet numerals must not be fragments of a printed number or word. */
final class TupletNumeralNeighbors {
    private TupletNumeralNeighbors() {}

    static boolean joinedText(
            byte[] gray, int width, int height, int left, int top, int right, int bottom) {
        if (gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || left < 0
                || top < 0
                || right >= width
                || bottom >= height
                || left > right
                || top > bottom) return false;
        int glyphHeight = bottom - top + 1;
        if (glyphHeight < 7) return false;
        int margin = Math.max(4, Math.round(glyphHeight * 1.6f));
        int x0 = Math.max(0, left - margin), x1 = Math.min(width - 1, right + margin);
        int y0 = Math.max(0, top - Math.round(glyphHeight * .8f)),
                y1 = Math.min(height - 1, bottom + Math.round(glyphHeight * .3f));
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || (gray[(y0 + seed / w) * width + x0 + seed % w] & 255) >= 165)
                continue;
            int pending = 1, count = 0, minX = width, maxX = -1, minY = height, maxY = -1;
            seen[seed] = true;
            queue[0] = seed;
            while (pending > 0) {
                int at = queue[--pending], x = at % w, y = at / w;
                count++;
                minX = Math.min(minX, x + x0);
                maxX = Math.max(maxX, x + x0);
                minY = Math.min(minY, y + y0);
                maxY = Math.max(maxY, y + y0);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (seen[next] || (gray[(ny + y0) * width + nx + x0] & 255) >= 165)
                            continue;
                        seen[next] = true;
                        queue[pending++] = next;
                    }
            }
            if (minX <= x0 || maxX >= x1 || minY <= y0 || maxY >= y1) continue;
            int separation = maxX < left ? left - maxX : minX > right ? minX - right : -1;
            int cw = maxX - minX + 1, ch = maxY - minY + 1;
            // Lowercase glyphs may be followed by a taller ascender on the same baseline.
            boolean alignedAscender =
                    ch > glyphHeight * 1.35f
                            && ch <= glyphHeight * 1.9f
                            && Math.abs(maxY - bottom) <= glyphHeight * .2f;
            if (separation < 1
                    || separation > glyphHeight * .45f
                    || ch < glyphHeight * .65f
                    || ch > glyphHeight * 1.35f && !alignedAscender
                    || cw < Math.max(2, ch * .12f)
                    || cw > ch * .95f
                    || count < cw * ch * .10f
                    || !alignedAscender
                            && Math.abs((minY + maxY - top - bottom) * .5f) > glyphHeight * .25f)
                continue;
            return true;
        }
        return false;
    }
}
