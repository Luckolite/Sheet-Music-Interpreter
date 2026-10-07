// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Bounded collinear fragments propose a wedge only after both original raw arms prove it. */
final class ShadedHairpinFragments {
    record Rail(
            PlayingTechniqueDetector.Staff staff,
            int left,
            int right,
            double slope,
            double intercept) {}

    record Shape(PlayingTechniqueDetector.Staff staff, int left, int right, int direction) {}

    private ShadedHairpinFragments() {}

    static List<Rail> pieces(
            PlayingTechniqueDetector.Staff staff, int left, int right, int[] upper, int[] lower) {
        Rail single = fragment(staff, left, right, upper, lower);
        if (single != null) return List.of(single);
        if (staff == null
                || upper == null
                || lower == null
                || upper.length != right - left + 1
                || lower.length != upper.length
                || !Float.isFinite(staff.gap())
                || staff.gap() < 3
                || left < 0
                || right < left) return List.of();
        var result = new ArrayList<Rail>();
        for (int side = 0; side < 2; side++) {
            int[] edge = side == 0 ? upper : lower;
            int start = 0;
            while (start < edge.length) {
                while (start < edge.length
                        && (edge[start] == Integer.MAX_VALUE || edge[start] == Integer.MIN_VALUE))
                    start++;
                if (start >= edge.length) break;
                int end = start;
                while (end + 1 < edge.length
                        && edge[end + 1] != Integer.MAX_VALUE
                        && edge[end + 1] != Integer.MIN_VALUE
                        && Math.abs(edge[end + 1] - edge[end]) <= Math.max(2, staff.gap() * .2))
                    end++;
                if (end - start + 1 >= staff.gap() * 2) {
                    int[] boundary = new int[end - start + 1];
                    for (int x = 0; x < boundary.length; x++)
                        boundary[x] = edge[start + x] + (side == 0 ? 1 : -1);
                    Rail rail = fragment(staff, left + start, left + end, boundary, boundary);
                    if (rail != null) result.add(rail);
                }
                start = end + 1;
            }
        }
        return List.copyOf(result);
    }

