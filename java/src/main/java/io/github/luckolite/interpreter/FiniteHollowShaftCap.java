// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A finite edge-rooted note shaft is not a throughgoing staff-height axis.
 * Caller retains the independent closed oval, turning contour and labelled-shaft proofs.
 */
final class FiniteHollowShaftCap {
    static final class Budget {
        private int remaining;
        private boolean refused;

        Budget(int n) {
            remaining = Math.max(0, n);
        }

        boolean debit(long n) {
            if (refused || n < 0 || n > remaining) {
                refused = true;
                return false;
            }
            remaining -= (int) n;
            return true;
        }

        boolean refused() {
            return refused;
        }

        void refuse() {
            refused = true;
        }
    }

    private FiniteHollowShaftCap() {}

    static boolean proves(
            byte[] g,
            int w,
            int h,
            int l,
            int t,
            int r,
            int b,
            float cx,
            float cy,
            float gap,
            int[] own,
            float staffTop,
            float staffBottom) {
        return proves(
                g, w, h, l, t, r, b, cx, cy, gap, own, staffTop, staffBottom, new Budget(65536));
    }

    static boolean proves(
            byte[] g,
            int w,
            int h,
            int l,
            int t,
            int r,
            int b,
            float cx,
            float cy,
            float gap,
            int[] own,
            float staffTop,
            float staffBottom,
            Budget budget) {
        if (g == null
                || w <= 0
                || h <= 0
                || g.length != (long) w * h
                || own == null
                || own.length < 3
                || budget == null
                || !Float.isFinite(cx + cy + gap + staffTop + staffBottom)
                || gap < 8
                || gap > 64
                || l < 0
                || t < 0
                || r >= w
                || b >= h
                || l > r
                || t > b
                || cx < l
                || cx > r
                || cy < t
                || cy > b
                || staffTop < 0
                || staffBottom >= h
                || staffBottom - staffTop < gap * 3.5f
                || staffBottom - staffTop > gap * 4.5f
                || Math.abs(own[2]) != 1) return false;
        int x = own[0], end = own[1], dir = own[2], flank = Math.round(gap);
        if (x - flank < 0
                || x + flank >= w
                || end < 1
                || end >= h - 1
                || (end - cy) * dir < gap * 2.5f
                || (end - cy) * dir > gap * 6
                || (cx - x) * dir < gap * .3f
                || (dir > 0
                        ? (x < l - gap * .2f || x > l + gap * .35f)
                        : (x > r + gap * .2f || x < r - gap * .35f))) return false;
        int sh = Math.round(staffBottom) - Math.round(staffTop) + 1, hh = b - t + 1;
        if (Math.round(staffTop) < 0 || Math.round(staffBottom) >= h || sh <= 0) return false;
        long charge =
                (long) (sh + hh + 3) * (3 + 2 * flank)
                        + (long) (hh + 3) * (40 + Math.round(gap * 6))
                        + Math.round(gap * 7) * 3L;
        if (charge > 65536 || !budget.debit(charge)) return false;
        // The supplied observed shaft must end in literal paper, not continue as a barline.
        int white = 0;
        for (int d = 1; d <= Math.max(3, Math.round(gap * .4f)); d++) {
            int y = end + dir * d;
            if (y < 0 || y >= h) return false;
            white = paper(g, w, x, y) ? white + 1 : 0;
            if (white >= 2) break;
        }
        if (white < 2) return false;
        int boundaryTop = Math.round(staffTop), boundaryBottom = Math.round(staffBottom);
        int[] unexplained = new int[sh + 1];
        for (int y = boundaryTop; y <= boundaryBottom; y++) {
            boolean dark = false;
            for (int dx = -1; dx <= 1; dx++) if ((g[y * w + x + dx] & 255) < 170) dark = true;
            unexplained[y - boundaryTop + 1] =
                    unexplained[y - boundaryTop]
                            + (dark && !rule(g, w, x, y, gap, staffTop, flank) ? 1 : 0);
        }
        int near =
                dir > 0
                        ? Math.min(b - 1, Math.round(cy + gap * .25f))
                        : Math.max(t + 1, Math.round(cy - gap * .25f));
        int far = dir > 0 ? t : b;
        for (int cap = far; dir > 0 ? cap < near : cap > near; cap += dir) {
            int y2 = cap + dir;
            if (cap < boundaryTop
                    || cap > boundaryBottom
                    || y2 < 0
                    || y2 >= h
                    || !paper(g, w, x, cap)
                    || !paper(g, w, x, y2)) continue;
            int root = -1;
            for (int d = 2; d <= Math.max(3, Math.round(gap * .35f)); d++) {
                int y = cap + dir * d;
                if (y < t || y > b) break;
                if ((g[y * w + x] & 255) < 170) {
                    root = y;
                    break;
                }
            }
            if (root < 0) continue;
            int blank = 0, ink = 0, total = 0;
            for (int y = root; (end - y) * dir >= 0; y += dir) {
                total++;
                boolean dark = (g[y * w + x] & 255) < 205;
                if (dark) {
                    ink++;
                    blank = 0;
                } else if (++blank > Math.max(1, Math.round(gap * .16f))) break;
            }
            if (total != (end - root) * dir + 1
                    || total < gap * 2.5f
                    || ink < total * .85f
                    || (end - root) * dir < gap * 2.5f) continue;
            boolean exteriorClear =
                    dir > 0
                            ? unexplained[cap - boundaryTop] == 0
                            : unexplained[sh] - unexplained[cap - boundaryTop + 1] == 0;
            if (exteriorClear) return true;
        }
        return false;
    }

    private static boolean paper(byte[] g, int w, int x, int y) {
        for (int dx = -1; dx <= 1; dx++) if ((g[y * w + x + dx] & 255) < 225) return false;
        return true;
    }

    private static boolean rule(byte[] g, int w, int x, int y, float gap, float top, int flank) {
        int line = Math.round((y - top) / gap);
        if (line < 0 || line > 4 || Math.abs(y - (top + line * gap)) > gap * .16f) return false;
        int left = 0, right = 0;
        for (int d = 1; d <= flank; d++) {
            if ((g[y * w + x - d] & 255) < 185) left++;
            if ((g[y * w + x + d] & 255) < 185) right++;
        }
        return left >= flank * .8f && right >= flank * .8f;
    }
}
