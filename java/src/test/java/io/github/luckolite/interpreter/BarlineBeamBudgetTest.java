// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Optional positive beam evidence cannot consume an unfunded legacy raw-path budget. */
public class BarlineBeamBudgetTest {
    static Class<?> budgetType() throws Exception {
        return Class.forName(OmrScoreInterpreter.class.getName() + "$RawCourtesyBudget");
    }

    static Object budget(long work, long bytes) throws Exception {
        var c = budgetType().getDeclaredConstructor(long.class, long.class);
        c.setAccessible(true);
        return c.newInstance(work, bytes);
    }

    static boolean optional(Object b, long work, long bytes) throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "prepayBarlineProof", budgetType(), long.class, long.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, b, work, bytes);
    }

    static boolean legacy(Object b, long work, long bytes) throws Exception {
        var m = budgetType().getDeclaredMethod("prepay", long.class, long.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(b, work, bytes);
    }

    @Test
    public void unfundedOptionalWorkLeavesLegacyPathAvailable() throws Exception {
        var b = budget(100, 100);
        assertFalse(optional(b, 101, 1));
        assertTrue(legacy(b, 100, 100));
    }

    @Test
    public void unfundedOptionalAllocationLeavesLegacyPathAvailable() throws Exception {
        var b = budget(100, 100);
        assertFalse(optional(b, 1, 101));
        assertTrue(legacy(b, 100, 100));
    }

    @Test
    public void fundedOptionalProofActuallyDebitsSharedAllowance() throws Exception {
        var b = budget(100, 100);
        assertTrue(optional(b, 40, 60));
        assertFalse(optional(b, 61, 1));
        assertTrue(legacy(b, 60, 40));
    }

    @Test
    public void alignedOpposedHeadCannotAcquireAnAdditionalBeamValue() throws Exception {
        var page = new BarlineOpposedBeamOriginalTest.Page("valid");
        Class<?> component = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        Class<?> staff = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var hc =
                component.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        hc.setAccessible(true);
        Object head = hc.newInstance(225, 271, 289, 158, 174, 280f, 166f),
                peer = hc.newInstance(225, 231, 249, 137, 153, 240f, 145f),
                opposed = hc.newInstance(225, 271, 289, 172, 188, 280f, 180f);
        var sc = staff.getDeclaredConstructor(float.class, float.class, float.class);
        sc.setAccessible(true);
        Object own = sc.newInstance(166f, 222f, 14f), upper = sc.newInstance(40f, 96f, 14f);
        var count = staff.getDeclaredField("count");
        count.setAccessible(true);
        count.setInt(own, 2);
        count.setInt(upper, 2);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "barlineOpposedDoubleBeamCount",
                        byte[].class,
                        int.class,
                        int.class,
                        component,
                        staff,
                        staff,
                        java.util.List.class,
                        java.util.List.class,
                        MeasureRegion.class,
                        int.class,
                        budgetType());
        m.setAccessible(true);
        var b = budget(64_000_000L, 16L * 1024 * 1024);
        int result =
                (Integer)
                        m.invoke(
                                null,
                                page.g,
                                BarlineOpposedBeamOriginalTest.W,
                                BarlineOpposedBeamOriginalTest.H,
                                head,
                                own,
                                own,
                                java.util.List.of(upper, own),
                                java.util.List.of(head, peer, opposed),
                                new MeasureRegion(.03f, .97f, .03f, .94f),
                                0,
                                b);
        assertEquals(0, result);
        assertTrue(legacy(b, 100, 100));
    }
}
