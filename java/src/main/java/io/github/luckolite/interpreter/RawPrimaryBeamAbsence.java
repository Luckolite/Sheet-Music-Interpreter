// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Exhaustive bounded primary-ribbon possibility. UNKNOWN dark ink is always
 * possible non-carrier core; only infeasibility of every family proves absence.
 * This deliberately over-approximates contact and quantized endpoint cells. */
final class RawPrimaryBeamAbsence {
    enum State {
        POSSIBLE,
        IMPOSSIBLE,
        UNKNOWN
    }

    record Interval(long low, long high) {}

    record Certificate(
            int side,
            int rawPrefixColumns,
            int checkedRivalShafts,
            int checkedOverlappingRivalShafts) {}

    private final byte[] raw;
    private final int w, h;
    private final FiniteRawBeamStrip.Carrier carrier;
    private final PrintedIsolatedFlags.Budget budget;

    RawPrimaryBeamAbsence(
            byte[] raw,
            int w,
            int h,
            FiniteRawBeamStrip.Carrier carrier,
            PrintedIsolatedFlags.Budget budget) {
        this.raw = raw;
        this.w = w;
        this.h = h;
        this.carrier = carrier;
        this.budget = budget;
    }

    Optional<List<Certificate>> prove(
            PrintedBeamGeometry.Column shaft, List<PrintedBeamGeometry.Column> columns) {
        double gap = shaft.gap();
        int
                minimumEnd =
                        shaft.shaftColumns().stream()
                                .mapToInt(PrintedBeamGeometry.End::endY)
                                .min()
                                .orElse(shaft.cap()),
                maximumEnd =
                        shaft.shaftColumns().stream()
                                .mapToInt(PrintedBeamGeometry.End::endY)
                                .max()
                                .orElse(shaft.cap());
        if (minimumEnd - gap * 1.3 < 0 || maximumEnd + gap * 1.3 >= h) return Optional.empty();
        // Classify the COMPLETE rival domain before side-dependent separation.
        // Overlapping envelopes do not belong exclusively to either side and
        // must receive their one raw-contact exclusion here, never be skipped.
        int overlapping = 0;
        for (var rival : columns) {
            budget.take(1);
            if (rival.id() == shaft.id()) continue;
            if (rival.left() <= shaft.right() && shaft.left() <= rival.right()) {
                overlapping++;
                if (shortRawContact(shaft, rival) != State.IMPOSSIBLE) return Optional.empty();
            }
        }
        var certificates = new ArrayList<Certificate>();
        for (int side : new int[] {-1, 1}) {
            int origin = side < 0 ? shaft.left() : shaft.right(),
                    distance = (int) Math.ceil(gap * 2.4),
                    checked = 0;
            if (origin + side * distance < 0 || origin + side * distance >= w)
                return Optional.empty();
            for (var rival : columns) {
                budget.take(1);
                if (rival.id() == shaft.id()) continue;
                int edge = side < 0 ? rival.right() : rival.left(), dx = (edge - origin) * side;
                if (dx <= 0) continue;
                checked++;
                if (possible(shaft, side, dx, rival) != State.IMPOSSIBLE) return Optional.empty();
            }
            if (possible(shaft, side, distance, null) != State.IMPOSSIBLE) return Optional.empty();
            certificates.add(new Certificate(side, distance - 1, checked, overlapping));
        }
        return Optional.of(List.copyOf(certificates));
    }

    private Interval anchor(PrintedBeamGeometry.Column shaft, int k) {
        int min = shaft.cap(), max = shaft.cap();
        if (!shaft.shaftColumns().isEmpty()) {
            min = Integer.MAX_VALUE;
            max = Integer.MIN_VALUE;
            for (var e : shaft.shaftColumns()) {
                min = Math.min(min, e.endY());
                max = Math.max(max, e.endY());
            }
        }
        int shift = shaft.direction() > 0 ? k - 1 : 0;
        return new Interval(
                (long) Math.floor(1024 * (min - shaft.gap() * .3 - shift)),
                (long) Math.ceil(1024 * (max + shaft.gap() * .3 - shift)));
    }

