// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Exact finite raster explanation of unmodified member profiles at witnessed staff contacts.
 * Staff membership is an independent input, never inferred from the proposed line.
 * Each original nonstaff member constrains the band. Every reconstructed interior
 * sample must already be dark in this same raster or carry independent staff authority.
 * The existing one-pixel edge uncertainty is retained; no white interior is invented.
 */
final class ObservedOccludedBodyDomain {
    enum Status {
        PROVED,
        INVALID,
        INCOMPLETE,
        NO_NONSTAFF_SUPPORT,
        INFEASIBLE,
        DISJOINT_COLUMN,
        BUDGET_EXHAUSTED
    }

    record Result(
            Status status,
            FiniteParallelBeamBand band,
            long work,
            int columns,
            int originalMembers) {}

    private ObservedOccludedBodyDomain() {}

    private static final class Work {
        long remaining, used;

        Work(long n) {
            remaining = n;
        }

        boolean take(long n) {
            if (n < 0 || n > remaining) return false;
            remaining -= n;
            used += n;
            return true;
        }
    }

    static Result derive(
            byte[] gray,
            int width,
            int height,
            Map<Integer, int[]> profile,
            Map<Integer, Set<Integer>> staffRows,
            int thickness,
            long workLimit) {
        Work work = new Work(Math.max(0, workLimit));
        if (gray == null
                || width <= 0
                || height <= 0
                || (long) width * height != gray.length
                || profile == null
                || profile.size() < 2
                || profile.size() > 512
                || staffRows == null
                || thickness < 3
                || thickness > 64
                || workLimit < 0) return result(Status.INVALID, null, work, 0, 0);
        if (!work.take(profile.size() * 12L))
            return result(Status.BUDGET_EXHAUSTED, null, work, 0, 0);
        TreeMap<Integer, int[]> sorted = new TreeMap<>(profile);
        if ((long) sorted.lastKey() - sorted.firstKey() + 1 != sorted.size())
            return result(Status.INCOMPLETE, null, work, 0, 0);
        int n = sorted.size(), at = 0, members = 0;
        int[] xs = new int[n], lo = new int[n], hi = new int[n];
        for (var entry : sorted.entrySet()) {
            int x = entry.getKey();
            int[] edge = entry.getValue();
            if (x < 0
                    || x >= width
                    || edge == null
                    || edge.length != 2
                    || edge[0] < 0
                    || edge[1] < edge[0]
                    || edge[1] >= height
                    || edge[1] - edge[0] > 128)
                return result(Status.INVALID, null, work, at, members);
            Set<Integer> staff = staffRows.getOrDefault(x, Set.of());
            if (staff == null) return result(Status.INVALID, null, work, at, members);
            int top = Integer.MAX_VALUE, bottom = -1;
            // Do not mutate or replace the original member profile, including staff coowners.
            for (int y = edge[0]; y <= edge[1]; y++) {
                if (!work.take(1)) return result(Status.BUDGET_EXHAUSTED, null, work, at, members);
                members++;
                if ((gray[y * width + x] & 255) >= MaximalRawBarExtractor.DARK_LIMIT)
                    return result(Status.INVALID, null, work, at, members);
                if (!staff.contains(y)) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
            if (bottom < 0) return result(Status.NO_NONSTAFF_SUPPORT, null, work, at, members);
            int first = Integer.MAX_VALUE, last = -1, options = 0;
            // Required members lie inside the same established one-pixel edge bound.
            for (int b = bottom - 1; b <= top + thickness; b++) {
                if (!work.take(1)) return result(Status.BUDGET_EXHAUSTED, null, work, at, members);
                int a = b - thickness + 1;
                if (a < 0 || b >= height) continue;
                boolean existing = true;
                for (int y = a + 1; y < b; y++) {
                    if (!work.take(1))
                        return result(Status.BUDGET_EXHAUSTED, null, work, at, members);
                    if (!staff.contains(y)
                            && (gray[y * width + x] & 255) >= MaximalRawBarExtractor.DARK_LIMIT) {
                        existing = false;
                        break;
                    }
                }
                if (existing) {
                    first = Math.min(first, b);
                    last = b;
                    options++;
                }
            }
            if (options == 0) return result(Status.INFEASIBLE, null, work, at, members);
            if ((long) last - first + 1 != options)
                return result(Status.DISJOINT_COLUMN, null, work, at, members);
            xs[at] = x;
            lo[at] = first;
            hi[at] = last;
            at++;
        }
        // Exact pair constraints plus rational comparisons are conservatively precharged.
        if (!work.take(32L * n * n))
            return result(Status.BUDGET_EXHAUSTED, null, work, at, members);
        var band = FiniteParallelBeamBand.proveBottomIntervals(xs, lo, hi);
        return result(band == null ? Status.INFEASIBLE : Status.PROVED, band, work, at, members);
    }

    private static Result result(Status s, FiniteParallelBeamBand b, Work w, int c, int m) {
        return new Result(s, b, w.used, c, m);
    }
}
