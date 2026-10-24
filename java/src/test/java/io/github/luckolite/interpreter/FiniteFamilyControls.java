// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class FiniteFamilyControls {
    static final int W = 100, H = 100;
    static int checks = 0;

    record Frame(byte[] g, List<Map<Integer, int[]>> b) {}

    static Frame frame(double[] slopes, int[] base) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        List<Map<Integer, int[]>> b = new ArrayList<>();
        for (int i = 0; i < slopes.length; i++) {
            Map<Integer, int[]> p = new TreeMap<>();
            for (int x = 10; x < 40; x++) {
                double edge = base[i] + slopes[i] * (x - 10) + .2;
                int t = (int) Math.floor(edge), z = (int) Math.ceil(edge + 6);
                p.put(x, new int[] {t, z});
                for (int y = t; y <= z; y++) g[y * W + x] = 0;
            }
            b.add(p);
        }
        return new Frame(g, b);
    }

    static boolean pass(Frame f, long budget) {
        return ObservedFiniteBodyFamily.qualifies(f.g, W, H, f.b, Map.of(), 17, budget);
    }

    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) {
        Frame plain = frame(new double[] {-.5, -.5, -.5}, new int[] {30, 42, 54});
        check(pass(plain, 200000000), "true_parallel_finite_family");
        Frame finite = frame(new double[] {-.5, -.55, -.6}, new int[] {30, 42, 54});
        check(
                pass(finite, 200000000),
                "true_cooriented_finite_family_without_exact_infinite_parallelism");
        Frame shifted = frame(new double[] {-.5, -.55, -.6}, new int[] {40, 52, 64});
        check(pass(shifted, 200000000), "translation_equivariance");
        check(
                !pass(frame(new double[] {-.5, -.1, -.6}, new int[] {30, 42, 54}), 200000000),
                "same_sign_fan_exceeds_existing_finite_gap_bounds");
        check(
                !pass(frame(new double[] {-.1, -1, -.5}, new int[] {30, 42, 54}), 200000000),
                "crossing_strokes_cannot_reorder_at_opposite_endpoint");
        check(
                !pass(frame(new double[] {-.5, .2, -.5}, new int[] {30, 42, 54}), 200000000),
                "opposite_orientation_rejected");
        check(
                !pass(frame(new double[] {-.5, -.5, -.5}, new int[] {30, 35, 40}), 200000000),
                "too_close_finite_bodies_rejected");
        check(
                !pass(frame(new double[] {-.5, -.5, -.5}, new int[] {30, 42, 59}), 200000000),
                "unbalanced_family_rejected");
        check(!pass(plain, 0), "zero_budget_abstains");
        check(!pass(plain, 10000), "bounded_polytope_precharge_abstains");
        byte[] foreign = finite.g.clone();
        Arrays.fill(foreign, (byte) 255);
        check(!pass(new Frame(foreign, finite.b), 200000000), "foreign_raster_rejected");
        var hole = frame(new double[] {-.5, -.55, -.6}, new int[] {30, 42, 54});
        hole.b.get(1).remove(23);
        check(!pass(hole, 200000000), "incomplete_finite_body_rejected");
        var two = frame(new double[] {-.5, -.55}, new int[] {30, 42});
        check(pass(two, 200000000), "true_two_body_family");
        check(
                !pass(frame(new double[] {0, 0, 0}, new int[] {30, 42, 54}), 200000000),
                "horizontal_direction_unknown_rejected");
        System.out.println("finiteControls=" + checks + "/" + checks);
    }
}
