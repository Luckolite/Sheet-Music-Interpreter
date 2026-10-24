// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Joins two complete observed bows across independently proved printed row boundaries. */
final class PrintedSystemSlurJoins {
    record Observed(
            PrintedSlurPageCapture.Frame<? extends PrintedSlurOwners.Location> frame,
            PrintedSlurContours.Bow bow) {}

    private record FrameInfo(
            PrintedSlurPageCapture.Frame<? extends PrintedSlurOwners.Location> frame,
            int firstMeasure,
            int lastMeasure,
            int staff,
            int count,
            float firstX,
            float lastX,
            float gap,
            float row) {}

    private record Half(
            Observed observed, PrintedSlurOwners.Location owner, boolean outgoing, float phase) {}

    private record Pair(Half first, Half last) {}

    private static final class Budget {
        long left;
        boolean exhausted;

        Budget(long n) {
            if (n < 0 || n > 8_000_000)
                throw new IllegalArgumentException("Invalid continuation budget");
            left = n;
        }

        boolean take(long n) {
            if (exhausted || n < 0 || n > left) {
                exhausted = true;
                return false;
            }
            left -= n;
            return true;
        }
    }

    static List<ScoreSlurPages.Span> join(
            int page,
            List<Observed> observed,
            List<? extends PrintedSlurPageCapture.Frame<? extends PrintedSlurOwners.Location>>
                    frames,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int w,
            int h) {
        return join(page, observed, frames, notes, gray, w, h, 8_000_000L);
    }

