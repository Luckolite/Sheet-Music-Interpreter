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
        long[] completionBudget = {4_000_000L};
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
            completeBoundaryCores(cores, gray, w, h, bottom, gap, staffs, staff, completionBudget);
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
                    if (up == null && lo != null)
                        up = finiteArmEndpoint(u, lo, gray, w, h, left, gap, true);
                    else if (lo == null && up != null)
                        lo = finiteArmEndpoint(l, up, gray, w, h, left, gap, false);
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

    /** Finish only an observed lower core that crossed the proposal band, under a complete
     * opposite-arm anchor. The old maximum two-gap opening bounds this connected search;
     * the original full-interval fit and optical checks still decide admission. */
    private static void completeBoundaryCores(
            List<Core> cores,
            byte[] gray,
            int w,
            int h,
            int bottom,
            float gap,
            List<PlayingTechniqueDetector.Staff> staffs,
            PlayingTechniqueDetector.Staff staff,
            long[] budget) {
        if (cores.size() > budget[0]) return;
        budget[0] -= cores.size();
        var original = List.copyOf(cores);
        for (Core anchor : original) {
            long anchorWork = anchor.upper().length * 6L + 64;
            if (anchorWork > budget[0]) return;
            budget[0] -= anchorWork;
            double[] up = fit(anchor.upper(), gap), lo = fit(anchor.lower(), gap);
            if (up == null || lo == null) continue;
            boolean thin = true;
            for (int x = 0; x < anchor.upper().length; x++)
                if (anchor.upper()[x] != Integer.MAX_VALUE
                        && anchor.lower()[x] - anchor.upper()[x] > 4) thin = false;
            if (!thin) continue;
            int end =
                    (int)
                            Math.ceil(
                                    Math.max(
                                                    up[1],
                                                    up[1]
                                                            + up[0]
                                                                    * (anchor.right()
                                                                            - anchor.left()))
                                            + gap * 2
                                            + 2);
            if (end <= bottom || end >= h - 1) continue;
            long staffWork = staffs.size();
            if (staffWork > budget[0]) return;
            budget[0] -= staffWork;
            boolean overlap = false;
            for (var next : staffs)
                if (next.top() > staff.bottom() && end >= Math.floor(next.top() - gap * .3))
                    overlap = true;
            if (overlap) continue;
            int margin = Math.round(gap * .5f),
                    left = Math.max(0, anchor.left() - margin),
                    right = Math.min(w - 1, anchor.right() + margin),
                    width = right - left + 1;
            for (Core seed : original) {
                long seedWork = seed.lower().length + 16L;
                if (seedWork > budget[0]) return;
                budget[0] -= seedWork;
                if (seed == anchor || seed.left() < left || seed.right() > right) continue;
                boolean clipped = false;
                for (int y : seed.lower()) if (y == bottom) clipped = true;
                if (!clipped) continue;
                long area = (long) width * (end - bottom + 1);
                long charge = area * 32 + seed.upper().length * 2L + 64;
                if (area > Integer.MAX_VALUE || charge > budget[0]) return;
                budget[0] -= charge;
                boolean[] mask = new boolean[(int) area];
                int[] queue = new int[(int) area];
                int count = 0;
                for (int x = seed.left(); x <= seed.right(); x++)
                    if (seed.lower()[x - seed.left()] == bottom) queue[count++] = x - left;
                int run = Math.max(6, Math.round(gap * 1.5f));
                for (int y = bottom + 1; y <= end; y++) {
                    int x = left;
                    while (x <= right) {
                        while (x <= right && (gray[y * w + x] & 255) >= 145) x++;
                        int start = x;
                        while (x <= right && (gray[y * w + x] & 255) < 145) x++;
                        if (x - start >= run)
                            Arrays.fill(
                                    mask,
                                    (y - bottom) * width + start - left,
                                    (y - bottom) * width + x - left,
                                    true);
                    }
                }
                int seeds = count;
                boolean cut = false;
                for (int at = 0; at < count; at++) {
                    int p = queue[at], x = p % width, y = p / width;
                    if (y > 0 && (x == 0 || x == width - 1 || y == end - bottom)) cut = true;
                    for (int dy = -2; dy <= 2; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx < 0 || xx >= width || yy <= 0 || yy > end - bottom) continue;
                            int q = yy * width + xx;
                            if (mask[q]) {
                                mask[q] = false;
                                queue[count++] = q;
                            }
                        }
                }
                if (cut || count == seeds) continue;
                int min = seed.left(), max = seed.right();
                for (int k = seeds; k < count; k++) {
                    min = Math.min(min, left + queue[k] % width);
                    max = Math.max(max, left + queue[k] % width);
                }
                int[] upper = new int[max - min + 1], lower = new int[upper.length];
                Arrays.fill(upper, Integer.MAX_VALUE);
                Arrays.fill(lower, Integer.MIN_VALUE);
                for (int x = seed.left(); x <= seed.right(); x++) {
                    upper[x - min] = seed.upper()[x - seed.left()];
                    lower[x - min] = seed.lower()[x - seed.left()];
                }
                for (int k = seeds; k < count; k++) {
                    int x = left + queue[k] % width - min, y = bottom + queue[k] / width;
                    upper[x] = Math.min(upper[x], y);
                    lower[x] = Math.max(lower[x], y);
                }
                cores.add(new Core(min, max, upper, lower));
            }
        }
    }

    /** One terminal sample may belong solely to the independently fitted opposite arm.
     * Keep the original full interval for all later raw arm/interior checks.
     */
    private static double[] finiteArmEndpoint(
            int[] observed,
            double[] other,
            byte[] gray,
            int w,
            int h,
            int left,
            float gap,
            boolean upper) {
        double tolerance = Math.max(1.5, gap * .15);
        int reach = (int) Math.ceil(tolerance);
        double[] result = null;
        for (int endpoint : new int[] {0, observed.length - 1}) {
            int value = observed[endpoint];
            if (value == Integer.MAX_VALUE || value == Integer.MIN_VALUE) continue;
            double otherAt = other[1] + other[0] * endpoint;
            if (Math.abs(value - otherAt) > tolerance) continue;
            int[] own = observed.clone();
            own[endpoint] = Integer.MAX_VALUE;
            double[] line = fit(own, gap);
            if (line == null) continue;
            double at = line[1] + line[0] * endpoint;
            if ((upper ? otherAt - at : at - otherAt) < gap * .55) continue;
            int direction = endpoint == 0 ? -1 : 1;
            int inside = endpoint - direction;
            if (inside < 0
                    || inside >= observed.length
                    || observed[inside] == Integer.MAX_VALUE
                    || observed[inside] == Integer.MIN_VALUE
                    || Math.abs(observed[inside] - line[1] - line[0] * inside) > tolerance)
                continue;
            boolean supported = false, finite = true;
            int ix = left + inside, iy = (int) Math.round(line[1] + line[0] * inside);
            if (ix < 0 || ix >= w || iy < 2 || iy >= h - 2) continue;
            for (int dy = -2; dy <= 2; dy++) supported |= (gray[(iy + dy) * w + ix] & 255) < 145;
            if (!supported) continue;
            boolean terminalOwned =
                    observedArmTerminal(
                            observed, line, gray, w, h, left, endpoint, direction, reach);
            for (int step = 0; step <= reach && finite; step++) {
                int localX = endpoint + direction * step,
                        x = left + localX,
                        y = (int) Math.round(line[1] + line[0] * localX);
                if (x < 0 || x >= w || y < 2 || y >= h - 2) {
                    finite = false;
                    break;
                }
                for (int dy = -2; dy <= 2; dy++) {
                    int grayValue = gray[(y + dy) * w + x] & 255;
                    if (grayValue < 145 && (step != 0 || !terminalOwned)
                            || step == reach && grayValue < 225) finite = false;
                }
            }
            if (!finite) continue;
            if (result != null) return null; // no arbitrary choice of terminal correspondence
            result = line;
        }
        return result;
    }

    private static boolean observedArmTerminal(
            int[] observed,
            double[] line,
            byte[] gray,
            int w,
            int h,
            int left,
            int endpoint,
            int direction,
            int reach) {
        int previousFirst = -1, previousLast = -1, previousY = 0;
        for (int step = reach; step >= 0; step--) {
            int localX = endpoint - direction * step,
                    x = left + localX,
                    y = (int) Math.round(line[1] + line[0] * localX);
            if (localX < 0 || localX >= observed.length || x < 0 || x >= w || y < 2 || y >= h - 2)
                return false;
            int first = Integer.MAX_VALUE, last = Integer.MIN_VALUE, count = 0;
            for (int dy = -2; dy <= 2; dy++)
                if ((gray[(y + dy) * w + x] & 255) < 145) {
                    first = Math.min(first, y + dy);
                    last = Math.max(last, y + dy);
                    count++;
                }
            // No split, clipped, absent, or unconnected terminal/core cross-section.
            if (count == 0 || count != last - first + 1 || first == y - 2 || last == y + 2)
                return false;
            if (previousFirst >= 0 && (first > previousLast + 1 || last < previousFirst - 1))
                return false;
            if (step > 0) {
                int own = observed[localX];
                if (own < first || own > last) return false;
            } else {
                int phase = y - previousY;
                if (previousFirst < 0
                        || first < previousFirst + phase
                        || last > previousLast + phase) return false;
            }
            previousFirst = first;
            previousLast = last;
            previousY = y;
        }
        return true;
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
