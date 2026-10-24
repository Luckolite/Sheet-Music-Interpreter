// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Source-free physical family proof using complete finite edges, not search ticks. */
final class ObservedParallelBodyFamily {
    private ObservedParallelBodyFamily() {}

    static long workUpperBound(
            List<MaximalRawBarExtractor.Body> bodies, int width, Set<Integer> staffPixels) {
        if (bodies == null
                || bodies.size() < 2
                || bodies.size() > 3
                || width <= 0
                || staffPixels == null
                || staffPixels.isEmpty()) return Long.MAX_VALUE;
        long total = staffPixels.size(), spans = 0, members = 0;
        try {
            for (MaximalRawBarExtractor.Body body : bodies) {
                long n = body.length();
                if (n < 2
                        || n > 512
                        || body.firstX() < 0
                        || body.lastX() >= width
                        || (long) body.lastX() - body.firstX() + 1 != n) return Long.MAX_VALUE;
                spans = Math.addExact(spans, n);
                members = Math.addExact(members, body.rawPixels().size());
                // Per-body map construction/sorting, thickness median, all finite
                // edge-pair constraints, and repeated staff-contact probes.
                total = Math.addExact(total, Math.multiplyExact(n, n));
            }
            total = Math.addExact(total, Math.multiplyExact(spans, bodies.size() * 8L));
            return Math.addExact(total, members);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    static boolean qualifies(
            List<Map<Integer, int[]>> bodies,
            Map<Integer, Set<Integer>> witnessedStaffRowsByX,
            int gap) {
        if (bodies == null
                || bodies.size() < 2
                || bodies.size() > 3
                || witnessedStaffRowsByX == null
                || gap < 5
                || gap > 64) return false;
        List<ObservedBodySlopeDomain.Result> domains = new ArrayList<>();
        int commonLeft = Integer.MIN_VALUE,
                commonRight = Integer.MAX_VALUE,
                shortestSpan = Integer.MAX_VALUE;
        for (Map<Integer, int[]> body : bodies) {
            if (body == null || body.size() < 2) return false;
            TreeMap<Integer, int[]> ordered = new TreeMap<>(body);
            if ((long) ordered.lastKey() - ordered.firstKey() + 1 != ordered.size()) return false;
            commonLeft = Math.max(commonLeft, ordered.firstKey());
            commonRight = Math.min(commonRight, ordered.lastKey());
            shortestSpan = Math.min(shortestSpan, ordered.size());
            int thickness =
                    ObservedBodySlopeDomain.lowerMedianObservedThickness(
                            ordered, witnessedStaffRowsByX);
            if (thickness <= 0) return false;
            ObservedBodySlopeDomain.Result domain =
                    ObservedBodySlopeDomain.derive(ordered, witnessedStaffRowsByX, thickness);
            if (domain.status() != ObservedBodySlopeDomain.Status.PROVED) return false;
            domains.add(domain);
        }
        if (commonRight < commonLeft || commonRight - commonLeft + 1 < shortestSpan - 2)
            return false;
        if (!ObservedBodySlopeDomain.sharesSlope(domains)) return false;
        double minSpacing = 0.5 * gap, maxSpacing = 1.1 * gap, maxImbalance = 0.2 * gap;
        for (int x = commonLeft; x <= commonRight; x++) {
            List<Double> centers = new ArrayList<>(bodies.size());
            for (Map<Integer, int[]> body : bodies) {
                int[] edge = body.get(x);
                if (edge == null || edge.length != 2 || edge[1] < edge[0]) return false;
                centers.add((edge[0] + edge[1]) / 2.0);
            }
            Collections.sort(centers);
            double priorSpacing = centers.get(1) - centers.get(0);
            if (priorSpacing < minSpacing || priorSpacing > maxSpacing) return false;
            for (int i = 2; i < centers.size(); i++) {
                double spacing = centers.get(i) - centers.get(i - 1);
                if (spacing < minSpacing
                        || spacing > maxSpacing
                        || Math.abs(spacing - priorSpacing) > maxImbalance) return false;
                priorSpacing = spacing;
            }
        }
        return true;
    }
}