    static Rail fragment(
            PlayingTechniqueDetector.Staff staff, int left, int right, int[] upper, int[] lower) {
        if (staff == null
                || upper == null
                || lower == null
                || !Float.isFinite(staff.gap())
                || staff.gap() < 3
                || right < left
                || left < 0) return null;
        float gap = staff.gap();
        int w = right - left + 1;
        if (w < gap * 2 || upper.length != w || lower.length != w) return null;
        int n = 0;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int x = 0; x < w; x++) {
            if (upper[x] == Integer.MAX_VALUE || lower[x] - upper[x] > Math.max(3, gap * .4))
                continue;
            double y = (upper[x] + lower[x]) * .5;
            n++;
            sx += x;
            sy += y;
            sxx += (double) x * x;
            sxy += x * y;
        }
        if (n < w * .85) return null;
        double d = n * sxx - sx * sx;
        if (d <= 0) return null;
        double slope = (n * sxy - sx * sy) / d, at = (sy - slope * sx) / n;
        if (Math.abs(slope) > .15) return null;
        int capOutliers = 0, capWidth = (int) Math.ceil(gap * .75);
        for (int x = 0; x < w; x++)
            if (upper[x] != Integer.MAX_VALUE
                    && lower[x] - upper[x] <= Math.max(3, gap * .4)
                    && Math.abs((upper[x] + lower[x]) * .5 - (at + slope * x))
                            > Math.max(1.5, gap * .12)) {
                if (x >= capWidth && x < w - capWidth) return null;
                capOutliers++;
            }
        if (capOutliers > n * .05) return null;
        return new Rail(staff, left, right, slope, at - slope * left);
    }

    static List<Shape> recover(List<Rail> fragments, byte[] gray, int width, int height) {
        if (gray == null || width <= 0 || height <= 0 || gray.length != (long) width * height)
            return List.of();
        var rails = new ArrayList<Rail>(fragments);
        rails.sort(Comparator.comparingInt(Rail::left));
        boolean changed = true;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < rails.size(); i++)
                for (int j = i + 1; j < rails.size(); j++) {
                    Rail a = rails.get(i), b = rails.get(j);
                    double g = a.staff().gap();
                    if (!a.staff().equals(b.staff())
                            || b.left() - a.right() > g
                            || Math.abs(a.slope() - b.slope()) > .035) continue;
                    int near = Math.max(a.left(), Math.min(a.right(), b.left()));
                    // Both fragments carry their own bounded raster fitting error.
                    if (Math.abs(row(a, near) - row(b, near)) > Math.max(3, g * .24)) continue;
                    int overlapRight = Math.min(a.right(), b.right());
                    if (overlapRight - near > g
                            && Math.abs(row(a, overlapRight) - row(b, overlapRight))
                                    > Math.max(3, g * .24)) continue;
                    if (b.left() > a.right() + 1 && !rawGap(a, b, gray, width, height)) continue;
                    int left = Math.min(a.left(), b.left()), right = Math.max(a.right(), b.right());
                    Rail first = a.left() <= b.left() ? a : b,
                            last = a.right() >= b.right() ? a : b;
                    double slope =
                            (row(last, right) - row(first, left)) / Math.max(1, right - left);
                    Rail merged =
                            new Rail(
                                    a.staff(), left, right, slope, row(first, left) - slope * left);
                    rails.set(i, merged);
                    rails.remove(j);
                    changed = true;
                    break outer;
                }
        }
        var result = new ArrayList<Shape>();
        for (int i = 0; i < rails.size(); i++)
            for (int j = i + 1; j < rails.size(); j++) {
                Rail a = rails.get(i), b = rails.get(j);
                double g = a.staff().gap();
                if (!a.staff().equals(b.staff())
                        || a.right() - a.left() < g * 4
                        || b.right() - b.left() < g * 4) continue;
                int commonLeft = Math.max(a.left(), b.left()),
                        commonRight = Math.min(a.right(), b.right());
                if (commonRight - commonLeft
                        < Math.min(a.right() - a.left(), b.right() - b.left()) * .8) continue;
                int middle = (commonLeft + commonRight) / 2;
                if (row(a, middle) > row(b, middle)) {
                    Rail t = a;
                    a = b;
                    b = t;
                }
                double change = b.slope() - a.slope();
                if (Math.abs(change) < .008) continue;
                double closing = (a.intercept() - b.intercept()) / change;
                int left = Math.min(a.left(), b.left()),
                        right = Math.max(a.right(), b.right()),
                        direction = change > 0 ? 1 : -1;
                if (direction == 1) {
                    if (closing < left - g * 8
                            || closing > left && Math.abs(row(b, left) - row(a, left)) > g * .25)
                        continue;
                    if (closing < left && Math.abs(row(b, left) - row(a, left)) > g * .25)
                        left = (int) Math.round(closing);
                } else {
                    if (closing > right + g * 8
                            || closing < right && Math.abs(row(b, right) - row(a, right)) > g * .25)
                        continue;
                    if (closing > right && Math.abs(row(b, right) - row(a, right)) > g * .25)
                        right = (int) Math.round(closing);
                }
                int w = right - left + 1;
                if (left < 0
                        || right >= width
                        || w < g * 6
                        || Math.min(a.right() - a.left(), b.right() - b.left()) < w * .5
                        || Math.max(a.right() - a.left(), b.right() - b.left()) < w * .85) continue;
                double open =
                        direction == 1
                                ? row(b, right) - row(a, right)
                                : row(b, left) - row(a, left);
                if (open < g * .55 || open > g * 3) continue;
                double ymin =
                        Math.min(
                                Math.min(row(a, left), row(a, right)),
                                Math.min(row(b, left), row(b, right)));
                double ymax =
                        Math.max(
                                Math.max(row(a, left), row(a, right)),
                                Math.max(row(b, left), row(b, right)));
                if (!(ymin > a.staff().bottom() + g * .25 || ymax < a.staff().top() - g * .25))
                    continue;
                if (!rawPair(a, b, left, right, gray, width, height)) continue;
                Shape shape = new Shape(a.staff(), left, right, direction);
                boolean duplicate = false;
                for (Shape old : result)
                    if (old.staff().equals(shape.staff())
                            && old.direction() == direction
                            && Math.abs(old.left() - left) < g
                            && Math.abs(old.right() - right) < g) duplicate = true;
                if (!duplicate) result.add(shape);
            }
        return List.copyOf(result);
    }

    private static boolean rawPair(
            Rail upper, Rail lower, int left, int right, byte[] gray, int width, int height) {
        double g = upper.staff().gap();
        int flank = Math.max(4, (int) Math.round(g * .5)),
                radius = Math.max(2, (int) Math.round(g * .15));
        int up = 0,
                down = 0,
                open = 0,
                eligible = 0,
                shade = 0,
                cap = 0,
                upExtended = 0,
                downExtended = 0,
                upExtendedInk = 0,
                downExtendedInk = 0;
        int length = right - left + 1;
        int capWidth = Math.max(3, (int) Math.round(g * .3));
        boolean opens = row(lower, right) - row(upper, right) > row(lower, left) - row(upper, left);
        for (int x = left; x <= right; x++) {
            double ay = row(upper, x), by = row(lower, x);
            int a = (int) Math.round(ay), b = (int) Math.round(by);
            if (a - flank < 0 || b + flank >= height || a > b + 1) return false;
            int paper =
                    Math.max(
                            gray[(a - flank) * width + x] & 255,
                            gray[(b + flank) * width + x] & 255);
            if (paper >= 96 && paper < 185) shade++;
            boolean extendUp = x < upper.left() || x > upper.right(),
                    extendDown = x < lower.left() || x > lower.right();
            // Outside a measured core, both endpoint fit errors can contribute.
            int upRadius = extendUp ? Math.max(radius, (int) Math.ceil(g * .24)) : radius;
            int downRadius = extendDown ? Math.max(radius, (int) Math.ceil(g * .24)) : radius;
            boolean upInk = thinInk(gray, width, x, a, paper, upRadius, flank, g),
                    downInk = thinInk(gray, width, x, b, paper, downRadius, flank, g);
            if ((!upInk || !downInk)
                    && by - ay < g * .55
                    && joinedApexInk(gray, width, x, a, b, paper, radius, flank, g)) {
                upInk = true;
                downInk = true;
            }
            if (extendUp) {
                upExtended++;
                if (upInk) upExtendedInk++;
            }
            if (extendDown) {
                downExtended++;
                if (downInk) downExtendedInk++;
            }
            if (upInk) up++;
            if (downInk) down++;
            if ((opens ? x - left : right - x) < capWidth
                    && upInk
                    && downInk
                    && Math.abs(by - ay) <= g * .25) cap++;
            if (by - ay >= g * .55) {
                eligible++;
                if ((gray[((a + b) / 2) * width + x] & 255) >= paper - 12) open++;
            }
        }
        return shade >= length * .8
                && up >= length * .9
                && down >= length * .9
                && eligible >= length * .25
                && open >= eligible * .8
                && cap >= capWidth * .8
                && upExtendedInk >= upExtended * .95
                && downExtendedInk >= downExtended * .95;
    }

    private static boolean thinInk(
            byte[] gray, int width, int x, int y, int paper, int radius, int flank, double gap) {
        int threshold = paper - 18;
        for (int yy = y - radius; yy <= y + radius; yy++) {
            if ((gray[yy * width + x] & 255) >= threshold) continue;
            int top = yy, bottom = yy;
            while (top > y - flank && (gray[(top - 1) * width + x] & 255) < threshold) top--;
            while (bottom < y + flank && (gray[(bottom + 1) * width + x] & 255) < threshold)
                bottom++;
            if (top > y - flank
                    && bottom < y + flank
                    && bottom - top + 1 <= gap * .5 + 1
                    && Math.abs((top + bottom) * .5 - y) <= radius) return true;
        }
        return false;
    }

    private static boolean rawGap(Rail a, Rail b, byte[] gray, int width, int height) {
        double g = a.staff().gap();
        int hits = 0,
                total = 0,
                flank = Math.max(4, (int) Math.round(g * .5)),
                radius = Math.max(2, (int) Math.round(g * .15));
        for (int x = a.right() + 1; x < b.left(); x++) {
            int y = (int) Math.round((row(a, x) + row(b, x)) * .5);
            if (x < 0 || x >= width || y - flank < 0 || y + flank >= height) return false;
            int paper =
                    Math.max(
                            gray[(y - flank) * width + x] & 255,
                            gray[(y + flank) * width + x] & 255);
            total++;
            if (thinInk(gray, width, x, y, paper, radius, flank, g)) hits++;
        }
        return total > 0 && hits >= total * .8;
    }

    private static boolean joinedApexInk(
            byte[] gray,
            int width,
            int x,
            int upper,
            int lower,
            int paper,
            int radius,
            int flank,
            double gap) {
        int center = (upper + lower) / 2, threshold = paper - 18;
        if ((gray[center * width + x] & 255) >= threshold) return false;
        int top = center, bottom = center;
        while (top > upper - flank && (gray[(top - 1) * width + x] & 255) < threshold) top--;
        while (bottom < lower + flank && (gray[(bottom + 1) * width + x] & 255) < threshold)
            bottom++;
        return top > upper - flank
                && bottom < lower + flank
                && top <= upper + radius
                && bottom >= lower - radius
                && bottom - top + 1 <= Math.max(0, lower - upper) + gap * .5 + 1;
    }

    private static double row(Rail rail, int x) {
        return rail.intercept() + rail.slope() * x;
    }
}