    State possible(
            PrintedBeamGeometry.Column shaft,
            int side,
            int distance,
            PrintedBeamGeometry.Column rival) {
        if (rival != null) return pairPossible(shaft, side, distance, rival);
        int origin = side < 0 ? shaft.left() : shaft.right();
        double gap = shaft.gap();
        int minimum = Math.max(2, (int) Math.ceil(gap * .18)),
                maximum = (int) Math.floor(gap),
                last = distance;
        if (distance < 2 || origin + side * distance < 0 || origin + side * distance >= w)
            return State.UNKNOWN;
        for (int k = minimum; k <= maximum; k++) {
            budget.take(1);
            Interval a = anchor(shaft, k);
            var options = new ArrayList<List<Interval>>();
            options.add(List.of(a));
            for (int i = 1; i <= distance; i++)
                options.add(List.of(new Interval((-h - 64L) * 1024, (2 * h + 64L) * 1024)));
            boolean possible = true;
            for (int dx = 2; dx <= last; dx++) {
                int x = origin + side * dx,
                        low = (int) Math.floor(a.low() / 1024.0 - .8 * dx) - 1,
                        high = (int) Math.ceil(a.high() / 1024.0 + .8 * dx + k) + 1,
                        start = Integer.MIN_VALUE;
                var found = new ArrayList<Interval>();
                for (int y = low; y <= high + 1; y++) {
                    budget.take(1);
                    boolean dark = y <= high && (y < 0 || y >= h || (raw[y * w + x] & 255) < 165);
                    if (dark) {
                        if (start == Integer.MIN_VALUE) start = y;
                    } else if (start != Integer.MIN_VALUE) {
                        int end = y - 1, first = Integer.MIN_VALUE, previous = Integer.MIN_VALUE;
                        for (int top = start; top <= end - k + 1; top++) {
                            int core = 0;
                            for (int yy = top; yy < top + k; yy++) {
                                budget.take(1);
                                if (yy < 0
                                        || yy >= h
                                        || (!carrier.at(x, yy) || carrier.unknown(x, yy))) core++;
                            }
                            if (core >= minimum) {
                                if (first == Integer.MIN_VALUE) first = top;
                                previous = top;
                            } else if (first != Integer.MIN_VALUE) {
                                found.add(
                                        new Interval(1024L * first - 512, 1024L * previous + 512));
                                first = Integer.MIN_VALUE;
                            }
                        }
                        if (first != Integer.MIN_VALUE)
                            found.add(new Interval(1024L * first - 512, 1024L * previous + 512));
                        start = Integer.MIN_VALUE;
                    }
                }
                if (found.isEmpty()) {
                    possible = false;
                    break;
                }
                if (found.size() > 16) return State.UNKNOWN;
                options.set(dx, found);
            }
            if (possible) {
                State state = family(options, new int[] {256});
                if (state != State.IMPOSSIBLE) return state;
            }
        }
        return State.IMPOSSIBLE;
    }