    static List<ScoreSlurPages.Span> join(
            int page,
            List<Observed> observed,
            List<? extends PrintedSlurPageCapture.Frame<? extends PrintedSlurOwners.Location>>
                    frames,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int w,
            int h,
            long allowance) {
        Objects.requireNonNull(observed);
        Objects.requireNonNull(frames);
        Objects.requireNonNull(notes);
        if (page < 0 || gray == null || w < 3 || h < 3 || gray.length != (long) w * h)
            throw new IllegalArgumentException("Invalid continuation source");
        if (observed.size() > 4096 || frames.size() > 512) return List.of();
        long headCount = frames.stream().mapToLong(f -> f.heads().size()).sum();
        var budget = new Budget(allowance);
        if (!budget.take(observed.size() * 256L + frames.size() * 64L + headCount * 64))
            return List.of();
        var info = new IdentityHashMap<PrintedSlurPageCapture.Frame<?>, FrameInfo>();
        var seen = new HashSet<Integer>();
        for (var f : frames) {
            int first = Integer.MAX_VALUE, last = -1, staff = -1, count = -1;
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (var head : f.heads()) {
                if (head.owner() < 0
                        || head.owner() >= notes.size()
                        || !seen.add(head.owner())
                        || !Float.isFinite(head.x() + head.y())
                        || head.x() < 0
                        || head.x() >= w
                        || head.y() < 0
                        || head.y() >= h) return List.of();
                var n = notes.get(head.owner());
                if (n.staffIndex() != head.staffIndex() || n.staffCount() != head.staffCount())
                    return List.of();
                if (staff >= 0 && (staff != n.staffIndex() || count != n.staffCount()))
                    return List.of();
                staff = n.staffIndex();
                count = n.staffCount();
                first = Math.min(first, n.measureIndex());
                last = Math.max(last, n.measureIndex());
                min = Math.min(min, head.x());
                max = Math.max(max, head.x());
            }
            float[] track = f.track().at((min + max) * .5f);
            if (last < 0 || !Float.isFinite(track[0] + track[1]) || track[1] < 6 || track[1] > 64)
                continue;
            info.put(f, new FrameInfo(f, first, last, staff, count, min, max, track[1], track[0]));
        }
        var halves = new ArrayList<Half>();
        for (var item : observed) {
            var f = item.frame();
            var fi = info.get(f);
            if (fi == null) continue;
            var bow = item.bow();
            if (bow.points().size() < 2 || bow.points().size() > 2048) continue;
            if (bow.end().x() < fi.lastX() + fi.gap() * .5f
                    && bow.start().x() > fi.firstX() - fi.gap() * .5f) continue;
            if (!budget.take(f.heads().size() * 64L + bow.points().size() * 16L)) continue;
            boolean literal = true;
            int previous = -1;
            for (var point : bow.points()) {
                int x = point.x(), y = Math.round(point.y());
                if (!Float.isFinite(point.y())
                        || x <= previous
                        || x < 0
                        || x >= w
                        || y < 0
                        || y >= h
                        || (gray[y * w + x] & 255) > 205) {
                    literal = false;
                    break;
                }
                previous = x;
            }
            if (!literal || !budget.take(capCost(item, fi.gap()))) continue;
            var a =
                    PrintedSlurOwners.capOwner(
                            bow.start(), bow.above(), true, f.heads(), gray, w, h, f.track());
            var b =
                    PrintedSlurOwners.capOwner(
                            bow.end(), bow.above(), false, f.heads(), gray, w, h, f.track());
            if (a.isPresent() == b.isPresent()) continue;
            boolean outgoing = a.isPresent();
            var owner = outgoing ? a.orElseThrow() : b.orElseThrow();
            var n = notes.get(owner.owner());
            if (n.measureIndex() != (outgoing ? fi.lastMeasure() : fi.firstMeasure())) continue;
            var free = outgoing ? bow.end() : bow.start();
            if (outgoing
                    ? free.x() < fi.lastX() + fi.gap() * .5f
                    : free.x() > fi.firstX() - fi.gap() * .5f) continue;
            if (!PrintedSlurOwners.headBody(gray, w, h, owner, f.track().at(owner.x())[1]))
                continue;
            float[] at = f.track().at(free.x());
            float phase = (at[0] - free.y()) / at[1];
            if (!Float.isFinite(phase)) continue;
            halves.add(new Half(item, owner, outgoing, phase));
        }
        if (budget.exhausted
                || halves.size() > 512
                || !budget.take(halves.size() * (long) halves.size() * 16)) return List.of();
        var edges = new IdentityHashMap<PrintedSlurPageCapture.Frame<?>, int[]>();
        var pairs = new ArrayList<Pair>();
        for (var a : halves)
            if (a.outgoing())
                for (var b : halves)
                    if (!b.outgoing()) {
                        var fa = info.get(a.observed().frame());
                        var fb = info.get(b.observed().frame());
                        if (fa == fb
                                || fa.staff() != fb.staff()
                                || fa.count() != fb.count()
                                || fa.lastMeasure() + 1 != fb.firstMeasure()
                                || fb.row() - fa.row() < Math.max(fa.gap(), fb.gap()) * 4
                                || a.observed().bow().above() != b.observed().bow().above()
                                || Math.abs(a.phase() - b.phase()) > .65f) continue;
                        if (!budget.take(
                                        (fa.frame().heads().size()
                                                        + (long) fb.frame().heads().size())
                                                * 64)
                                || !budget.take(interiorCost(a, b))) continue;
                        if (!interiorHead(a, b, gray, w, h)) continue;
                        if (!edges.containsKey(fa.frame()))
                            edges.put(fa.frame(), boundaries(fa, gray, w, h, budget));
                        if (!edges.containsKey(fb.frame()))
                            edges.put(fb.frame(), boundaries(fb, gray, w, h, budget));
                        int[] ea = edges.get(fa.frame()), eb = edges.get(fb.frame());
                        if (ea == null || eb == null || ea[1] < 0 || eb[0] < 0) continue;
                        var end = a.observed().bow().end();
                        var start = b.observed().bow().start();
                        if (end.x() > ea[1]
                                || ea[1] - end.x() > fa.gap() * 2
                                || start.x() < eb[0]
                                || start.x() - eb[0] > fb.gap() * 12) continue;
                        pairs.add(new Pair(a, b));
                    }
        // Every competing half and pairing must have been examined before uniqueness
        // can be established. Resource refusal invalidates the entire optional join.
        if (budget.exhausted) return List.of();
        var uses = new IdentityHashMap<Half, Integer>();
        for (var pair : pairs) {
            uses.merge(pair.first(), 1, Integer::sum);
            uses.merge(pair.last(), 1, Integer::sum);
        }
        var result = new ArrayList<ScoreSlurPages.Span>();
        var owners = new HashSet<String>();
        for (var pair : pairs) {
            if (uses.get(pair.first()) != 1 || uses.get(pair.last()) != 1) continue;
            var a = pair.first();
            var b = pair.last();
            String key =
                    a.owner().owner() + ":" + b.owner().owner() + ":" + a.observed().bow().above();
            if (!owners.add(key)) continue;
            result.add(
                    new ScoreSlurPages.Span(
                            "printed-page-" + page + "-system-" + key,
                            new ScoreSlurPages.Anchor(page, a.owner().owner()),
                            new ScoreSlurPages.Anchor(page, b.owner().owner()),
                            a.observed().bow().above()
                                    ? ScoreSlurSpan.Placement.ABOVE
                                    : ScoreSlurSpan.Placement.BELOW,
                            ScoreSlurSpan.LineType.SOLID,
                            List.of(
                                    evidence(page, a.observed().bow(), w, h),
                                    evidence(page, b.observed().bow(), w, h))));
        }
        return List.copyOf(result);
    }

