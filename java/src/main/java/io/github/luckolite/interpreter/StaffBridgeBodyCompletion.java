// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Restores only an unassigned dark continuation uniquely connected across observed staff.
 * Existing members and competitors are immutable; an ambiguous second body refuses.
 */
final class StaffBridgeBodyCompletion {
    enum Status {
        COMPLETE,
        INVALID,
        AMBIGUOUS,
        BUDGET_EXHAUSTED
    }

    record Result(Status status, List<List<Integer>> bodies, long work, int added) {
        Result {
            bodies = bodies.stream().map(List::copyOf).toList();
        }
    }

    private StaffBridgeBodyCompletion() {}

    static Result complete(
            byte[] g,
            int w,
            int h,
            List<List<Integer>> input,
            Set<Integer> staff,
            int gap,
            long limit) {
        if (g == null
                || w < 1
                || h < 1
                || (long) w * h != g.length
                || input == null
                || input.size() < 2
                || input.size() > 3
                || staff == null
                || gap < 5
                || gap > 64
                || limit < 0) return new Result(Status.INVALID, List.of(), 0, 0);
        long used = 0;
        Map<Integer, Integer> owner = new HashMap<>();
        List<Set<Integer>> original = new ArrayList<>(), additions = new ArrayList<>();
        for (int bi = 0; bi < input.size(); bi++) {
            var pixels = input.get(bi);
            if (pixels == null || pixels.isEmpty() || pixels.size() > 500000)
                return new Result(Status.INVALID, List.of(), used, 0);
            if (pixels.size() * 8L > limit - used)
                return new Result(Status.BUDGET_EXHAUSTED, List.of(), used, 0);
            used += pixels.size() * 8L;
            Set<Integer> set = new HashSet<>();
            for (int p : pixels)
                if (p < 0
                        || p >= g.length
                        || (g[p] & 255) >= 185
                        || !set.add(p)
                        || owner.putIfAbsent(p, bi) != null)
                    return new Result(Status.INVALID, List.of(), used, 0);
            original.add(set);
            additions.add(new HashSet<>());
        }
        int reach = Math.min(64, (int) Math.rint(.75 * gap) + 1);
        for (int bi = 0; bi < input.size(); bi++) {
            Map<Integer, int[]> cols = new TreeMap<>();
            for (int p : input.get(bi)) {
                int x = p % w, y = p / w;
                int[] e = cols.get(x);
                if (e == null) cols.put(x, new int[] {y, y});
                else {
                    e[0] = Math.min(e[0], y);
                    e[1] = Math.max(e[1], y);
                }
            }
            for (var e : cols.entrySet())
                for (int direction : new int[] {-1, 1}) {
                    int x = e.getKey(),
                            edge = e.getValue()[direction < 0 ? 0 : 1],
                            y = edge + direction,
                            steps = 0;
                    List<Integer> bridge = new ArrayList<>(), residual = new ArrayList<>();
                    while (y >= 0 && y < h && staff.contains(y * w + x)) {
                        if (++used > limit)
                            return new Result(Status.BUDGET_EXHAUSTED, List.of(), used - 1, 0);
                        if (++steps > reach)
                            return new Result(Status.AMBIGUOUS, List.of(), used, 0);
                        if ((g[y * w + x] & 255) >= 185)
                            return new Result(Status.AMBIGUOUS, List.of(), used, 0);
                        bridge.add(y * w + x);
                        y += direction;
                    }
                    if (bridge.isEmpty()) continue;
                    boolean other = false;
                    while (y >= 0 && y < h && (g[y * w + x] & 255) < 185) {
                        if (++used > limit)
                            return new Result(Status.BUDGET_EXHAUSTED, List.of(), used - 1, 0);
                        if (++steps > reach)
                            return new Result(Status.AMBIGUOUS, List.of(), used, 0);
                        int p = y * w + x;
                        Integer existing = owner.get(p);
                        if (existing != null) {
                            other = existing != bi;
                            break;
                        }
                        if (staff.contains(p))
                            return new Result(Status.AMBIGUOUS, List.of(), used, 0);
                        residual.add(p);
                        y += direction;
                    }
                    if (residual.isEmpty()) continue;
                    if (other || y < 0 || y >= h)
                        return new Result(Status.AMBIGUOUS, List.of(), used, 0);
                    for (int p : bridge) {
                        Integer existing = owner.get(p);
                        if (existing != null && existing != bi)
                            return new Result(Status.AMBIGUOUS, List.of(), used, 0);
                    }
                    additions.get(bi).addAll(bridge);
                    additions.get(bi).addAll(residual);
                }
        }
        List<List<Integer>> out = new ArrayList<>();
        Map<Integer, Integer> claims = new HashMap<>();
        int added = 0;
        for (int bi = 0; bi < input.size(); bi++) {
            Set<Integer> full = new HashSet<>(original.get(bi));
            full.addAll(additions.get(bi));
            added += full.size() - original.get(bi).size();
            for (int p : full) {
                Integer previous = claims.putIfAbsent(p, bi);
                if (previous != null && previous != bi)
                    return new Result(Status.AMBIGUOUS, List.of(), used, 0);
            }
            var sorted = new ArrayList<>(full);
            Collections.sort(sorted);
            out.add(sorted);
        }
        return new Result(Status.COMPLETE, out, used, added);
    }
}
