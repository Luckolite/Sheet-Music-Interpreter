// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class OccludedDomainControls {
    static int checks = 0;

    static void check(boolean x, String name) {
        if (!x) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }

    static final int W = 80, H = 80;
    static final long B = 1000000;

    static byte[] raster() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int x = 10; x < 40; x++) {
            int top = 40 - (x - 10) / 2;
            for (int y = top; y <= top + 6; y++) g[y * W + x] = 0;
        }
        return g;
    }

    static Map<Integer, int[]> profile() {
        Map<Integer, int[]> p = new TreeMap<>();
        for (int x = 10; x < 40; x++) {
            int t = 40 - (x - 10) / 2;
            p.put(x, new int[] {t, t + 6});
        }
        return p;
    }

    static ObservedOccludedBodyDomain.Result run(
            byte[] g, Map<Integer, int[]> p, Map<Integer, Set<Integer>> s, long b) {
        return ObservedOccludedBodyDomain.derive(g, W, H, p, s, 7, b);
    }

    public static void main(String[] args) {
        byte[] g = raster();
        var p = profile();
        var empty = Map.<Integer, Set<Integer>>of();
        check(
                run(g, p, empty, B).status() == ObservedOccludedBodyDomain.Status.PROVED,
                "ordinary_complete_band");
        Map<Integer, Set<Integer>> staff = new HashMap<>();
        for (int x = 10; x < 40; x++) {
            staff.put(x, Set.of(34, 35));
            g[34 * W + x] = 0;
            g[35 * W + x] = 0;
        }
        Map<Integer, int[]> c = new TreeMap<>();
        for (var e : p.entrySet()) {
            int[] a = e.getValue().clone();
            if (a[0] <= 35 && a[1] > 35) a[0] = 36;
            c.put(e.getKey(), a);
        }
        int[] before = c.get(30).clone();
        var r = run(g, c, staff, B);
        check(
                r.status() == ObservedOccludedBodyDomain.Status.PROVED,
                "staff_censored_body_restores_existing_ink");
        check(Arrays.equals(before, c.get(30)), "original_member_profile_unchanged");
        check(r.work() > 0 && r.work() < B, "bounded_resource_accounting");
        check(
                run(g, c, staff, 0).status() == ObservedOccludedBodyDomain.Status.BUDGET_EXHAUSTED,
                "zero_budget_abstains");
        byte[] white = g.clone();
        white[36 * W + 30] = (byte) 255;
        check(
                run(white, c, staff, B).status() != ObservedOccludedBodyDomain.Status.PROVED,
                "independent_paper_inside_member_rejected");
        byte[] hidden = g.clone();
        hidden[33 * W + 30] = (byte) 255;
        check(
                run(hidden, c, staff, B).status() != ObservedOccludedBodyDomain.Status.PROVED,
                "unobserved_nonstaff_interior_rejected");
        var missing = new TreeMap<Integer, int[]>(c);
        missing.remove(23);
        check(
                run(g, missing, staff, B).status() == ObservedOccludedBodyDomain.Status.INCOMPLETE,
                "missing_original_column_rejected");
        var bad = new TreeMap<Integer, int[]>(c);
        bad.put(22, new int[] {70, 69});
        check(
                run(g, bad, staff, B).status() == ObservedOccludedBodyDomain.Status.INVALID,
                "invalid_edge_rejected");
        byte[] stale = new byte[W * H];
        Arrays.fill(stale, (byte) 255);
        check(
                run(stale, c, staff, B).status() == ObservedOccludedBodyDomain.Status.INVALID,
                "stale_or_foreign_raster_rejected");
        var allStaff = new HashMap<Integer, Set<Integer>>();
        for (var e : p.entrySet()) {
            Set<Integer> s = new HashSet<>();
            for (int y = e.getValue()[0]; y <= e.getValue()[1]; y++) s.add(y);
            allStaff.put(e.getKey(), s);
        }
        check(
                run(g, p, allStaff, B).status()
                        == ObservedOccludedBodyDomain.Status.NO_NONSTAFF_SUPPORT,
                "staff_only_no_body_evidence_rejected");
        check(
                ObservedOccludedBodyDomain.derive(g, W, H, p, empty, 2, B).status()
                        == ObservedOccludedBodyDomain.Status.INVALID,
                "invalid_thickness_rejected");
        int[] xs = {1, 2, 3}, lo = {10, 10, 20}, hi = {10, 10, 20};
        check(
                FiniteParallelBeamBand.proveBottomIntervals(xs, lo, hi) == null,
                "nonlinear_incompatible_edges_rejected");
        check(
                FiniteParallelBeamBand.proveBottomIntervals(
                                new int[] {2, 1}, new int[] {2, 2}, new int[] {3, 3})
                        == null,
                "unordered_coordinates_rejected");
        System.out.println("controls=" + checks + "/" + checks);
    }
}
