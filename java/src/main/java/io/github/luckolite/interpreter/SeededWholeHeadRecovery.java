// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

final class SeededWholeHeadRecovery {
    record Head(int left, int top, int right, int bottom, int area, float x, float y) {}

    static Head recover(byte[] gray, int width, int height, float gap, float seedX, float seedY) {
        if (gray == null
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || !Float.isFinite(gap)
                || gap < 6) return null;
        int l = Math.max(0, Math.round(seedX - gap * 1.4f)),
                r = Math.min(width - 1, Math.round(seedX + gap * 1.4f));
        int t = Math.max(0, Math.round(seedY - gap)),
                b = Math.min(height - 1, Math.round(seedY + gap));
        int w = r - l + 1, h = b - t + 1;
        if (w < 1 || h < 1) return null;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h], steps = {-1, 1, -w, w};
        for (int seed = 0; seed < w * h; seed++) {
            if (seen[seed] || (gray[(t + seed / w) * width + l + seed % w] & 255) < 205) continue;
            int read = 0, end = 1;
            queue[0] = seed;
            seen[seed] = true;
            boolean edge = false;
            int minX = w, maxX = 0, minY = h, maxY = 0;
            while (read < end) {
                int p = queue[read++], x = p % w, y = p / w;
                edge |= x == 0 || x == w - 1 || y == 0 || y == h - 1;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int delta : steps) {
                    int n = p + delta;
                    if (n < 0
                            || n >= w * h
                            || seen[n]
                            || Math.abs(n % w - x) + Math.abs(n / w - y) != 1) continue;
                    if ((gray[(t + n / w) * width + l + n % w] & 255) >= 205) {
                        seen[n] = true;
                        queue[end++] = n;
                    }
                }
            }
            if (edge || end < gap * gap * .1f) continue;
            float cx = l + (minX + maxX) * .5f, cy = t + (minY + maxY) * .5f;
            if (Math.abs(cx - seedX) > gap * .45f || Math.abs(cy - seedY) > gap * .35f) continue;
            int left = Math.round(cx - gap * .85f),
                    right = Math.round(cx + gap * .85f),
                    top = Math.round(cy - gap * .55f),
                    bottom = Math.round(cy + gap * .55f);
            if (!WholeHeadPocketEvidence.proved(gray, width, height, left, top, right, bottom, gap))
                continue;
            boolean sides = true;
            for (int side : new int[] {-1, 1}) {
                int ink = 0, total = 0;
                for (int y = Math.round(cy - gap * .25f); y <= Math.round(cy + gap * .25f); y++)
                    for (int dx = Math.round(gap * .4f); dx <= Math.round(gap * .8f); dx++) {
                        int x = Math.round(cx) + side * dx;
                        total++;
                        if ((gray[y * width + x] & 255) < 170) ink++;
                    }
                if (ink < total * .6f) sides = false;
            }
            if (!sides) continue;
            int area = 0;
            for (int y = top; y <= bottom; y++)
                for (int x = left; x <= right; x++) if ((gray[y * width + x] & 255) < 170) area++;
            return new Head(left, top, right, bottom, area, cx, cy);
        }
        return null;
    }
}