    /** A projection/superset of the ACTUAL pair geometry family. All supplied
     * endpoints, including distant mixed-gap endpoints, are tested. The pair
     * mean controls thickness, core minimum AND every continuous cap collar.
     * Constraints live at the same interior columns as positive geometry;
     * extrapolated shaft-cap constraints must never narrow that family. */
    private State pairPossible(
            PrintedBeamGeometry.Column shaft,
            int side,
            int distance,
            PrintedBeamGeometry.Column rival) {
        if (distance < 5) return shortRawContact(shaft, rival);
        int origin = side < 0 ? shaft.left() : shaft.right();
        if (origin + side * distance < 0 || origin + side * distance >= w) return State.UNKNOWN;
        double mean = (shaft.gap() + rival.gap()) * .5;
        int minimum = Math.max(2, (int) Math.ceil(mean * .18)), maximum = (int) Math.floor(mean);
        int last = distance - 2, prefix = Math.min(last, (int) Math.ceil(shaft.gap() * 2.4));
        // Positive geometry uses the left column's direction. Opposed stems
        // are over-approximated by retaining BOTH directions, never selected.
        int[] directions =
                shaft.direction() == rival.direction()
                        ? new int[] {shaft.direction()}
                        : new int[] {-1, 1};
        for (int direction : directions)
            for (int k = minimum; k <= maximum; k++) {
                budget.take(1);
                int shift = direction > 0 ? k - 1 : 0;
                var positions = new ArrayList<Integer>();
                var options = new ArrayList<List<Interval>>();
                boolean possible = true;
                for (int dx = 2; dx <= prefix; dx++) {
                    int x = origin + side * dx;
                    double cap = shaft.cap() + (rival.cap() - shaft.cap()) * (double) dx / distance;
                    long lowCap = (long) Math.floor(1024 * (cap - .3 * mean - shift)),
                            highCap = (long) Math.ceil(1024 * (cap + .3 * mean - shift));
                    int low = (int) Math.floor(lowCap / 1024.0) - 1,
                            high = (int) Math.ceil(highCap / 1024.0 + k) + 1,
                            start = Integer.MIN_VALUE;
                    var found = new ArrayList<Interval>();
                    for (int y = low; y <= high + 1; y++) {
                        budget.take(1);
                        boolean dark =
                                y <= high && (y < 0 || y >= h || (raw[y * w + x] & 255) < 165);
                        if (dark) {
                            if (start == Integer.MIN_VALUE) start = y;
                        } else if (start != Integer.MIN_VALUE) {
                            int end = y - 1;
                            for (int top = start; top <= end - k + 1; top++) {
                                int core = 0;
                                for (int yy = top; yy < top + k; yy++) {
                                    budget.take(1);
                                    if (yy < 0
                                            || yy >= h
                                            || (!carrier.at(x, yy) || carrier.unknown(x, yy)))
                                        core++;
                                }
                                if (core < minimum) continue;
                                long lo = Math.max(1024L * top - 512, lowCap),
                                        hi = Math.min(1024L * top + 512, highCap);
                                if (lo <= hi) {
                                    if (!found.isEmpty()
                                            && lo <= found.get(found.size() - 1).high() + 1) {
                                        var prev = found.remove(found.size() - 1);
                                        found.add(
                                                new Interval(
                                                        prev.low(), Math.max(prev.high(), hi)));
                                    } else found.add(new Interval(lo, hi));
                                }
                            }
                            start = Integer.MIN_VALUE;
                        }
                    }
                    if (found.isEmpty()) {
                        possible = false;
                        break;
                    }
                    if (found.size() > 16) return State.UNKNOWN;
                    positions.add(dx);
                    options.add(found);
                }
                if (!possible) continue;
                if (last > prefix) {
                    double cap =
                            shaft.cap() + (rival.cap() - shaft.cap()) * (double) last / distance;
                    positions.add(last);
                    options.add(
                            List.of(
                                    new Interval(
                                            (long) Math.floor(1024 * (cap - .3 * mean - shift)),
                                            (long) Math.ceil(1024 * (cap + .3 * mean - shift)))));
                }
                State state = familyAt(options, positions, new int[] {256});
                if (state != State.IMPOSSIBLE) return state;
            }
        return State.IMPOSSIBLE;
    }

