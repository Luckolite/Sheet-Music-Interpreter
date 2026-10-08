// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;

/** A sustained written oval requires a closed central pocket and its own complete printed shaft. */
final class ClosedWrittenHeadPocket {
    private ClosedWrittenHeadPocket() {}

    static boolean proved(
            byte[] gray,
            int w,
            int h,
            int left,
            int top,
            int right,
            int bottom,
            float cx,
            float cy,
            float gap,
            float slope) {
        if (PrintedStemMetadata.detect(gray, w, h, left, right, top, bottom, cx, cy, gap, slope)
                == 0) return false;
        int l = Math.max(left, (int) Math.ceil(cx - gap * .9f));
        int r = Math.min(right, (int) Math.floor(cx + gap * .9f));
        int t = Math.max(top, (int) Math.ceil(cy - gap * .675f));
        int b = Math.min(bottom, (int) Math.floor(cy + gap * .675f));
        if (r - l + 1 < gap * .85f || b - t + 1 < gap * .55f) return false;
        int radius = Math.max(3, Math.round(gap));
        int[] paper = new int[256], ink = new int[256];
        int np = 0, ni = 0;
        for (int y = Math.max(0, t - radius); y <= Math.min(h - 1, b + radius); y++)
            for (int x = Math.max(0, l - radius); x <= Math.min(w - 1, r + radius); x++) {
                int v = gray[y * w + x] & 255;
                if (x >= l && x <= r && y >= t && y <= b) {
                    ink[v]++;
                    ni++;
                } else {
                    paper[v]++;
                    np++;
                }
            }
        int bg = percentile(paper, np, 85), dark = percentile(ink, ni, 10);
        if (bg < 60 || bg >= 185 || bg - dark < 35) return false;
        int threshold = (bg + dark) / 2, bright = bg - Math.max(12, Math.round((bg - dark) * .25f));
        int width = r - l + 1, height = b - t + 1;
        boolean[] seen = new boolean[width * height];
        int[] queue = new int[seen.length];
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || (gray[(t + seed / width) * w + l + seed % width] & 255) < threshold)
                continue;
            int size = 1;
            queue[0] = seed;
            seen[seed] = true;
            boolean edge = false;
            int minX = width, maxX = -1, minY = height, maxY = -1, central = 0;
            for (int at = 0; at < size; at++) {
                int z = queue[at], x = z % width, y = z / width;
                edge |= x == 0 || x == width - 1 || y == 0 || y == height - 1;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (Math.abs(l + x - cx) <= gap * .35f && Math.abs(t + y - cy) <= gap * .35f)
                    central++;
                for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                    int xx = x + d[0], yy = y + d[1];
                    if (xx < 0 || xx >= width || yy < 0 || yy >= height) continue;
                    int next = yy * width + xx;
                    if (!seen[next] && (gray[(t + yy) * w + l + xx] & 255) >= threshold) {
                        seen[next] = true;
                        queue[size++] = next;
                    }
                }
            }
            if (edge
                    || size < Math.max(6, Math.round(gap * gap * .03f))
                    || central < 5
                    || maxX - minX + 1 < gap * .4f
                    || maxY - minY + 1 < gap * .2f) continue;
            // Only a shaded antialias corner may close diagonally. A raw paper notch remains open.
            boolean brightLeak = false;
            boolean[] member = new boolean[seen.length];
            for (int i = 0; i < size; i++) member[queue[i]] = true;
            for (int i = 0; i < size && !brightLeak; i++) {
                int z = queue[i], x = z % width, y = z / width;
                for (int dx : new int[] {-1, 1})
                    for (int dy : new int[] {-1, 1}) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= width || yy < 0 || yy >= height) continue;
                        int n = yy * width + xx;
                        if (!member[n] && (gray[(t + yy) * w + l + xx] & 255) >= bright)
                            brightLeak = true;
                    }
            }
            if (!brightLeak) return true;
        }
        return false;
    }

    private static int percentile(int[] a, int n, int p) {
        int sum = 0;
        for (int i = 0; i < 256; i++) if ((sum += a[i]) >= Math.max(1, n * p / 100)) return i;
        return 255;
    }
}
