// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Separate printed beam bands cannot establish an inter-staff ink connection. */
final class BeamInkConnectivity {
    private BeamInkConnectivity() {}

    static boolean connected(
            byte[] gray, int width, int height, int ax, int ay, int bx, int by, float gap) {
        if (gray == null || gray.length != (long) width * height || bx <= ax || gap < 2)
            return false;
        int left = ax + 2, right = bx - 2, radius = Math.max(2, Math.round(gap * .22f));
        int top = Math.max(0, Math.min(ay, by) - radius),
                bottom = Math.min(height - 1, Math.max(ay, by) + radius);
        if (left < 0 || right >= width || right <= left) return false;
        int w = right - left + 1, h = bottom - top + 1;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        int size = 0, take = 0;
        int first = Math.round(ay + (by - ay) * (left - ax) / (float) (bx - ax));
        for (int y = Math.max(top, first - 1); y <= Math.min(bottom, first + 1); y++)
            if ((gray[y * width + left] & 255) < 165) {
                int at = (y - top) * w;
                seen[at] = true;
                queue[size++] = at;
            }
        while (take < size) {
            int at = queue[take++], x = left + at % w, y = top + at / w;
            int expected = Math.round(ay + (by - ay) * (x - ax) / (float) (bx - ax));
            if (x == right && Math.abs(y - expected) <= 1) return true;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < left || nx > right || ny < top || ny > bottom) continue;
                    int next = (ny - top) * w + nx - left;
                    if (seen[next]) continue;
                    int line = Math.round(ay + (by - ay) * (nx - ax) / (float) (bx - ax));
                    if (Math.abs(ny - line) > radius || (gray[ny * width + nx] & 255) >= 165)
                        continue;
                    seen[next] = true;
                    queue[size++] = next;
                }
        }
        return false;
    }
}
