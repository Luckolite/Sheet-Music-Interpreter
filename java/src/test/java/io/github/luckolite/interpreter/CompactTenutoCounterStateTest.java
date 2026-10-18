// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original NEW compact-representation unit oracles; no historical behavior claim. */
public class CompactTenutoCounterStateTest {
    private static Class<?> nested(String name) throws Exception {
        return Class.forName(FiniteTenutoStaffCounter.class.getName() + "$" + name);
    }

    private static void budget(String method, int... values) throws Exception {
        Class<?>[] types = new Class<?>[values.length];
        Arrays.fill(types, int.class);
        Method m = nested("CompactBudget").getDeclaredMethod(method, types);
        m.setAccessible(true);
        Object[] args = new Object[values.length];
        for (int i = 0; i < values.length; i++) args[i] = values[i];
        m.invoke(null, args);
    }

    private static void exhausted(String method, int... values) throws Exception {
        try {
            budget(method, values);
            fail("resource exhaustion must throw the existing conservative Limit");
        } catch (InvocationTargetException e) {
            assertEquals(
                    FiniteTenutoStaffCounter.class.getName() + "$Limit",
                    e.getCause().getClass().getName());
        }
    }

    @Test
    public void compactLiveBudgetsFailClosedAtDeclaredBoundary() throws Exception {
        budget("checkLive", 8192, 8192, 262144);
        exhausted("checkLive", 8193, 1, 4);
        exhausted("checkLive", 1, 8193, 4);
        exhausted("checkLive", 1, 1, 262145);
    }

    @Test
    public void compactTotalBudgetsNeverResetAcrossFrontiers() throws Exception {
        budget("checkTotals", 32768, 50000000);
        exhausted("checkTotals", 32769, 1);
        exhausted("checkTotals", 1, 50000001);
    }

    private static Object range(double lo, double hi) throws Exception {
        Constructor<?> c = nested("Range").getDeclaredConstructor(double.class, double.class);
        c.setAccessible(true);
        return c.newInstance(lo, hi);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> union(List<Object> old, Object addition) throws Exception {
        Method m =
                FiniteTenutoStaffCounter.class.getDeclaredMethod(
                        "compactUnion", List.class, nested("Range"));
        m.setAccessible(true);
        return (List<Object>) m.invoke(null, old, addition);
    }

    private static double edge(Object r, String name) throws Exception {
        Method m = r.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        return (double) m.invoke(r);
    }

    @Test
    public void gapUnionKeepsRealParameterHole() throws Exception {
        var a = union(List.of(range(10, 11)), range(12, 13));
        assertEquals(2, a.size());
        assertEquals(11, edge(a.get(0), "high"), 0);
        assertEquals(12, edge(a.get(1), "low"), 0);
    }

    @Test
    public void overlappingSameKeyGapUnionIsExact() throws Exception {
        var a = union(List.of(range(10, 11)), range(10.5, 12));
        assertEquals(1, a.size());
        assertEquals(10, edge(a.get(0), "low"), 0);
        assertEquals(12, edge(a.get(0), "high"), 0);
    }

    private static Object key(
            int a, int bits, boolean ambiguous, List<Integer> known, int fifth, boolean core)
            throws Exception {
        Constructor<?> c =
                nested("CompactKey")
                        .getDeclaredConstructor(
                                int.class,
                                int.class,
                                int.class,
                                int.class,
                                int.class,
                                boolean.class,
                                List.class,
                                int.class,
                                boolean.class);
        c.setAccessible(true);
        return c.newInstance(a, 2, 3, 4, bits, ambiguous, known, fifth, core);
    }

    @Test
    public void futureRelevantIdentityDifferencesCannotMerge() throws Exception {
        Object a = key(1, 63, false, List.of(1, 2, 4, 8), 9, true);
        assertEquals(a, key(1, 63, false, List.of(1, 2, 4, 8), 9, true));
        assertNotEquals(a, key(5, 63, false, List.of(1, 2, 4, 8), 9, true));
        assertNotEquals(a, key(1, 31, false, List.of(1, 2, 4, 8), 9, true));
        assertNotEquals(a, key(1, 63, true, List.of(1, 2, 4, 8), 9, true));
        assertNotEquals(a, key(1, 63, false, List.of(1, 2, 4, 16), 9, true));
        assertNotEquals(a, key(1, 63, false, List.of(1, 2, 4, 8), 10, true));
        assertNotEquals(a, key(1, 63, false, List.of(1, 2, 4, 8), 9, false));
    }
}
