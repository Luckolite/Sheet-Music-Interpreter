// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A bounded detached two-cycle ornament cannot extend a completed beam shaft. */
final class DetachedMordentBeamCap {
    private DetachedMordentBeamCap() {}

    static boolean crosses(
            byte[] gray,
            int width,
            int height,
            float headY,
            int[] strict,
            int[] pale,
            float gap,
            int threshold) {
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || strict == null
                || pale == null
                || strict.length < 3
                || pale.length < 3
                || !Float.isFinite(gap)
                || gap < 8
                || gap > Math.min(width, height) / 6f
                || !Float.isFinite(headY)
                || headY < 0
                || headY >= height
                || Math.abs(strict[2]) != 1
                || strict[2] != pale[2]
                || Math.abs(strict[0] - pale[0]) > gap * .3f
                || strict[1] < 0
                || strict[1] >= height
                || pale[1] < 0
                || pale[1] >= height
                || threshold < 1
                || threshold > 220) return false;
        int side = strict[2], x = pale[0];
        float distance = (pale[1] - strict[1]) * side;
        if (distance < gap * .5f || distance > gap * 2 || x < 1 || x >= width - 1) return false;
        int cap =
                DetachedBeamTailCap.endpoint(
                        gray, width, height, x, headY, pale[1], side, gap, threshold);
        if (cap == pale[1] || Math.abs(cap - strict[1]) > Math.max(1, gap * .25f)) return false;
        int radius = Math.round(gap * 2);
        int left = x - radius,
                right = x + radius,
                top = Math.min(pale[1], strict[1]) - Math.round(gap),
                bottom = Math.max(pale[1], strict[1]) + Math.round(gap);
        if (left < 1 || right >= width - 1 || top < 1 || bottom >= height - 1) return false;
        int seed = -1;
        for (int step = 0; step <= Math.round(gap * .65f); step++) {
            int y = pale[1] - side * step;
            if (y <= top || y >= bottom || (y - strict[1]) * side <= 0) break;
            if ((gray[y * width + x] & 255) < 180) {
                seed = y;
                break;
            }
        }
        if (seed < 0) return false;
        int rw = right - left + 1, rh = bottom - top + 1;
        boolean[] seen = new boolean[rw * rh];
        int[] queue = new int[seen.length];
        int start = (seed - top) * rw + x - left, read = 0, write = 1;
        queue[0] = start;
        seen[start] = true;
        int minX = x, maxX = x, minY = seed, maxY = seed;
        while (read < write) {
            int pos = queue[read++], cx = left + pos % rw, cy = top + pos / rw;
            if (cx == left || cx == right || cy == top || cy == bottom) return false;
            minX = Math.min(minX, cx);
            maxX = Math.max(maxX, cx);
            minY = Math.min(minY, cy);
            maxY = Math.max(maxY, cy);
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int next = pos + dy * rw + dx;
                    if (!seen[next] && (gray[(cy + dy) * width + cx + dx] & 255) < 180) {
                        seen[next] = true;
                        queue[write++] = next;
                    }
                }
        }
        if (maxX - minX + 1 > gap * 3.5f || maxY - minY + 1 > gap * 1.5f) return false;
        return MordentContour.matches(
                gray, width, new PortableNoteOrnaments.Bounds(minX, minY, maxX + 1, maxY + 1));
    }
}
