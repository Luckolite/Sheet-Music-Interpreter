// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Bounded raw-supported physical beam-strip family; no score-specific geometry.
 * Every admitted nearest-integer top cell has its OWN non-carrier core. Raw
 * pixel-cell uncertainty is applied once; continuous cap bounds are rounded
 * inward at 1/1024 pixel and receive no second raster allowance. */
final class FiniteRawBeamStrip {
    static final int PHASE = 1024;

    interface Carrier {
        default boolean contrastEnabled() {
            return false;
        }

        default void chargeContrast(long units) {}

        boolean at(int x, int y);

        default boolean unknown(int x, int y) {
            return false;
        }
    }

    record Interval(int low, int high) {
        Interval {
            if (low < 0 || high < low) throw new IllegalArgumentException();
        }
    }

    record Q(long numerator, long denominator) implements Comparable<Q> {
        Q {
            if (denominator <= 0) throw new IllegalArgumentException();
        }

        public int compareTo(Q q) {
            return Long.compare(numerator * q.denominator, q.numerator * denominator);
        }
    }

    record Slopes(Q low, Q high) {}

    record Proof(int thickness, List<Interval> columns, List<Interval> rasterTops, Slopes slopes) {
        Proof {
            columns = List.copyOf(columns);
            rasterTops = List.copyOf(rasterTops);
        }
    }

    private record Run(int top, int bottom, double base) {}

    private static final class Budget {
        int remaining = 256;
        boolean exceeded;
    }

    static Proof prove(
            byte[] gray,
            boolean[] carrier,
            int width,
            int height,
            int first,
            int last,
            double capFirst,
            double capLast,
            double gap,
            int direction,
            double offset,
            int minimumThickness) {
        if (carrier == null || gray == null || carrier.length != gray.length) return null;
        return prove(
                gray,
                (x, y) -> carrier[y * width + x],
                width,
                height,
                first,
                last,
                capFirst,
                capLast,
                gap,
                direction,
                offset,
                minimumThickness);
    }

    static Proof prove(
            byte[] gray,
            Carrier carrier,
            int width,
            int height,
            int first,
            int last,
            double capFirst,
            double capLast,
            double gap,
            int direction,
            double offset,
            int minimumThickness) {
        if (gray == null
                || carrier == null
                || width < 1
                || height < 1
                || height > 1000000
                || (long) width * height != gray.length
                || first < 0
                || last >= width
                || last - first + 1 < 2
                || last - first + 1 > 512
                || !Double.isFinite(capFirst)
                || !Double.isFinite(capLast)
                || !Double.isFinite(gap)
                || gap < 3
                || gap > 64
                || !Double.isFinite(offset)
                || offset < 0
                || offset > gap * 8
                || (direction != -1 && direction != 1)
                || minimumThickness < 0
                || minimumThickness > 64) return null;
        java.util.function.LongConsumer contrastCharge = null;
        if (carrier.contrastEnabled()) {
            carrier.chargeContrast(32);
            contrastCharge = carrier::chargeContrast;
        }
        int coreMinimum = Math.max(2, (int) Math.ceil(gap * .18));
        int minimum = Math.max(coreMinimum, minimumThickness), maximum = (int) Math.floor(gap);
        var runs = new ArrayList<List<Run>>();
        for (int x = first; x <= last; x++) {
            double base =
                    capFirst
                            + (capLast - capFirst) * (x - first) / (last - first)
                            - direction * offset;
            int low = Math.max(0, (int) Math.floor(base - gap * 1.5)),
                    high = Math.min(height - 1, (int) Math.ceil(base + gap * 1.5));
            var column = new ArrayList<Run>();
            int start = -1;
            for (int y = low; y <= high + 1; y++) {
                boolean ink =
                        y <= high
                                && ((gray[y * width + x] & 255) < 165
                                        || contrastCharge != null
                                                && PrintedBeamRawContrast.faint(
                                                        gray,
                                                        width,
                                                        height,
                                                        x,
                                                        y,
                                                        gap,
                                                        false,
                                                        contrastCharge));
                if (ink) {
                    if (start < 0) start = y;
                } else if (start >= 0) {
                    int bottom = y - 1, count = 0;
                    if (start > low && bottom < high && bottom - start + 1 >= coreMinimum) {
                        for (int row = start; row <= bottom; row++)
                            if (!carrier.at(x, row) && !carrier.unknown(x, row)) count++;
                        if (count >= coreMinimum) column.add(new Run(start, bottom, base));
                    }
                    start = -1;
                }
            }
            runs.add(column);
        }
        for (int k = maximum; k >= minimum; k--) {
            var choices = new ArrayList<List<Interval>>();
            var identities = new ArrayList<HashMap<Interval, Interval>>();
            for (int columnIndex = 0; columnIndex < runs.size(); columnIndex++) {
                var options = new ArrayList<Interval>();
                var rawPositions = new HashMap<Interval, Interval>();
                int x = first + columnIndex;
                for (var r : runs.get(columnIndex)) {
                    int spanStart = -1;
                    for (int t = r.top(); t <= r.bottom() - k + 2; t++) {
                        int count = 0;
                        if (t <= r.bottom() - k + 1)
                            for (int y = t; y < t + k; y++)
                                if (!carrier.at(x, y) && !carrier.unknown(x, y)) count++;
                        boolean eligible = t <= r.bottom() - k + 1 && count >= coreMinimum;
                        if (eligible) {
                            if (spanStart < 0) spanStart = t;
                        } else if (spanStart >= 0) {
                            int spanEnd = t - 1, shift = direction > 0 ? k - 1 : 0;
                            int lo =
                                    Math.max(
                                            PHASE * spanStart - PHASE / 2,
                                            (int) Math.ceil(PHASE * (r.base() - gap * .3 - shift)));
                            int hi =
                                    Math.min(
                                            PHASE * spanEnd + PHASE / 2 - 1,
                                            (int)
                                                    Math.floor(
                                                            PHASE * (r.base() + gap * .3 - shift)));
                            if (lo <= hi) {
                                var interval = new Interval(lo, hi);
                                options.add(interval);
                                rawPositions.put(interval, new Interval(spanStart, spanEnd));
                            }
                            spanStart = -1;
                        }
                    }
                }
                if (options.isEmpty()) break;
                choices.add(options);
                identities.add(rawPositions);
            }
            if (choices.size() != runs.size()) continue;
            var solution = unique(choices);
            if (solution != null) {
                var ids = new ArrayList<Integer>();
                var rawBounds = new ArrayList<Interval>();
                for (int i = 0; i < solution.size(); i++) {
                    ids.add(i);
                    rawBounds.add(identities.get(i).get(solution.get(i)));
                }
                var scaled = feasible(ids, solution);
                var slopes =
                        new Slopes(
                                new Q(scaled.low().numerator(), scaled.low().denominator() * PHASE),
                                new Q(
                                        scaled.high().numerator(),
                                        scaled.high().denominator() * PHASE));
                return new Proof(k, solution, rawBounds, slopes);
            }
        }
        return null;
    }

