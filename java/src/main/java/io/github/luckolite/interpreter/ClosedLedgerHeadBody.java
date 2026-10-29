// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete body evidence for the additional remote upper-ledger acquisition route. */
final class ClosedLedgerHeadBody {
    private ClosedLedgerHeadBody() {}

    private record Pocket(int left, int top, int right, int bottom, int area) {}

    static boolean proved(
            byte[] g,
            int w,
            int h,
            int left,
            int top,
            int right,
            int bottom,
            float gap,
            int[] stem) {
        if (g == null
                || w < 1
                || h < 1
                || g.length != (long) w * h
                || !Float.isFinite(gap)
                || gap < 6
                || left < 1
                || right >= w - 1
                || top < 1
                || bottom >= h - 1) return false;
        int bw = right - left + 1, bh = bottom - top + 1;
        if (bw < gap * .8f
                || bw > gap * 1.8f
                || bh < gap * .55f
                || bh > gap * 1.3f
                || (long) bw * bh > 4096) return false;
        int inset = Math.max(1, Math.round(bw * .25f));
        for (int x = left + inset; x <= right - inset; x++)
            if ((g[(top - 1) * w + x] & 255) < 225 || (g[(bottom + 1) * w + x] & 255) < 225)
                return false;
        int corners = 0;
        for (int y : new int[] {top, bottom})
            for (int x : new int[] {left + 1, right - 1})
                if ((g[y * w + x] & 255) < 225) {
                    if (++corners > 1
                            || stem == null
                            || stem.length != 3
                            || Math.abs(stem[0] - x) > Math.max(1, Math.round(gap * .16f))
                            || (y == top
                                    ? stem[2] >= 0 || stem[1] >= top
                                    : stem[2] <= 0 || stem[1] <= bottom)) return false;
                }
        int[] rows = new int[bh];
        int total = 0, peak = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++)
                if ((g[y * w + x] & 255) < 165) {
                    rows[y - top]++;
                    total++;
                    peak = Math.max(peak, rows[y - top]);
                }
        int edge = Math.max(0, Math.round(bh * .12f));
        if (total >= bw * bh * .48f
                && peak >= bw * .75f
                && rows[edge] <= Math.round(peak * .86f)
                && rows[bh - 1 - edge] <= Math.round(peak * .86f)) return true;
        if (total < bw * bh * .22f) return false;
        int margin = Math.max(2, Math.round(gap * .5f)),
                x0 = Math.max(0, left - margin),
                x1 = Math.min(w - 1, right + margin),
                y0 = Math.max(0, top - margin),
                y1 = Math.min(h - 1, bottom + margin),
                cw = x1 - x0 + 1,
                size = cw * (y1 - y0 + 1);
        boolean[] seen = new boolean[size];
        int[] q = new int[size];
        List<Pocket> pockets = new ArrayList<>();
        for (int seed = 0; seed < size; seed++) {
            if (seen[seed] || (g[(y0 + seed / cw) * w + x0 + seed % cw] & 255) <= 150) continue;
            int read = 0, n = 1;
            q[0] = seed;
            seen[seed] = true;
            boolean boundary = false;
            int minX = w, maxX = 0, minY = h, maxY = 0;
            while (read < n) {
                int z = q[read++], x = x0 + z % cw, y = y0 + z / cw;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                boundary |= x == x0 || x == x1 || y == y0 || y == y1;
                for (int d = 0; d < 4; d++) {
                    int xx = x + (d == 0 ? -1 : d == 1 ? 1 : 0),
                            yy = y + (d == 2 ? -1 : d == 3 ? 1 : 0);
                    if (xx < x0 || xx > x1 || yy < y0 || yy > y1) continue;
                    int at = (yy - y0) * cw + xx - x0;
                    if (!seen[at] && (g[yy * w + xx] & 255) > 150) {
                        seen[at] = true;
                        q[n++] = at;
                    }
                }
            }
            if (!boundary && minX > left && maxX < right && minY > top && maxY < bottom)
                pockets.add(new Pocket(minX, minY, maxX, maxY, n));
        }
        for (Pocket a : pockets) {
            if (normal(a, left, top, right, bottom, gap)) return true;
            for (Pocket b : pockets) {
                if (a.bottom >= b.top) continue;
                int first = a.bottom + 1, last = b.top - 1;
                if (last < first || last - first + 1 > Math.max(1, Math.round(gap * .25f)))
                    continue;
                int overlap = Math.min(a.right, b.right) - Math.max(a.left, b.left) + 1;
                if (overlap < Math.min(a.right - a.left + 1, b.right - b.left + 1) * .75f) continue;
                int flank = Math.max(1, Math.round(gap * .3f));
                if (left - flank < 0 || right + flank >= w) continue;
                boolean rule = true;
                for (int y = first; y <= last && rule; y++) {
                    int l = 0, r = 0;
                    for (int d = 1; d <= flank; d++) {
                        if ((g[y * w + left - d] & 255) < 150
                                && ShadedLedgerRule.thin(g, w, h, left - d, y, gap)) l++;
                        if ((g[y * w + right + d] & 255) < 150
                                && ShadedLedgerRule.thin(g, w, h, right + d, y, gap)) r++;
                    }
                    if (Math.min(l, r) < flank * .8f) rule = false;
                }
                if (!rule) continue;
                Pocket joined =
                        new Pocket(
                                Math.min(a.left, b.left),
                                a.top,
                                Math.max(a.right, b.right),
                                b.bottom,
                                a.area + b.area + overlap * (last - first + 1));
                if (normal(joined, left, top, right, bottom, gap)) return true;
            }
        }
        return false;
    }

    /** A model may omit one raw oval tip; the original complete-body proof stays authoritative. */
    static boolean provedModelBoundary(
            byte[] g, int w, int h, int left, int top, int right, int bottom, float gap, int[] stem) {
        if (proved(g, w, h, left, top, right, bottom, gap, stem)) return true;
        if (g == null || g.length != (long) w * h || !Float.isFinite(gap) || gap < 6
                || left < 1 || right >= w - 1 || top < 2 || bottom >= h - 2
                || right < left || bottom < top || (long) (right - left + 1) * (bottom - top + 1) > 4096
                || stem == null || stem.length != 3 || Math.abs(stem[2]) != 1) return false;
        int shaftEdge = stem[2] > 0 ? left : right;
        if (Math.abs(stem[0] - shaftEdge) > Math.max(1, Math.round(gap * .16f))
                || (stem[2] > 0 ? stem[1] - bottom : top - stem[1]) < gap * 2.3f) return false;
        int rawBody = 0;
        for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++)
            if ((g[y * w + x] & 255) < 165) rawBody++;
        if (rawBody < 3) return false;
        boolean above = connectedTip(g, w, left, top, right, bottom, gap, stem, -1)
                && proved(g, w, h, left, top - 1, right, bottom, gap, stem);
        boolean below = connectedTip(g, w, left, top, right, bottom, gap, stem, 1)
                && proved(g, w, h, left, top, right, bottom + 1, gap, stem);
        // A bounded unique model edge is different from searching for a more convenient box.
        return above != below;
    }

    private static boolean connectedTip(
            byte[] g, int w, int left, int top, int right, int bottom, float gap, int[] stem,
            int direction) {
        int outside = direction < 0 ? top - 1 : bottom + 1;
        int inside = direction < 0 ? top : bottom;
        int first = -1, last = -1, count = 0;
        for (int x = left; x <= right; x++) {
            if (direction == stem[2]
                    && Math.abs(x - stem[0]) <= Math.max(1, Math.round(gap * .12f))) continue;
            if ((g[outside * w + x] & 255) >= 225) continue;
            if ((g[inside * w + x] & 255) >= 225) return false;
            if (first < 0) first = x;
            last = x;
            count++;
        }
        return count > 0 && first > left && last < right
                && last - first + 1 == count && count <= Math.round((right - left + 1) * .75f);
    }

    private static boolean normal(Pocket p, int left, int top, int right, int bottom, float gap) {
        int pw = p.right - p.left + 1, ph = p.bottom - p.top + 1;
        return pw >= gap * .45f
                && pw <= gap * 1.65f
                && ph >= gap * .4f
                && ph <= gap * 1.05f
                && p.area >= pw * ph * .45f
                && Math.abs((p.left + p.right - left - right) * .5f) <= gap * .2f
                && Math.abs((p.top + p.bottom - top - bottom) * .5f) <= gap * .2f;
    }
}
