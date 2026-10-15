// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A continuous thick rail between two independently proved, same-direction stems. */
final class PrintedSingleBeamPath {
    private PrintedSingleBeamPath() {}

    static boolean connects(byte[] gray, int width, int height, int[] a, int[] b, float gap) {
        if (gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || a == null
                || b == null
                || a.length != 3
                || b.length != 3
                || !Float.isFinite(gap)
                || gap < 8
                || Math.abs(a[2]) != 1
                || a[2] != b[2]) return false;
        if (a[0] > b[0]) {
            int[] t = a;
            a = b;
            b = t;
        }
        int span = b[0] - a[0];
        if (span < gap * 2 || span > gap * 5 || Math.abs(b[1] - a[1]) > gap * .75f) return false;
        int margin = Math.max(3, Math.round(gap * .3f)), radius = Math.round(gap);
        int reach = Math.max(2, Math.round(gap * .5f));
        boolean[] previous = new boolean[height];
        boolean first = true;
        for (int x = a[0] + margin; x <= b[0] - margin; x++) {
            int expected = Math.round(a[1] + (b[1] - a[1]) * (x - a[0]) / (float) span);
            if (x < 1
                    || x >= width - 1
                    || expected - radius - reach < 0
                    || expected + radius + reach >= height) return false;
            int[] values = new int[radius * 2 + 1];
            for (int d = -radius; d <= radius; d++)
                values[d + radius] = gray[(expected + d) * width + x] & 255;
            java.util.Arrays.sort(values);
            int paper = values[values.length * 4 / 5];
            if (paper < 225) return false;
            int limit = Math.min(205, paper - 30);
            boolean[] current = new boolean[height];
            boolean any = false;
            for (int y = expected - reach; y <= expected + reach; y++) {
                if ((gray[(y - 1) * width + x] & 255) >= limit
                        || (gray[y * width + x] & 255) >= limit
                        || (gray[(y + 1) * width + x] & 255) >= limit) continue;
                int top = y, bottom = y;
                while (top > y - radius && (gray[(top - 1) * width + x] & 255) < limit) top--;
                while (bottom < y + radius && (gray[(bottom + 1) * width + x] & 255) < limit)
                    bottom++;
                int thickness = bottom - top + 1;
                if (thickness < Math.max(4, Math.ceil(gap * .25f)) || thickness > gap * .8f)
                    continue;
                if (first || previous[y - 1] || previous[y] || previous[y + 1]) {
                    current[y] = true;
                    any = true;
                }
            }
            if (!any) return false;
            previous = current;
            first = false;
        }
        return !first;
    }
}
