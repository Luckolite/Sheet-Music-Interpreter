// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Independent closed half-head evidence when semantic edges include a short ledger and omit AA tips. */
final class HollowLedgerModelEnvelope {
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
                || left < 2
                || right >= w - 2
                || top < 2
                || bottom >= h - 2
                || right < left
                || bottom < top
                || stem == null
                || stem.length != 3
                || Math.abs(stem[2]) != 1) return false;
        int t = top - 1, b = bottom + 1, bw = right - left + 1, bh = b - t + 1;
        // Extents measure pixel-centre distance, not a count with an extra sample at both ends.
        if (right - left < gap * .8f
                || right - left > Math.round(gap * 1.8f)
                || b - t < gap * .55f
                || b - t > Math.round(gap * 1.3f)
                || (long) bw * bh > 4096) return false;
        int axis = stem[2] > 0 ? left : right;
        if (Math.abs(stem[0] - axis) > Math.max(1, Math.round(gap * .16f))
                || (stem[2] > 0 ? stem[1] - bottom : top - stem[1]) < gap * 2.3f) return false;
        int inset = Math.max(1, Math.round(bw * .25f));
        for (int x = left + inset; x <= right - inset; x++)
            if ((g[(t - 1) * w + x] & 255) < 225 || (g[(b + 1) * w + x] & 255) < 225) return false;
        boolean[] rules = new boolean[bh];
        for (int y = t; y <= b; y++) rules[y - t] = ledgerRow(g, w, h, left, right, y, gap);
        for (int y : new int[] {top, bottom})
            for (int x : new int[] {left + 1, right - 1})
                if ((g[y * w + x] & 255) < 225
                        && !rules[y - t]
                        && !shaftPixel(x, y, top, bottom, gap, stem)) return false;
        if (!tipOwned(g, w, left, top, right, bottom, gap, stem, -1, rules[0])
                || !tipOwned(g, w, left, top, right, bottom, gap, stem, 1, rules[bh - 1]))
            return false;
        int[] spans = new int[bh];
        int peak = 0;
        for (int y = t; y <= b; y++) {
            if (rules[y - t]) continue;
            int x0 = w, x1 = -1;
            for (int x = left; x <= right; x++)
                if ((g[y * w + x] & 255) < 225 && !shaftPixel(x, y, top, bottom, gap, stem)) {
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                }
            spans[y - t] = Math.max(0, x1 - x0 + 1);
            peak = Math.max(peak, spans[y - t]);
        }
        if (peak < bw * .65f) return false;
        int edge = Math.max(1, Math.round(bh * .12f)),
                reach = Math.max(edge, Math.round(bh * .35f));
        int upper = -1, lower = -1;
        for (int d = edge; d <= reach; d++) {
            if (upper < 0 && !rules[d]) upper = spans[d];
            if (lower < 0 && !rules[bh - 1 - d]) lower = spans[bh - 1 - d];
        }
        if (upper < 0
                || lower < 0
                || upper > Math.round(peak * .86f)
                || lower > Math.round(peak * .86f)) return false;
        boolean[] seen = new boolean[bw * bh];
        int[] queue = new int[seen.length];
        Pocket found = null;
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || (g[(t + seed / bw) * w + left + seed % bw] & 255) <= 150) continue;
            int read = 0, n = 1, x0 = w, x1 = -1, y0 = h, y1 = -1;
            boolean exterior = false;
            queue[0] = seed;
            seen[seed] = true;
            while (read < n) {
                int at = queue[read++], x = at % bw, y = at / bw;
                exterior |= x == 0 || x == bw - 1 || y == 0 || y == bh - 1;
                x0 = Math.min(x0, left + x);
                x1 = Math.max(x1, left + x);
                y0 = Math.min(y0, t + y);
                y1 = Math.max(y1, t + y);
                for (int d : new int[] {-1, 1, -bw, bw}) {
                    int k = at + d;
                    if (k < 0
                            || k >= seen.length
                            || seen[k]
                            || Math.abs(k % bw - x) + Math.abs(k / bw - y) != 1) continue;
                    if ((g[(t + k / bw) * w + left + k % bw] & 255) > 150) {
                        seen[k] = true;
                        queue[n++] = k;
                    }
                }
            }
            if (exterior || n < Math.max(2, gap * gap * .025f)) continue;
            if (found != null) return false;
            found = new Pocket(x0, y0, x1, y1, n);
        }
        if (found == null) return false;
        int pw = found.right - found.left + 1, ph = found.bottom - found.top + 1;
        return pw >= gap * .45f
                && pw <= gap * 1.65f
                && ph >= gap * .4f
                && ph <= gap * 1.05f
                && found.area >= pw * ph * .45f
                && Math.abs((found.left + found.right - left - right) * .5f) <= gap * .2f
                && Math.abs((found.top + found.bottom - t - b) * .5f) <= gap * .2f;
    }

    private static boolean shaftPixel(int x, int y, int top, int bottom, float gap, int[] stem) {
        return Math.abs(x - stem[0]) <= Math.max(1, Math.round(gap * .16f))
                && (y - (top + bottom) * .5f) * stem[2] >= -gap * .3f
                && (stem[1] - y) * stem[2] >= 0;
    }

    private static boolean tipOwned(
            byte[] g,
            int w,
            int left,
            int top,
            int right,
            int bottom,
            float gap,
            int[] stem,
            int direction,
            boolean rule) {
        if (rule) return true;
        int y = direction < 0 ? top - 1 : bottom + 1, inside = direction < 0 ? top : bottom;
        int first = -1, last = -1, n = 0;
        for (int x = left; x <= right; x++) {
            if ((g[y * w + x] & 255) >= 225 || shaftPixel(x, y, top, bottom, gap, stem)) continue;
            if ((g[inside * w + x] & 255) >= 225) return false;
            if (first < 0) first = x;
            last = x;
            n++;
        }
        return n == 0
                || first > left
                        && last < right
                        && last - first + 1 == n
                        && n <= Math.round((right - left + 1) * .75f);
    }

    private static boolean ledgerRow(
            byte[] g, int w, int h, int left, int right, int y, float gap) {
        int margin = Math.max(1, Math.round(gap * .18f)), l = left - margin, r = right + margin;
        if (l < 1 || r >= w - 1) return false;
        for (int row = Math.max(1, y - 1); row <= Math.min(h - 2, y + 1); row++) {
            int ink = 0, a = 0, b = 0;
            for (int x = l; x <= r; x++)
                if ((g[row * w + x] & 255) < 165) {
                    ink++;
                    if (x < left && ShadedLedgerRule.thin(g, w, h, x, row, gap)) a++;
                    if (x > right && ShadedLedgerRule.thin(g, w, h, x, row, gap)) b++;
                }
            if (ink < (r - l + 1) * .85f || a < margin || b < margin) continue;
            int cx = (left + right) / 2,
                    x0 = cx,
                    x1 = cx,
                    limit = Math.max(4, Math.round(gap * 4.5f));
            while (x0 > 0 && cx - x0 <= limit && (g[row * w + x0 - 1] & 255) < 165) x0--;
            while (x1 + 1 < w && x1 - cx <= limit && (g[row * w + x1 + 1] & 255) < 165) x1++;
            if (x1 - x0 + 1 <= limit) return true;
        }
        return false;
    }
}
