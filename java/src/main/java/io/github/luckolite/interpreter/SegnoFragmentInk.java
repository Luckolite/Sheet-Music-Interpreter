// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Requires the full crossed segno body and two separately printed opposing dots. */
final class SegnoFragmentInk {
    private SegnoFragmentInk() {}

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
                || maxY >= height
                || minX > maxX
                || minY > maxY) return false;
        int left = Math.max(0, Math.round(minX - gap * 3)),
                right = Math.min(width - 1, Math.round(maxX + gap * 3));
        int top = Math.max(0, Math.round(minY - gap * 3)),
                bottom = Math.min(height - 1, Math.round(maxY + gap * 3));
        int w = right - left + 1, h = bottom - top + 1;
        int[] queue = null;
        for (int threshold : new int[] {65, 80, 110, 140}) {
            boolean[] seen = new boolean[w * h];
            if (queue == null) queue = new int[w * h];
            var glyphs = new ArrayList<int[]>();
            for (int seed = 0; seed < w * h; seed++) {
                if (seen[seed]
                        || (gray[(top + seed / w) * width + left + seed % w] & 255) > threshold)
                    continue;
                int size = 1, take = 0, l = w, r = -1, t = h, b = -1;
                queue[0] = seed;
                seen[seed] = true;
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
                            int n = yy * w + xx;
                            if (!seen[n]
                                    && (gray[(top + yy) * width + left + xx] & 255) <= threshold) {
                                seen[n] = true;
                                queue[size++] = n;
                            }
                        }
                }
                if (l > 0 && t > 0 && r < w - 1 && b < h - 1 && size >= 4)
                    glyphs.add(new int[] {l + left, t + top, r + left, b + top, size});
            }
            for (int[] body : glyphs) {
                float bw = body[2] - body[0] + 1,
                        bh = body[3] - body[1] + 1,
                        cx = (body[0] + body[2]) * .5f,
                        cy = (body[1] + body[3]) * .5f;
                if (bw < gap
                        || bw > gap * 3
                        || bh < gap * 1.6f
                        || bh > gap * 3.5f
                        || bw / bh < .55f
                        || bw / bh > .95f
                        || body[4] < gap * gap * .5f
                        || body[4] > bw * bh * .7f
                        || minX > body[2]
                        || maxX < body[0]
                        || minY > body[3]
                        || maxY < body[1]) continue;
                boolean leftDot = false, rightDot = false;
                for (int[] dot : glyphs) {
                    if (dot == body) continue;
                    float dw = dot[2] - dot[0] + 1,
                            dh = dot[3] - dot[1] + 1,
                            dx = (dot[0] + dot[2]) * .5f - cx,
                            dy = (dot[1] + dot[3]) * .5f - cy;
                    if (dw < gap * .15f
                            || dw > gap * .6f
                            || dh < gap * .15f
                            || dh > gap * .6f
                            || dw / dh < .45f
                            || dw / dh > 1.8f
                            || dot[4] < dw * dh * .5f
                            || Math.abs(dx) < bw * .32f
                            || Math.abs(dx) > bw * .65f
                            || Math.abs(dy) > bh * .25f) continue;
                    if (dx < 0 && dy > 0) leftDot = true;
                    if (dx > 0 && dy < 0) rightDot = true;
                }
                if (leftDot && rightDot && crossingDiagonal(gray, width, body, threshold, gap))
                    return true;
            }
        }
        return false;
    }

    private static boolean crossingDiagonal(
            byte[] gray, int width, int[] body, int threshold, float gap) {
        int hits = 0, total = 0;
        for (int i = 0; i <= 12; i++) {
            float f = i / 12f;
            int x = Math.round(body[0] + (body[2] - body[0]) * f),
                    y = Math.round(body[3] - (body[3] - body[1]) * f);
            boolean ink = false;
            for (int dy = -Math.round(gap * .16f); dy <= Math.round(gap * .16f); dy++)
                for (int dx = -Math.round(gap * .16f); dx <= Math.round(gap * .16f); dx++)
                    if (x + dx >= body[0]
                            && x + dx <= body[2]
                            && y + dy >= body[1]
                            && y + dy <= body[3]
                            && (gray[(y + dy) * width + x + dx] & 255) <= threshold) ink = true;
            total++;
            if (ink) hits++;
        }
        return hits >= total * .85f;
    }
}
