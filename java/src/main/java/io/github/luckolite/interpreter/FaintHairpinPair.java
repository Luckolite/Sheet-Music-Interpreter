// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Two independently dark straight cores may witness complete faint arms on uniform white paper. */
final class FaintHairpinPair {
    record Shape(PlayingTechniqueDetector.Staff staff, int left, int right, int direction) {}

    static List<Shape> recover(List<ShadedHairpinFragments.Rail> cores, byte[] g, int w, int h) {
        if (cores == null || g == null || w < 3 || h < 3 || g.length != (long) w * h)
            return List.of();
        var out = new ArrayList<Shape>();
        for (int i = 0; i < cores.size(); i++)
            for (int j = i + 1; j < cores.size(); j++) {
                var a = cores.get(i);
                var b = cores.get(j);
                if (a == null || b == null) continue;
                if (a.staff() == null || !a.staff().equals(b.staff())) continue;
                float gap = a.staff().gap();
                if (!Float.isFinite(gap) || gap < 6) continue;
                int commonLeft = Math.max(a.left(), b.left()),
                        commonRight = Math.min(a.right(), b.right());
                if (commonRight - commonLeft + 1 < gap * 2) continue;
                int middle = (commonLeft + commonRight) / 2;
                if (row(a, middle) > row(b, middle)) {
                    var swap = a;
                    a = b;
                    b = swap;
                }
                if (!finite(a)
                        || !finite(b)
                        || Math.abs(a.slope()) < .02
                        || Math.abs(b.slope()) < .02
                        || Math.abs(a.slope()) > .15
                        || Math.abs(b.slope()) > .15
                        || a.slope() * b.slope() >= 0) continue;
                double closing = (a.intercept() - b.intercept()) / (b.slope() - a.slope());
                if (!Double.isFinite(closing)) continue;
                int direction = b.slope() > a.slope() ? 1 : -1;
                // Rounded line fits have a bounded raster phase; every apex proposal must prove
                // real ink.
                double uncertainty = coreFitError(a, g, w, h, gap) + coreFitError(b, g, w, h, gap);
                if (!Double.isFinite(uncertainty)) continue;
                int phase =
                        Math.max(
                                1,
                                Math.min(
                                        (int) Math.ceil(gap),
                                        (int)
                                                Math.ceil(
                                                        uncertainty
                                                                / Math.abs(
                                                                        b.slope() - a.slope()))));
                for (int offset = -phase; offset <= phase; offset++) {
                    int apex = (int) Math.round(closing) + (direction > 0 ? offset : -offset);
                    int left = direction > 0 ? apex : commonLeft;
                    int right = direction > 0 ? commonRight : apex;
                    int length = right - left + 1;
                    if (left < 1
                            || right >= w - 1
                            || length < gap * 6
                            || length > gap * 12
                            || a.right() - a.left() + 1 < length * .3
                            || b.right() - b.left() + 1 < length * .3
                            || a.right() - a.left() + 1 < gap * 2
                            || b.right() - b.left() + 1 < gap * 2) continue;
                    if (direction > 0
                                    && (Math.abs(a.right() - b.right()) > gap * .5
                                            || left >= commonLeft)
                            || direction < 0
                                    && (Math.abs(a.left() - b.left()) > gap * .5
                                            || right <= commonRight)) continue;
                    double opening =
                            direction > 0
                                    ? row(b, right) - row(a, right)
                                    : row(b, left) - row(a, left);
                    if (opening < gap * .55 || opening > gap * 3) continue;
                    double low =
                            Math.min(
                                    Math.min(row(a, left), row(a, right)),
                                    Math.min(row(b, left), row(b, right)));
                    double high =
                            Math.max(
                                    Math.max(row(a, left), row(a, right)),
                                    Math.max(row(b, left), row(b, right)));
                    if (!(low > a.staff().bottom() + gap * .25
                            || high < a.staff().top() - gap * .25)) continue;
                    if (!darkCore(a, g, w, h, gap)
                            || !darkCore(b, g, w, h, gap)
                            || !completePair(a, b, left, right, g, w, h, gap, direction)) continue;
                    var shape = new Shape(a.staff(), left, right, direction);
                    boolean duplicate = false;
                    for (var old : out)
                        if (old.staff().equals(shape.staff())
                                && old.direction() == direction
                                && Math.abs(old.left() - left) < gap
                                && Math.abs(old.right() - right) < gap) duplicate = true;
                    if (!duplicate) out.add(shape);
                    break; // One physical pair has one outermost fully witnessed apex.
                }
            }
        return List.copyOf(out);
    }

    private static boolean finite(ShadedHairpinFragments.Rail r) {
        return Double.isFinite(r.slope()) && Double.isFinite(r.intercept());
    }

    private static double row(ShadedHairpinFragments.Rail r, int x) {
        return r.intercept() + r.slope() * x;
    }

    private static double coreFitError(
            ShadedHairpinFragments.Rail r, byte[] g, int w, int h, float gap) {
        if (r.left() < 1 || r.right() >= w - 1) return Double.NaN;
        int radius = Math.max(1, Math.round(gap * .15f));
        double error = .5;
        int hits = 0;
        for (int x = r.left(); x <= r.right(); x++) {
            double expected = row(r, x);
            if (!Double.isFinite(expected) || expected < radius || expected >= h - radius - 1)
                return Double.NaN;
            int center = (int) Math.round(expected);
            if (center - radius < 0 || center + radius >= h) return Double.NaN;
            int top = h, bottom = -1;
            for (int y = center - radius; y <= center + radius; y++)
                if ((g[y * w + x] & 255) < 145) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            if (bottom >= top) {
                hits++;
                error = Math.max(error, Math.abs((top + bottom) * .5 - expected) + .5);
            }
        }
        return hits >= (r.right() - r.left() + 1) * .9 ? error : Double.NaN;
    }

