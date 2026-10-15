// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Compact complete raw components with symbolic head and independently labelled stem evidence. */
final class SymbolStemHeadRecovery {
    record Head(int left, int top, int right, int bottom, int area, float x, float y) {}

    static List<Head> find(
            byte[] labels, byte[] gray, int w, int h, float gap, int top, int bottom) {
        List<Head> result = new ArrayList<>();
        if (gray == null
                || labels == null
                || w < 1
                || h < 1
                || gray.length != (long) w * h
                || labels.length != gray.length
                || !Float.isFinite(gap)
                || gap < 8) return result;
        top = Math.max(0, top);
        bottom = Math.min(h - 1, bottom);
        if (top >= bottom) return result;
        int size = w * (bottom - top + 1);
        boolean[] seen = new boolean[size];
        int[] queue = new int[size];
        for (int seed = 0; seed < size; seed++) {
            if (seen[seed] || (gray[top * w + seed] & 255) >= 205) continue;
            int read = 0, end = 1;
            queue[0] = seed;
            seen[seed] = true;
            int l = w, r = 0, t = h, b = 0, sy = 0, nh = 0;
            long sx = 0, yy = 0;
            while (read < end) {
                int p = queue[read++], x = p % w, y = top + p / w;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                sx += x;
                yy += y;
                if (labels[y * w + x] == 5) sy++;
                if (labels[y * w + x] == 2) nh++;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, ny = y + dy;
                        if (xx < 0 || xx >= w || ny < top || ny > bottom) continue;
                        int next = (ny - top) * w + xx;
                        if (!seen[next] && (gray[ny * w + xx] & 255) < 205) {
                            seen[next] = true;
                            queue[end++] = next;
                        }
                    }
            }
            int ww = r - l + 1, hh = b - t + 1;
            if (t == top
                    || b == bottom
                    || l == 0
                    || r == w - 1
                    || ww < gap * .65f
                    || ww > gap * 1.9f
                    || hh < gap * .55f
                    || hh > gap * 1.5f
                    || end < gap * gap * .32f
                    || sy < end * .4f
                    || nh > end * .2f) continue;
            float cy = yy / (float) end;
            boolean stem = false;
            for (int direction : new int[] {-1, 1}) {
                int edge = direction == 1 ? l : r;
                for (int x = Math.max(0, edge - 3); x <= Math.min(w - 1, edge + 3); x++) {
                    int ink = 0, semantic = 0, total = 0;
                    for (int d = Math.round(gap * .7f); d < Math.round(gap * 2.7f); d++) {
                        int y = Math.round(cy + direction * d);
                        if (y < 0 || y >= h) continue;
                        total++;
                        if ((gray[y * w + x] & 255) < 205) ink++;
                        if (labels[y * w + x] == 1) semantic++;
                    }
                    if (total >= gap && ink >= total * .85f && semantic >= total * .5f) stem = true;
                }
            }
            if (stem) result.add(new Head(l, t, r, b, end, sx / (float) end, cy));
        }
        return result;
    }
}