    /** At sub-interior separations, do not invent a ribbon or a lane veto.
     * Every actual full-ribbon/contact witness requires a literal raw8 path
     * between the two authenticated backbones inside their full x envelope.
     * All raw ink, including carrier and UNKNOWN, is possible contact here. */
    private State shortRawContact(PrintedBeamGeometry.Column a, PrintedBeamGeometry.Column b) {
        int left = Math.min(a.left(), b.left()),
                right = Math.max(a.right(), b.right()),
                stride = right - left + 1;
        var visited = new HashSet<Long>();
        var queue = new ArrayDeque<Long>();
        for (var end : a.shaftColumns())
            for (int y = Math.min(end.endY(), a.shaftStart());
                    y <= Math.max(end.endY(), a.shaftStart());
                    y++) {
                budget.take(1);
                if ((raw[y * w + end.x()] & 255) < 165) {
                    long key = (long) y * stride + end.x() - left;
                    if (visited.add(key)) queue.add(key);
                }
            }
        while (!queue.isEmpty()) {
            long key = queue.remove();
            int x = left + (int) (key % stride), y = (int) (key / stride);
            if (b.owns(x, y)) return State.UNKNOWN;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx, yy = y + dy;
                    if (dx == 0 && dy == 0 || xx < left || xx > right || yy < 0 || yy >= h)
                        continue;
                    budget.take(1);
                    long next = (long) yy * stride + xx - left;
                    if (visited.add(next) && (raw[yy * w + xx] & 255) < 165) queue.add(next);
                }
        }
        return State.IMPOSSIBLE;
    }

    private boolean fits(List<Integer> positions, List<Interval> intervals) {
        long ln = -4096, ld = 5, un = 4096, ud = 5;
        for (int i = 0; i < positions.size(); i++) {
            budget.take(1);
            if (intervals.get(i).low() > intervals.get(i).high()) return false;
            for (int j = i + 1; j < positions.size(); j++) {
                budget.take(1);
                long d = positions.get(j) - positions.get(i),
                        a = intervals.get(j).low() - intervals.get(i).high(),
                        b = intervals.get(j).high() - intervals.get(i).low();
                if (d < 0) {
                    d = -d;
                    long aa = -b;
                    b = -a;
                    a = aa;
                }
                if (d == 0) {
                    if (a > 0 || b < 0) return false;
                    continue;
                }
                if (a * ld > ln * d) {
                    ln = a;
                    ld = d;
                }
                if (b * ud < un * d) {
                    un = b;
                    ud = d;
                }
                if (ln * ud > un * ld) return false;
            }
        }
        return true;
    }

    private State family(List<List<Interval>> input, int[] remaining) {
        var positions = new ArrayList<Integer>();
        for (int i = 0; i < input.size(); i++) positions.add(i);
        return familyAt(input, positions, remaining);
    }

    private State familyAt(List<List<Interval>> input, List<Integer> positions, int[] remaining) {
        budget.take(1);
        if (--remaining[0] < 0) return State.UNKNOWN;
        var options = new ArrayList<List<Interval>>();
        for (var o : input) options.add(new ArrayList<>(o));
        while (true) {
            var ids = new ArrayList<Integer>();
            var intervals = new ArrayList<Interval>();
            for (int i = 0; i < options.size(); i++)
                if (options.get(i).size() == 1) {
                    ids.add(positions.get(i));
                    intervals.add(options.get(i).get(0));
                }
            if (!fits(ids, intervals)) return State.IMPOSSIBLE;
            boolean changed = false;
            for (int i = 0; i < options.size(); i++)
                if (options.get(i).size() > 1) {
                    var kept = new ArrayList<Interval>();
                    for (var option : options.get(i)) {
                        var p = new ArrayList<>(ids);
                        var v = new ArrayList<>(intervals);
                        p.add(positions.get(i));
                        v.add(option);
                        if (fits(p, v)) kept.add(option);
                    }
                    if (kept.isEmpty()) return State.IMPOSSIBLE;
                    if (kept.size() < options.get(i).size()) {
                        options.set(i, kept);
                        changed = true;
                    }
                }
            if (!changed) break;
        }
        int branch = -1;
        for (int i = 0; i < options.size(); i++)
            if (options.get(i).size() > 1
                    && (branch < 0 || options.get(i).size() < options.get(branch).size()))
                branch = i;
        if (branch < 0) return State.POSSIBLE;
        for (var option : options.get(branch)) {
            var child = new ArrayList<>(options);
            child.set(branch, List.of(option));
            State state = familyAt(child, positions, remaining);
            if (state != State.IMPOSSIBLE) return state;
        }
        return State.IMPOSSIBLE;
    }
}
