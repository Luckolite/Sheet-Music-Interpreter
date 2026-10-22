// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Straight horizontal cores propose pairs; unmodified source pixels must prove both arms. */
final class OpenHairpinPairs {
    record Shape(PlayingTechniqueDetector.Staff staff, int left, int right, int direction) {}

    record Core(int left, int right, int[] upper, int[] lower) {}

    static List<Shape> detect(
            byte[] gray, int w, int h, List<PlayingTechniqueDetector.Staff> staffs) {
        if (gray == null || gray.length != (long) w * h || w <= 0 || h <= 0) return List.of();
        var result = new ArrayList<Shape>();
        for (var staff : staffs) {
            float gap = staff.gap();
            if (!Float.isFinite(gap) || gap < 4) continue;
            int top = Math.max(0, (int) Math.ceil(staff.bottom() + gap * .3)),
                    bottom = Math.min(h - 1, (int) Math.floor(staff.bottom() + gap * 5));
            for (var next : staffs)
                if (next.top() > staff.bottom())
                    bottom = Math.min(bottom, (int) Math.floor(next.top() - gap * .3));
            if (bottom <= top) continue;
            int bh = bottom - top + 1;
            boolean[] mask = new boolean[w * bh];
            int run = Math.max(6, Math.round(gap * 1.5f));
            for (int y = top; y <= bottom; y++) {
                int x = 0;
                while (x < w) {
                    while (x < w && (gray[y * w + x] & 255) >= 145) x++;
                    int start = x;
                    while (x < w && (gray[y * w + x] & 255) < 145) x++;
                    if (x - start >= run)
                        Arrays.fill(mask, (y - top) * w + start, (y - top) * w + x, true);
                }
            }
            var cores = new ArrayList<Core>();
            int[] queue = new int[mask.length];
            for (int p = 0; p < mask.length; p++) {
                if (!mask[p]) continue;
                mask[p] = false;
                int n = 1, start = 0;
                queue[0] = p;
                int left = p % w, right = left;
                while (start < n) {
                    int at = queue[start++], x = at % w, y = at / w;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    for (int dy = -2; dy <= 2; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx < 0 || xx >= w || yy < 0 || yy >= bh) continue;
                            int q = yy * w + xx;
                            if (mask[q]) {
                                mask[q] = false;
                                queue[n++] = q;
                            }
                        }
                }
                if (right - left + 1 < gap * 6) continue;
                int[] u = new int[right - left + 1], l = new int[u.length];
                Arrays.fill(u, Integer.MAX_VALUE);
                Arrays.fill(l, Integer.MIN_VALUE);
                for (int k = 0; k < n; k++) {
                    int x = queue[k] % w - left, y = queue[k] / w + top;
                    u[x] = Math.min(u[x], y);
                    l[x] = Math.max(l[x], y);
                }
                cores.add(new Core(left, right, u, l));
            }
            for (int i = 0; i < cores.size(); i++)
                for (int j = i; j < cores.size(); j++) {
                    Core a = cores.get(i), c = cores.get(j);
                    int left = Math.max(a.left(), c.left()), right = Math.min(a.right(), c.right());
                    if (right - left + 1 < gap * 6
                            || Math.abs(a.left() - c.left()) > gap * .5
                            || Math.abs(a.right() - c.right()) > gap * .5) continue;
                    int len = right - left + 1;
                    int[] u = new int[len], l = new int[len];
                    for (int x = 0; x < len; x++) {
                        u[x] =
                                Math.min(
                                        a.upper()[left + x - a.left()],
                                        c.upper()[left + x - c.left()]);
                        l[x] =
                                Math.max(
                                        a.lower()[left + x - a.left()],
                                        c.lower()[left + x - c.left()]);
                    }
                    double[] up = fit(u, gap), lo = fit(l, gap);
                    if (up == null || lo == null || up[0] * lo[0] >= 0) continue;
                    double first = lo[1] - up[1],
                            last = first + (lo[0] - up[0]) * (len - 1),
                            open = Math.max(first, last),
                            closed = Math.min(first, last);
                    if (closed < -.5
                            || open < gap * .55
                            || open > gap * 2
                            || open - closed < gap * .3
                            || closed > open * .75) continue;
                    boolean[] barColumns =
                            interstaffBarColumns(gray, w, h, staffs, staff, left, right);
                    int both = 0, eligible = 0, clear = 0;
                    for (int x = 0; x < len; x++) {
                        int uy = (int) Math.round(up[1] + up[0] * x),
                                ly = (int) Math.round(lo[1] + lo[0] * x);
                        if (uy < 2 || ly >= h - 2) continue;
                        boolean ui = false, li = false;
                        for (int dy = -2; dy <= 2; dy++) {
                            ui |= (gray[(uy + dy) * w + left + x] & 255) < 145;
                            li |= (gray[(ly + dy) * w + left + x] & 255) < 145;
                        }
                        if (ui && li) both++;
                        if (ly - uy >= gap * .35) {
                            int middle = gray[((uy + ly) / 2) * w + left + x] & 255;
                            // Independently owned bar ink is neither paper nor unexplained wedge
                            // ink.
                            // Its halo and every other original sample remain subject to the old
                            // law.
                            if (barColumns[x] && middle < 145) continue;
                            eligible++;
                            if (middle >= 225) clear++;
                        }
                    }
                    if (both < len * .95 || eligible < len * .25 || clear < eligible * .9) continue;
                    Shape shape = new Shape(staff, left, right, last > first ? 1 : -1);
                    boolean duplicate = false;
                    for (var old : result)
                        if (old.staff().equals(staff)
                                && Math.abs(old.left() - left) < gap
                                && Math.abs(old.right() - right) < gap) duplicate = true;
                    if (!duplicate) result.add(shape);
                }
        }
        return List.copyOf(result);
    }

    /** Same full inter-staff raw-column authority used by DynamicBarlineCrop.
     * No pixels are erased; only a dark core sample of a complete, thin bar is accounted for.
     */
    private static boolean[] interstaffBarColumns(
            byte[] gray,
            int w,
            int h,
            List<PlayingTechniqueDetector.Staff> staffs,
            PlayingTechniqueDetector.Staff upper,
            int left,
            int right) {
        boolean[] result = new boolean[right - left + 1];
        for (var lower : staffs) {
            if (lower.index() != upper.index() + 1
                    || lower.count() != upper.count()
                    || lower.top() <= upper.bottom()) continue;
            float gap = Math.max(upper.gap(), lower.gap());
            if (!Float.isFinite(gap) || gap < 4) continue;
            int top = Math.max(0, Math.round(upper.top())),
                    bottom = Math.min(h - 1, Math.round(lower.bottom()));
            if (bottom - top < gap * 8 || bottom - top > gap * 24) continue;
            boolean[] candidate = new boolean[result.length];
            int total = 0;
            for (int x = left; x <= right; x++) {
                int hits = 0;
                for (int y = top; y <= bottom; y++) if ((gray[y * w + x] & 255) < 145) hits++;
                if (hits >= (bottom - top + 1) * .92f) {
                    candidate[x - left] = true;
                    total++;
                }
            }
            if (total == 0 || total > gap * .35f) continue;
            for (int x = 0; x < result.length; x++) result[x] |= candidate[x];
        }
        return result;
    }

    private static double[] fit(int[] y, float gap) {
        int n = 0;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int x = 0; x < y.length; x++)
            if (y[x] != Integer.MAX_VALUE && y[x] != Integer.MIN_VALUE) {
                n++;
                sx += x;
                sy += y[x];
                sxx += (double) x * x;
                sxy += (double) x * y[x];
            }
        if (n < y.length * .95) return null;
        double d = n * sxx - sx * sx;
        if (d <= 0) return null;
        double slope = (n * sxy - sx * sy) / d, at = (sy - slope * sx) / n;
        if (Math.abs(slope) > .15) return null;
        for (int x = 0; x < y.length; x++)
            if (y[x] != Integer.MAX_VALUE
                    && y[x] != Integer.MIN_VALUE
                    && Math.abs(y[x] - at - slope * x) > Math.max(1.5, gap * .15)) return null;
        return new double[] {slope, at};
    }
}
