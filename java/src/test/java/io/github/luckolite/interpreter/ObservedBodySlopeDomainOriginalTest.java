// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public final class ObservedBodySlopeDomainOriginalTest {
    private static Map<Integer, int[]> stripe(int x0, int x1, int center, double slope, int half) {
        Map<Integer, int[]> out = new TreeMap<>();
        for (int x = x0; x <= x1; x++) {
            int y = (int) Math.rint(center - slope * (x - x0));
            out.put(x, new int[] {y - half, y + half});
        }
        return out;
    }

    private static Map<Integer, Set<Integer>> emptyStaff() {
        return new HashMap<>();
    }

    @Test
    public void differentSearchRepresentativesCanShareAnExactFeasibleSlope() {
        Map<Integer, int[]> a = stripe(10, 40, 30, .50, 3),
                b = stripe(10, 40, 47, .55, 3),
                c = stripe(10, 40, 64, .45, 3);
        var da = ObservedBodySlopeDomain.derive(a, emptyStaff(), 7);
        var db = ObservedBodySlopeDomain.derive(b, emptyStaff(), 7);
        var dc = ObservedBodySlopeDomain.derive(c, emptyStaff(), 7);
        assertEquals(ObservedBodySlopeDomain.Status.PROVED, da.status());
        assertEquals(ObservedBodySlopeDomain.Status.PROVED, db.status());
        assertEquals(ObservedBodySlopeDomain.Status.PROVED, dc.status());
        int[] searchTicks = {
            -13, -8, -12
        }; // candidate search representatives are intentionally not consulted
        assertEquals(3, searchTicks.length);
        assertTrue(
                "complete finite bands have a shared exact slope despite different representatives",
                ObservedBodySlopeDomain.sharesSlope(List.of(da, db, dc)));
    }

    @Test
    public void genuinelyDivergentCompleteBodyCannotJoinCommonFamily() {
        var a = ObservedBodySlopeDomain.derive(stripe(10, 40, 30, .50, 3), emptyStaff(), 7);
        var b = ObservedBodySlopeDomain.derive(stripe(10, 40, 47, .50, 3), emptyStaff(), 7);
        var c = ObservedBodySlopeDomain.derive(stripe(10, 40, 64, -.20, 3), emptyStaff(), 7);
        assertFalse(ObservedBodySlopeDomain.sharesSlope(List.of(a, b, c)));
    }

    @Test
    public void witnessedStaffColumnsAreUnknownEdgesNotInventedBodyPixels() {
        var a = stripe(10, 40, 30, .50, 3);
        var b = stripe(10, 40, 47, .50, 3);
        var c = stripe(10, 40, 64, .50, 3);
        Map<Integer, Set<Integer>> staff = new HashMap<>();
        // Independent staff witness contacts the body across a short span; these x columns are
        // omitted as edge observations.
        for (int x = 18; x <= 22; x++) staff.put(x, Set.of(a.get(x)[0], b.get(x)[1], c.get(x)[0]));
        byte[][] snapshots = {snapshot(a), snapshot(b), snapshot(c)};
        var da = ObservedBodySlopeDomain.derive(a, staff, 7);
        var db = ObservedBodySlopeDomain.derive(b, staff, 7);
        var dc = ObservedBodySlopeDomain.derive(c, staff, 7);
        assertTrue(ObservedBodySlopeDomain.sharesSlope(List.of(da, db, dc)));
        assertArrayEquals(snapshots[0], snapshot(a));
        assertArrayEquals(snapshots[1], snapshot(b));
        assertArrayEquals(snapshots[2], snapshot(c));
        assertEquals(26, da.clearColumns());
    }

    @Test
    public void unobservedOrNoncontiguousBodyAbstains() {
        var a = stripe(10, 40, 30, .50, 3);
        Map<Integer, Set<Integer>> staff = new HashMap<>();
        for (int x = 10; x <= 40; x++) staff.put(x, Set.of(a.get(x)[0]));
        assertEquals(
                ObservedBodySlopeDomain.Status.NO_CLEAR_INTERVAL,
                ObservedBodySlopeDomain.derive(a, staff, 7).status());
        a.remove(20);
        assertEquals(
                ObservedBodySlopeDomain.Status.INCOMPLETE,
                ObservedBodySlopeDomain.derive(a, emptyStaff(), 7).status());
    }

    @Test
    public void disjointClearWindowsMustShareOneGlobalIntercept() {
        var a = stripe(10, 40, 30, .50, 3);
        Map<Integer, Set<Integer>> staff = new HashMap<>();
        for (int x = 18; x <= 22; x++) staff.put(x, Set.of(a.get(x)[0]));
        for (int x = 23; x <= 40; x++) {
            int[] edge = a.get(x);
            a.put(x, new int[] {edge[0] + 3, edge[1] + 3});
        }
        assertEquals(
                "separate windows cannot each invent a different line intercept",
                ObservedBodySlopeDomain.Status.INFEASIBLE,
                ObservedBodySlopeDomain.derive(a, staff, 7).status());
    }

    @Test
    public void exactThicknessAndInputValidationAreRequired() {
        assertEquals(
                ObservedBodySlopeDomain.Status.INFEASIBLE,
                ObservedBodySlopeDomain.derive(stripe(10, 40, 30, .50, 3), emptyStaff(), 5)
                        .status());
        assertEquals(
                ObservedBodySlopeDomain.Status.INVALID,
                ObservedBodySlopeDomain.derive(stripe(10, 40, 30, .50, 3), emptyStaff(), 0)
                        .status());
    }

    @Test
    public void measuredThicknessUsesOnlyClearColumnsAndLowerMedian() {
        var a = stripe(10, 14, 30, .50, 3);
        Map<Integer, Set<Integer>> staff = new HashMap<>();
        staff.put(12, Set.of(a.get(12)[0]));
        assertEquals(7, ObservedBodySlopeDomain.lowerMedianObservedThickness(a, staff));
        assertEquals(
                0,
                ObservedBodySlopeDomain.lowerMedianObservedThickness(
                        a,
                        new HashMap<>() {
                            {
                                for (int x = 10; x <= 14; x++) put(x, Set.of(a.get(x)[0]));
                            }
                        }));
    }

    @Test
    public void completeThreeBodyFamilyUsesRawDomainAndPerColumnSpacing() {
        var bodies =
                List.of(
                        stripe(10, 40, 30, .50, 3),
                        stripe(10, 40, 47, .51, 3),
                        stripe(10, 40, 64, .49, 3));
        assertTrue(ObservedParallelBodyFamily.qualifies(bodies, emptyStaff(), 17));
    }

    @Test
    public void completeTwoBodyFamilyRemainsSupported() {
        var bodies = List.of(stripe(10, 40, 30, .50, 3), stripe(10, 40, 47, .55, 3));
        assertTrue(ObservedParallelBodyFamily.qualifies(bodies, emptyStaff(), 17));
    }

    @Test
    public void candidateCountCannotForgiveCloseOrUnevenPhysicalSpacing() {
        assertFalse(
                ObservedParallelBodyFamily.qualifies(
                        List.of(
                                stripe(10, 40, 30, .50, 3),
                                stripe(10, 40, 37, .50, 3),
                                stripe(10, 40, 58, .50, 3)),
                        emptyStaff(),
                        17));
        assertFalse(
                ObservedParallelBodyFamily.qualifies(
                        List.of(
                                stripe(10, 40, 30, .50, 3),
                                stripe(10, 40, 47, .50, 3),
                                stripe(10, 40, 68, .50, 3)),
                        emptyStaff(),
                        17));
    }

    @Test
    public void familyRequiresCommonFiniteSlopeAndNearCompleteSharedSpan() {
        assertFalse(
                ObservedParallelBodyFamily.qualifies(
                        List.of(
                                stripe(10, 40, 30, .50, 3),
                                stripe(10, 40, 47, .50, 3),
                                stripe(10, 40, 64, -.20, 3)),
                        emptyStaff(),
                        17));
        assertFalse(
                ObservedParallelBodyFamily.qualifies(
                        List.of(
                                stripe(10, 40, 30, .50, 3),
                                stripe(20, 50, 47, .50, 3),
                                stripe(25, 55, 64, .50, 3)),
                        emptyStaff(),
                        17));
    }

    private static byte[] snapshot(Map<Integer, int[]> map) {
        byte[] out = new byte[map.size() * 3];
        int i = 0;
        for (var e : map.entrySet()) {
            out[i++] = (byte) (int) e.getKey();
            out[i++] = (byte) e.getValue()[0];
            out[i++] = (byte) e.getValue()[1];
        }
        return out;
    }
}
