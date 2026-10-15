// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Independent narrow ink on both sides of a full-height separator, excluding its staff rule. */
final class ThinBoundaryArc {
    private ThinBoundaryArc() {}

    static boolean proved(
            byte[] gray, int width, int height, int x, int edge, float gap, int inward) {
        if (gray == null || gray.length != (long) width * height || !Float.isFinite(gap) || gap < 8)
            return false;
        int near = Math.max(3, Math.round(gap * .3f)),
                far = Math.max(near + 2, Math.round(gap * .9f));
        int reach = Math.max(4, Math.round(gap * .85f)), rule = Math.max(1, Math.round(gap * .12f));
        int maxInk = Math.max(2, Math.round(gap * .3f));
        if (x - far < 0 || x + far >= width || edge - reach < 0 || edge + reach >= height)
            return false;
        int firstSideCenter = Integer.MIN_VALUE;
        for (int side : new int[] {-1, 1}) {
            int supported = 0, previous = Integer.MIN_VALUE, centerSum = 0;
            boolean ended = false;
            for (int dx = near; dx <= far; dx++) {
                int count = 0, first = height, last = -1;
                for (int dy = rule + 1; dy <= reach; dy++) {
                    int y = edge + inward * dy;
                    if ((gray[y * width + x + side * dx] & 255) < 180) {
                        count++;
                        first = Math.min(first, y);
                        last = y;
                    }
                }
                if (count > maxInk || (count > 0 && last - first + 1 > maxInk)) return false;
                if (count == 0) {
                    ended = true;
                }
                if (count > 0) {
                    if (ended) return false;
                    int center = (first + last) / 2;
                    if (previous != Integer.MIN_VALUE && Math.abs(center - previous) > 2)
                        return false;
                    previous = center;
                    centerSum += center;
                    supported++;
                }
            }
            if (supported < Math.max(3, Math.round(gap * .35f))) return false;
            int sideCenter = Math.round(centerSum / (float) supported);
            if (firstSideCenter == Integer.MIN_VALUE) firstSideCenter = sideCenter;
            else if (Math.abs(firstSideCenter - sideCenter) < Math.max(2, Math.round(gap * .12f)))
                return false;
        }
        return true;
    }
}