    private static boolean darkCore(
            ShadedHairpinFragments.Rail r, byte[] g, int w, int h, float gap) {
        int radius = Math.max(1, Math.round(gap * .15f)), hits = 0;
        if (r.left() < 1 || r.right() >= w - 1) return false;
        for (int x = r.left(); x <= r.right(); x++) {
            int e = (int) Math.round(row(r, x));
            if (e - radius < 0 || e + radius >= h) return false;
            boolean hit = false;
            for (int y = e - radius; y <= e + radius; y++)
                if ((g[y * w + x] & 255) < 145) hit = true;
            if (hit) hits++;
        }
        return hits >= (r.right() - r.left() + 1) * .9;
    }

    private static boolean completePair(
            ShadedHairpinFragments.Rail a,
            ShadedHairpinFragments.Rail b,
            int left,
            int right,
            byte[] g,
            int w,
            int h,
            float gap,
            int direction) {
        int radius = Math.max(1, Math.round(gap * .15f)),
                flank = Math.max(4, Math.round(gap * .5f)),
                n = 2 * radius + 1;
        boolean[] prevA = new boolean[n], prevB = new boolean[n];
        boolean first = true;
        int cap = 0, capWidth = Math.max(3, Math.round(gap * .3f)), eligible = 0, open = 0;
        for (int x = left; x <= right; x++) {
            int ay = (int) Math.round(row(a, x)), by = (int) Math.round(row(b, x));
            if (ay > by + 1 || ay - flank - radius < 0 || by + flank + radius >= h) return false;
            int paperA = g[(ay - flank) * w + x] & 255, paperB = g[(by + flank) * w + x] & 255;
            if (paperA < 225 || paperB < 225 || Math.abs(paperA - paperB) > 12) return false;
            // Both samples prove uniform bright paper; retain the uninked brighter reference.
            int paper = Math.max(paperA, paperB);
            // Near the actual apex, the two thin arms form one bounded run.
            int opening = Math.max(0, by - ay);
            int maxThickness = (int) Math.ceil(gap * .5 + 1 + (opening < gap * .55 ? opening : 0));
            var curA =
                    path(
                            g,
                            w,
                            x,
                            ay,
                            radius,
                            ay - flank,
                            by + flank,
                            paper,
                            maxThickness,
                            prevA,
                            first);
            var curB =
                    path(
                            g,
                            w,
                            x,
                            by,
                            radius,
                            ay - flank,
                            by + flank,
                            paper,
                            maxThickness,
                            prevB,
                            first);
            if (!any(curA) || !any(curB)) return false;
            boolean atCap = (direction > 0 ? x - left : right - x) < capWidth;
            if (atCap && Math.abs(by - ay) <= gap * .25 && sharedPixel(curA, ay, curB, by, radius))
                cap++;
            if (by - ay >= gap * .55) {
                eligible++;
                if ((g[((ay + by) / 2) * w + x] & 255) >= paper - 12) open++;
            }
            // The fitted center can advance by one row between adjacent columns.
            if (x < right) {
                int nextA = (int) Math.round(row(a, x + 1)) - ay,
                        nextB = (int) Math.round(row(b, x + 1)) - by;
                prevA = shift(curA, nextA);
                prevB = shift(curB, nextB);
            }
            first = false;
        }
        return cap >= capWidth * .8
                && eligible >= (right - left + 1) * .25
                && open >= eligible * .8;
    }

    private static boolean[] path(
            byte[] g,
            int w,
            int x,
            int center,
            int radius,
            int scanTop,
            int scanBottom,
            int paper,
            int maxThickness,
            boolean[] previous,
            boolean first) {
        boolean[] out = new boolean[previous.length];
        for (int d = -radius; d <= radius; d++) {
            int y = center + d;
            if ((g[y * w + x] & 255) > paper - 8) continue;
            int top = y, bottom = y;
            while (top > scanTop && (g[(top - 1) * w + x] & 255) <= paper - 8) top--;
            while (bottom < scanBottom && (g[(bottom + 1) * w + x] & 255) <= paper - 8) bottom++;
            if (top == scanTop || bottom == scanBottom || bottom - top + 1 > maxThickness) continue;
            int k = d + radius;
            if (first) out[k] = true;
            else
                for (int old = Math.max(0, k - 1);
                        old <= Math.min(previous.length - 1, k + 1);
                        old++) out[k] |= previous[old];
        }
        return out;
    }

    private static boolean[] shift(boolean[] previous, int advance) {
        boolean[] out = new boolean[previous.length];
        for (int k = 0; k < out.length; k++) {
            int old = k + advance;
            if (old >= 0 && old < out.length) out[k] = previous[old];
        }
        return out;
    }

    private static boolean any(boolean[] a) {
        for (boolean b : a) if (b) return true;
        return false;
    }

    private static boolean sharedPixel(boolean[] a, int ay, boolean[] b, int by, int radius) {
        for (int x = 0; x < a.length; x++)
            for (int y = 0; y < b.length; y++)
                if (a[x] && b[y] && ay + x - radius == by + y - radius) return true;
        return false;
    }
}
