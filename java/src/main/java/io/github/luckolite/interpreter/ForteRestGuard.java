// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Verifies a whole connected forte glyph when an off-staff rest candidate captured its top. */
final class ForteRestGuard {
    static boolean owns(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float gap) {
        if (gray == null || gray.length != width * height || gap < 6 || !Float.isFinite(gap))
            return false;
        int x0 = Math.max(0, Math.round(left - gap)),
                x1 = Math.min(width - 1, Math.round(right + gap));
        int y0 = Math.max(0, Math.round(top - gap * .5f)),
                y1 = Math.min(height - 1, Math.round(bottom + gap * .75f));
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        if (w <= 0 || h <= 0 || w > gap * 5 || h > gap * 5) return false;
        for (int threshold : new int[] {170, 205}) {
            boolean[] seen = new boolean[w * h];
            int[] queue = new int[w * h];
            for (int seed = 0; seed < seen.length; seed++) {
                if (seen[seed]
                        || (gray[(y0 + seed / w) * width + x0 + seed % w] & 255) >= threshold)
                    continue;
                int take = 0, size = 1, minX = w, maxX = -1, minY = h, maxY = -1;
                queue[0] = seed;
                seen[seed] = true;
                while (take < size) {
                    int at = queue[take++], x = at % w, y = at / w;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                            int next = ny * w + nx;
                            if (!seen[next]
                                    && (gray[(y0 + ny) * width + x0 + nx] & 255) < threshold) {
                                seen[next] = true;
                                queue[size++] = next;
                            }
                        }
                }
                // An adjacent dynamic cannot claim a separate genuine rest.
                if (x0 + maxX < left || x0 + minX > right || y0 + maxY < top || y0 + minY > bottom)
                    continue;
                float cx = x0 + (minX + maxX) * .5f, cy = y0 + (minY + maxY) * .5f;
                if (Math.abs(cx - (left + right) * .5f) > gap * .7f
                        || Math.abs(cy - (top + bottom) * .5f) > gap * .7f) continue;
                byte[] ink = new byte[w * h];
                for (int i = 0; i < size; i++) ink[queue[i]] = 1;
                if (ForteInkShape.matches(ink, w, minX, maxX, minY, maxY, gap)) return true;
            }
        }
        return false;
    }
}
