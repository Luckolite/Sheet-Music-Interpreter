// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Finite edge-domain proof for complete raw bodies; search ticks are not evidence. */
final class ObservedBodySlopeDomain {
    private ObservedBodySlopeDomain() {}

    enum Status {
        PROVED,
        INVALID,
        INCOMPLETE,
        NO_CLEAR_INTERVAL,
        INFEASIBLE
    }

    record Result(Status status, List<FiniteParallelBeamBand> intervals, int clearColumns) {
        Result {
            intervals = List.copyOf(intervals);
        }
    }

    static Result derive(
            Map<Integer, int[]> topBottomByX,
            Map<Integer, Set<Integer>> staffRowsByX,
            int thickness) {
        if (topBottomByX == null
                || topBottomByX.size() < 2
                || staffRowsByX == null
                || thickness < 1
                || topBottomByX.size() > 512) return result(Status.INVALID, List.of(), 0);
        TreeMap<Integer, int[]> ordered = new TreeMap<>(topBottomByX);
        int first = ordered.firstKey(), last = ordered.lastKey();
        if ((long) last - first + 1 != ordered.size())
            return result(Status.INCOMPLETE, List.of(), 0);
        for (int x = first; x <= last; x++) {
            int[] edge = ordered.get(x);
            if (edge == null || edge.length != 2 || edge[0] < 0 || edge[1] < edge[0])
                return result(Status.INVALID, List.of(), 0);
        }
        List<Integer> clearX = new ArrayList<>(),
                top = new ArrayList<>(),
                bottom = new ArrayList<>();
        for (Map.Entry<Integer, int[]> entry : ordered.entrySet()) {
            int x = entry.getKey();
            int[] edge = entry.getValue();
            Set<Integer> staff = staffRowsByX.getOrDefault(x, Set.of());
            if (staff.stream().anyMatch(y -> y >= edge[0] && y <= edge[1])) continue;
            clearX.add(x);
            top.add(edge[0]);
            bottom.add(edge[1]);
        }
        if (clearX.size() < 2) return result(Status.NO_CLEAR_INTERVAL, List.of(), clearX.size());
        int[] xs = clearX.stream().mapToInt(Integer::intValue).toArray();
        int[] tops = top.stream().mapToInt(Integer::intValue).toArray();
        int[] bottoms = bottom.stream().mapToInt(Integer::intValue).toArray();
        FiniteParallelBeamBand band = FiniteParallelBeamBand.proveAtX(xs, tops, bottoms, thickness);
        return band == null
                ? result(Status.INFEASIBLE, List.of(), clearX.size())
                : result(Status.PROVED, List.of(band), clearX.size());
    }

    /** Lower median raw edge height over columns not intersected by witnessed staff pixels. */
    static int lowerMedianObservedThickness(
            Map<Integer, int[]> topBottomByX, Map<Integer, Set<Integer>> staffRowsByX) {
        if (topBottomByX == null
                || staffRowsByX == null
                || topBottomByX.size() < 2
                || topBottomByX.size() > 512) return 0;
        List<Integer> heights = new ArrayList<>();
        for (Map.Entry<Integer, int[]> entry : topBottomByX.entrySet()) {
            int[] edge = entry.getValue();
            if (edge == null || edge.length != 2 || edge[0] < 0 || edge[1] < edge[0]) return 0;
            Set<Integer> staff = staffRowsByX.getOrDefault(entry.getKey(), Set.of());
            if (staff.stream().noneMatch(y -> y >= edge[0] && y <= edge[1]))
                heights.add(edge[1] - edge[0] + 1);
        }
        if (heights.size() < 2) return 0;
        heights.sort(Integer::compareTo);
        return heights.get((heights.size() - 1) / 2);
    }

    /** In one dimension, pairwise interval intersections imply a shared feasible slope. */
    static boolean sharesSlope(List<Result> bodies) {
        if (bodies == null || bodies.size() < 2) return false;
        List<FiniteParallelBeamBand> all = new ArrayList<>();
        for (Result body : bodies) {
            if (body == null || body.status != Status.PROVED || body.intervals.isEmpty())
                return false;
            all.addAll(body.intervals);
        }
        for (int i = 0; i < all.size(); i++)
            for (int j = i + 1; j < all.size(); j++)
                if (!all.get(i).parallelTo(all.get(j))) return false;
        return true;
    }

    private static Result result(Status status, List<FiniteParallelBeamBand> intervals, int clear) {
        return new Result(status, intervals, clear);
    }
}