    private static long capCost(Observed item, float frameGap) {
        var f = item.frame();
        long cost = 64 * 64 * 16L; // The final independently visible owner body.
        for (var cap : List.of(item.bow().start(), item.bow().end())) {
            float gap = f.track().at(cap.x())[1];
            if (!Float.isFinite(gap) || gap < 6 || gap > 64) return Long.MAX_VALUE;
            long unit = (long) Math.ceil(gap * gap * 16 + 64);
            for (var head : f.heads()) {
                double dx = Math.abs(head.x() - cap.x()) / gap,
                        clearance =
                                (item.bow().above() ? head.y() - cap.y() : cap.y() - head.y())
                                        / gap;
                if (dx > 1.1 || clearance < -.6 || clearance > 8.5) continue;
                cost += unit;
                float edge = head.x() + (item.bow().above() ? .6f : -.6f) * gap;
                if (Math.abs(edge - cap.x()) <= gap * .4f && clearance * gap - gap >= gap * .8f)
                    cost += unit * (f.heads().size() + 1L);
            }
        }
        return cost;
    }

    private static ScoreSlurSpan.ArcEvidence evidence(
            int page, PrintedSlurContours.Bow b, int w, int h) {
        return new ScoreSlurSpan.ArcEvidence(
                page,
                b.start().x() / (float) w,
                b.start().y() / h,
                b.end().x() / (float) w,
                b.end().y() / h);
    }

    private static boolean interiorHead(Half a, Half b, byte[] gray, int w, int h) {
        for (var half : List.of(a, b)) {
            var f = half.observed().frame();
            var bow = half.observed().bow();
            for (var head : f.heads())
                if (head.owner() != half.owner().owner()
                        && head.x() > bow.start().x() + 1
                        && head.x() < bow.end().x() - 1
                        && PrintedSlurOwners.headBody(gray, w, h, head, f.track().at(head.x())[1]))
                    return true;
        }
        return false;
    }

    private static long interiorCost(Half a, Half b) {
        long cost = 0;
        for (var half : List.of(a, b))
            for (var head : half.observed().frame().heads()) {
                float gap = half.observed().frame().track().at(head.x())[1];
                if (!Float.isFinite(gap) || gap < 6 || gap > 64) return Long.MAX_VALUE;
                cost += (long) Math.ceil(gap * gap * 4 + 64);
            }
        return cost;
    }

    private static int[] boundaries(FrameInfo f, byte[] gray, int w, int h, Budget budget) {
        float gap = f.gap();
        if (!budget.take((long) Math.ceil(gap * gap * 128))) return null;
        int left = edge(f, false, gray, w, h, budget), right = edge(f, true, gray, w, h, budget);
        return new int[] {left, right};
    }

    private static int edge(FrameInfo f, boolean right, byte[] gray, int w, int h, Budget budget) {
        int
                from =
                        right
                                ? (int) Math.ceil(f.lastX() + f.gap() * .5)
                                : (int) Math.floor(f.firstX() - f.gap() * 12),
                to =
                        right
                                ? (int) Math.ceil(f.lastX() + f.gap() * 6)
                                : (int) Math.floor(f.firstX() - f.gap() * .5);
        int first = -1, last = -1;
        for (int x = Math.max(1, from); x <= Math.min(w - 2, to); x++) {
            float[] track = f.frame().track().at(x);
            int top = Math.round(track[0] - 4 * track[1]), bottom = Math.round(track[0]);
            if (top < 1 || bottom >= h - 1) continue;
            if (!budget.take((bottom - top + 1L) * 4 + 64)) return -1;
            int dark = 0;
            for (int y = top; y <= bottom; y++) if ((gray[y * w + x] & 255) < 165) dark++;
            if (dark < (bottom - top + 1) * .95f) continue;
            if (!budget.take((long) Math.ceil(f.gap() * f.gap() * 16))) return -1;
            boolean rules = true;
            for (int dx = (int) Math.ceil(f.gap() * .5); dx <= Math.ceil(f.gap() * 2); dx++) {
                int xx = x + (right ? -dx : dx);
                if (xx < 0 || xx >= w) {
                    rules = false;
                    break;
                }
                float[] inside = f.frame().track().at(xx);
                for (int rule = 0; rule < 5; rule++) {
                    int center = Math.round(inside[0] - rule * inside[1]),
                            radius = (int) Math.ceil(f.gap() * .2);
                    boolean ink = false;
                    for (int y = center - radius; y <= center + radius; y++)
                        if (y >= 0 && y < h && (gray[y * w + xx] & 255) < 165) ink = true;
                    if (!ink) rules = false;
                }
            }
            if (!rules) continue;
            boolean paper = true;
            for (int dx = (int) Math.ceil(f.gap() * .5); dx <= Math.ceil(f.gap()); dx++) {
                int xx = x + (right ? dx : -dx);
                if (xx < 0 || xx >= w) {
                    paper = false;
                    break;
                }
                for (int rule = 0; rule < 5; rule++) {
                    int center = Math.round(track[0] - rule * track[1]),
                            radius = (int) Math.ceil(f.gap() * .2);
                    for (int y = center - radius; y <= center + radius; y++)
                        if (y < 0 || y >= h || (gray[y * w + xx] & 255) < 230) paper = false;
                }
            }
            if (!paper) continue;
            if (first < 0) first = x;
            last = x;
        }
        if (first < 0 || last - first > f.gap()) return -1;
        return right ? last : first;
    }
}
