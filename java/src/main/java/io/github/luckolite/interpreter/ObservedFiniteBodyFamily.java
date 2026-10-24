// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Universal whole finite body proof across all raster-feasible widths and edges. */
final class ObservedFiniteBodyFamily {
    private ObservedFiniteBodyFamily() {}

    static boolean qualifies(
            byte[] gray,
            int width,
            int height,
            List<Map<Integer, int[]>> bodies,
            Map<Integer, Set<Integer>> staff,
            int gap,
            long workLimit) {
        if (bodies == null
                || bodies.size() < 2
                || bodies.size() > 3
                || staff == null
                || gap < 5
                || gap > 64
                || workLimit < 0) return false;
        List<List<FiniteParallelBeamBand>> bands = new ArrayList<>();
        List<List<Integer>> widths = new ArrayList<>();
        int left = Integer.MIN_VALUE, right = Integer.MAX_VALUE, shortest = Integer.MAX_VALUE;
        long remaining = workLimit;
        for (var body : bodies) {
            if (body == null || body.size() < 2 || body.size() > 512) return false;
            long preparation = 192L * body.size();
            if (preparation > remaining) return false;
            remaining -= preparation;
            var ordered = new TreeMap<>(body);
            if ((long) ordered.lastKey() - ordered.firstKey() + 1 != ordered.size()) return false;
            left = Math.max(left, ordered.firstKey());
            right = Math.min(right, ordered.lastKey());
            shortest = Math.min(shortest, ordered.size());
            List<FiniteParallelBeamBand> alternatives = new ArrayList<>();
            List<Integer> thicknesses = new ArrayList<>();
            // Existing extractor width cap plus its established one-pixel edge uncertainty.
            int maximum = Math.min(64, (int) Math.rint(.75 * gap) + 1);
            for (int thickness = 3; thickness <= maximum; thickness++) {
                var result =
                        ObservedOccludedBodyDomain.derive(
                                gray, width, height, ordered, staff, thickness, remaining);
                remaining -= result.work();
                if (result.status() == ObservedOccludedBodyDomain.Status.BUDGET_EXHAUSTED)
                    return false;
                if (result.status() == ObservedOccludedBodyDomain.Status.PROVED) {
                    alternatives.add(result.band());
                    thicknesses.add(thickness);
                } else if (result.status() != ObservedOccludedBodyDomain.Status.INFEASIBLE)
                    return false;
            }
            if (alternatives.isEmpty()) return false;
            bands.add(alternatives);
            widths.add(thicknesses);
        }
        if (right <= left || right - left + 1 < shortest - 2) return false;
        long charge = 0;
        try {
            for (int i = 0; i < bodies.size(); i++) {
                long n = bodies.get(i).size();
                charge =
                        Math.addExact(
                                charge,
                                Math.multiplyExact(
                                        bands.get(i).size(),
                                        Math.multiplyExact(
                                                96L,
                                                Math.multiplyExact(n, Math.multiplyExact(n, n)))));
            }
        } catch (ArithmeticException overflow) {
            return false;
        }
        if (charge > remaining) return false;
        return FiniteParallelBeamBand.universallyCoherentFiniteAlternatives(
                bands, widths, left, right, gap);
    }
}
