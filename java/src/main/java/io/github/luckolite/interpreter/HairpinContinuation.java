// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Geometry-only evidence for disconnected hairpin arms at the next system. */
final class HairpinContinuation {
    record Stroke(
            PlayingTechniqueDetector.Staff staff,
            int left,
            int right,
            double startY,
            double endY) {}

    record Wedge(
            PlayingTechniqueDetector.Staff staff,
            int left,
            int right,
            double startOpening,
            double endOpening,
            int direction) {}

    record Link(Wedge from, PlayingTechniqueDetector.Staff staff, int left, int right) {}

    static PlayingTechniqueDetector.Staff distantOwner(
            List<PlayingTechniqueDetector.Staff> staffs, int top, int bottom) {
        PlayingTechniqueDetector.Staff best = null;
        double distance = Double.POSITIVE_INFINITY, runner = Double.POSITIVE_INFINITY;
        for (var staff : staffs) {
            double d;
            if (top >= staff.bottom() + staff.gap() * .25) d = (top - staff.bottom()) / staff.gap();
            else if (bottom <= staff.top() - staff.gap() * .25)
                d = (staff.top() - bottom) / staff.gap() + .75;
            else continue;
            if (d < distance) {
                runner = distance;
                distance = d;
                best = staff;
            } else runner = Math.min(runner, d);
        }
        return distance <= 6 && runner - distance >= 1 ? best : null;
    }

    static Stroke stroke(
            PlayingTechniqueDetector.Staff staff, int left, int right, int[] upper, int[] lower) {
        int width = upper.length, count = 0;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        if (width < staff.gap() * 6) return null;
        for (int x = 0; x < width; x++) {
            if (upper[x] == Integer.MAX_VALUE) continue;
            if (lower[x] - upper[x] > Math.max(2, staff.gap() * .2)) return null;
            double y = (upper[x] + lower[x]) * .5;
            count++;
            sx += x;
            sy += y;
            sxx += (double) x * x;
            sxy += x * y;
        }
        if (count < width * .95) return null;
        double denominator = count * sxx - sx * sx;
        if (denominator <= 0) return null;
        double slope = (count * sxy - sx * sy) / denominator, intercept = (sy - slope * sx) / count;
        if (Math.abs(slope) > .06) return null;
        for (int x = 0; x < width; x++)
            if (upper[x] != Integer.MAX_VALUE
                    && Math.abs((upper[x] + lower[x]) * .5 - (intercept + slope * x))
                            > Math.max(1.5, staff.gap() * .12)) return null;
        return new Stroke(staff, left, right, intercept, intercept + slope * (width - 1));
    }

    static List<Link> links(
            List<Wedge> wedges,
            List<Stroke> strokes,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            int width,
            int height) {
        var links = new ArrayList<Link>();
        for (var wedge : wedges) {
            if (wedge.direction() != 1)
                continue; // A widening source apex proves this continuation direction.
            var owner = wedge.staff();
            double end = rightEdge(owner, measures, height) * width;
            if (!Double.isFinite(end) || Math.abs(end - wedge.right()) > owner.gap() * 2) continue;
            PlayingTechniqueDetector.Staff next = null;
            for (var staff : staffs)
                if (staff.top() > owner.top() + owner.gap() * 4
                        && staff.index() == owner.index()
                        && staff.count() == owner.count()
                        && (next == null || staff.top() < next.top())) next = staff;
            if (next == null) continue;
            double beginning = leftEdge(next, measures, height) * width;
            var possible = new ArrayList<Link>();
            for (int i = 0; i < strokes.size(); i++)
                for (int j = i + 1; j < strokes.size(); j++) {
                    var a = strokes.get(i);
                    var b = strokes.get(j);
                    if (!a.staff().equals(next) || !b.staff().equals(next)) continue;
                    if (a.startY() > b.startY()) {
                        var t = a;
                        a = b;
                        b = t;
                    }
                    double gap = next.gap();
                    if (Math.abs(a.left() - b.left()) > gap
                            || Math.abs(a.right() - b.right()) > gap) continue;
                    int left = Math.min(a.left(), b.left()), right = Math.max(a.right(), b.right());
                    if (Math.abs(left - beginning) > gap * 2) continue;
                    double start = b.startY() - a.startY(), finish = b.endY() - a.endY();
                    if (start < gap * .4 || finish > gap * 3 || finish < start - gap * .1) continue;
                    // Both nearly parallel and gradually opening continuation arms are possible.
                    if (a.endY() - a.startY() > gap * .15 || b.endY() - b.startY() < -gap * .15)
                        continue;
                    double expected = wedge.endOpening() / owner.gap() * gap;
                    if (Math.abs(start - expected) > gap * .7) continue;
                    possible.add(new Link(wedge, next, left, right));
                }
            // Ambiguous paired rules must not choose an arbitrary continuation.
            if (possible.size() == 1) links.add(possible.get(0));
        }
        return List.copyOf(links);
    }

    static boolean matchingOpenEnds(
            OpenHairpinPairs.Shape outgoing, OpenHairpinPairs.Shape incoming) {
        var first = outgoing.staff();
        var second = incoming.staff();
        double end = outgoing.last() / first.gap();
        double start = incoming.first() / second.gap();
        return outgoing.direction() == incoming.direction()
                && first.index() == second.index()
                && first.count() == second.count()
                && end >= .4 && start >= .4 && Math.abs(end - start) <= .4;
    }

    static boolean followsOnNextSystem(
            OpenHairpinPairs.Shape outgoing, OpenHairpinPairs.Shape incoming,
            List<PlayingTechniqueDetector.Staff> staffs, List<MeasureRegion> measures,
            int width, int height) {
        var first = outgoing.staff();
        var second = incoming.staff();
        if (!matchingOpenEnds(outgoing, incoming)
                || second.top() <= first.top() + first.gap() * 4
                || Math.abs(rightEdge(first, measures, height) * width - outgoing.right())
                        > first.gap() * 2
                || Math.abs(leftEdge(second, measures, height) * width - incoming.left())
                        > second.gap() * 2) return false;
        for (var staff : staffs)
            if (staff.index() == first.index() && staff.count() == first.count()
                    && staff.top() > first.top() && staff.top() < second.top()) return false;
        return true;
    }
    static double leftEdge(
            PlayingTechniqueDetector.Staff staff, List<MeasureRegion> measures, int height) {
        double result = Double.POSITIVE_INFINITY, y = (staff.top() + staff.bottom()) * .5 / height;
        for (var m : measures)
            if (y >= m.top() && y <= m.bottom()) result = Math.min(result, m.left());
        return result;
    }

    static double rightEdge(
            PlayingTechniqueDetector.Staff staff, List<MeasureRegion> measures, int height) {
        double result = Double.NEGATIVE_INFINITY, y = (staff.top() + staff.bottom()) * .5 / height;
        for (var m : measures)
            if (y >= m.top() && y <= m.bottom()) result = Math.max(result, m.right());
        return result;
    }
}
