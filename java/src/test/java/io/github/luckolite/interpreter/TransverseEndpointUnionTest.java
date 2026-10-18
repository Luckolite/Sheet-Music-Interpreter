// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original API role checks reusing the unchanged pre-candidate procedural page. */
public final class TransverseEndpointUnionTest {
    private static Object page() throws Exception {
        Class<?> p = Class.forName(FiniteBeamQuarterOwnershipTest.class.getName() + "$Page");
        Constructor<?> c = p.getDeclaredConstructor(boolean.class, boolean.class);
        c.setAccessible(true);
        return c.newInstance(true, true);
    }

    private static boolean owns(Object p) throws Exception {
        Method m =
                FiniteBeamQuarterOwnershipTest.class.getDeclaredMethod(
                        "owns", p.getClass(), int.class, float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, 137, 102f / 260);
    }

    @Test
    public void rawShaftEndpointMayIncludeOnlyTheIndependentlyProvedRule() throws Exception {
        // Original raw beam lower edge at the first shaft is134, existing complete staff133..135.
        // No raster/owner changes: this is the already independent original positive.
        assertTrue(owns(page()));
    }

    @Test
    public void unsupportedShaftContinuationBeyondBeamAndRuleRemainsUnknown() throws Exception {
        Object p = page();
        Field field = p.getClass().getDeclaredField("gray");
        field.setAccessible(true);
        byte[] gray = (byte[]) field.get(p);
        for (int y = 136; y <= 140; y++) for (int x = 199; x <= 202; x++) gray[y * 480 + x] = 0;
        assertFalse(owns(p));
    }
}