    /** Returned constraints and input bounds use PHASE units. */
    static Slopes feasible(List<Integer> indices, List<Interval> intervals) {
        if (indices == null
                || intervals == null
                || indices.size() != intervals.size()
                || indices.size() > 512) return null;
        Q sl = new Q(-4L * PHASE, 5), sh = new Q(4L * PHASE, 5);
        for (int i = 0; i < indices.size(); i++) {
            if (indices.get(i) < 0
                    || indices.get(i) > 511
                    || intervals.get(i).high() > 1000000L * PHASE) return null;
            for (int j = i + 1; j < indices.size(); j++) {
                int denominator = indices.get(j) - indices.get(i);
                if (denominator <= 0) return null;
                Q a = new Q((long) intervals.get(j).low() - intervals.get(i).high(), denominator),
                        b =
                                new Q(
                                        (long) intervals.get(j).high() - intervals.get(i).low(),
                                        denominator);
                if (a.compareTo(sl) > 0) sl = a;
                if (b.compareTo(sh) < 0) sh = b;
                if (sl.compareTo(sh) > 0) return null;
            }
        }
        return new Slopes(sl, sh);
    }

    static List<Interval> unique(List<List<Interval>> choices) {
        if (choices == null || choices.size() < 2 || choices.size() > 512) return null;
        for (var o : choices) if (o == null || o.isEmpty() || o.size() > 16) return null;
        var solutions = new ArrayList<List<Interval>>();
        var budget = new Budget();
        visit(choices, solutions, budget);
        return !budget.exceeded && solutions.size() == 1 ? List.copyOf(solutions.get(0)) : null;
    }

    private static void visit(
            List<List<Interval>> input, List<List<Interval>> solutions, Budget budget) {
        if (--budget.remaining < 0) {
            budget.exceeded = true;
            return;
        }
        var options = new ArrayList<List<Interval>>();
        for (var o : input) options.add(new ArrayList<>(o));
        boolean changed;
        do {
            changed = false;
            var fixed = new ArrayList<Integer>();
            var intervals = new ArrayList<Interval>();
            for (int i = 0; i < options.size(); i++)
                if (options.get(i).size() == 1) {
                    fixed.add(i);
                    intervals.add(options.get(i).get(0));
                }
            if (feasible(fixed, intervals) == null) return;
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).size() == 1) continue;
                var kept = new ArrayList<Interval>();
                for (var candidate : options.get(i)) {
                    var ids = new ArrayList<Integer>();
                    var bounds = new ArrayList<Interval>();
                    for (int j = 0; j < options.size(); j++) {
                        if (j == i) {
                            ids.add(j);
                            bounds.add(candidate);
                        } else if (options.get(j).size() == 1) {
                            ids.add(j);
                            bounds.add(options.get(j).get(0));
                        }
                    }
                    if (feasible(ids, bounds) != null) kept.add(candidate);
                }
                if (kept.isEmpty()) return;
                if (kept.size() < options.get(i).size()) {
                    options.set(i, kept);
                    changed = true;
                }
            }
        } while (changed);
        int branch = -1;
        for (int i = 0; i < options.size(); i++)
            if (options.get(i).size() > 1
                    && (branch < 0 || options.get(i).size() < options.get(branch).size()))
                branch = i;
        if (branch < 0) {
            var answer = new ArrayList<Interval>();
            for (var o : options) answer.add(o.get(0));
            solutions.add(answer);
            return;
        }
        for (var candidate : options.get(branch)) {
            var child = new ArrayList<List<Interval>>();
            for (var o : options) child.add(new ArrayList<>(o));
            child.set(branch, List.of(candidate));
            visit(child, solutions, budget);
            if (solutions.size() > 1 || budget.exceeded) return;
        }
    }
}
