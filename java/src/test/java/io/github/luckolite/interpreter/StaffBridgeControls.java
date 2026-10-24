// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class StaffBridgeControls {
    static int checks = 0;

    static void check(boolean b, String n) {
        if (!b) throw new AssertionError(n);
        checks++;
        System.out.println("PASS " + n);
    }

    static final int W = 80, H = 80;

    static byte[] g() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int x = 10; x < 30; x++) {
            for (int y = 23; y <= 32; y++) g[y * W + x] = 0;
            for (int y = 45; y <= 53; y++) g[y * W + x] = 0;
        }
        return g;
    }

    static List<Integer> rect(int top, int bottom) {
        List<Integer> p = new ArrayList<>();
        for (int x = 10; x < 30; x++) for (int y = top; y <= bottom; y++) p.add(y * W + x);
        return p;
    }

    static Set<Integer> staff() {
        Set<Integer> s = new HashSet<>();
        for (int x = 10; x < 30; x++) for (int y = 26; y <= 27; y++) s.add(y * W + x);
        return s;
    }

    static StaffBridgeBodyCompletion.Result run(
            byte[] g, List<List<Integer>> p, Set<Integer> s, long budget) {
        return StaffBridgeBodyCompletion.complete(g, W, H, p, s, 17, budget);
    }

    public static void main(String[] args) {
        var input = List.of(rect(28, 32), rect(45, 53));
        var copy = input.stream().map(List::copyOf).toList();
        var complete = run(g(), input, staff(), 1000000);
        check(
                complete.status() == StaffBridgeBodyCompletion.Status.COMPLETE,
                "unique_staff_bridge_complete");
        check(complete.added() == 100, "entire_existing_continuation_and_staff_retained");
        check(
                complete.bodies().get(0).containsAll(input.get(0)),
                "every_original_member_preserved");
        check(input.equals(copy), "input_members_never_mutated");
        check(run(g(), input, Set.of(), 1000000).added() == 0, "no_staff_authority_no_completion");
        check(
                run(g(), input, staff(), 0).status()
                        == StaffBridgeBodyCompletion.Status.BUDGET_EXHAUSTED,
                "zero_budget_abstains");
        var ambiguous = List.of(rect(28, 32), rect(23, 23), rect(45, 53));
        check(
                run(g(), ambiguous, staff(), 1000000).status()
                        == StaffBridgeBodyCompletion.Status.AMBIGUOUS,
                "unassigned_continuation_reaching_second_body_refuses");
        var immediate = List.of(rect(28, 32), rect(23, 25), rect(45, 53));
        check(
                run(g(), immediate, staff(), 1000000).added() == 0,
                "staff_joins_two_owned_bodies_without_merging");
        byte[] longBranch = g();
        for (int x = 10; x < 30; x++) for (int y = 1; y <= 25; y++) longBranch[y * W + x] = 0;
        check(
                run(longBranch, input, staff(), 1000000).status()
                        == StaffBridgeBodyCompletion.Status.AMBIGUOUS,
                "bounded_bridge_rejects_long_foreign_branch");
        byte[] white = g();
        Arrays.fill(white, (byte) 255);
        check(
                run(white, input, staff(), 1000000).status()
                        == StaffBridgeBodyCompletion.Status.INVALID,
                "foreign_raster_rejected");
        check(
                run(g(), List.of(rect(28, 32), rect(28, 32)), staff(), 1000000).status()
                        == StaffBridgeBodyCompletion.Status.INVALID,
                "overlapping_raw_body_claims_rejected");
        check(
                StaffBridgeBodyCompletion.complete(g(), 79, H, input, staff(), 17, 1000000).status()
                        == StaffBridgeBodyCompletion.Status.INVALID,
                "wrong_raster_dimensions_rejected");
        System.out.println("staffBridgeControls=" + checks + "/" + checks);
    }
}
