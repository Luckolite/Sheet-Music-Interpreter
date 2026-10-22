// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class FiniteCapResourceBoundaryTest {
    @Test
    public void deniedBodyDebitIsExplicitAndReadOnly() {
        byte[] g = new byte[128 * 128];
        Arrays.fill(g, (byte) 250);
        for (int y = 50; y <= 64; y++) for (int x = 50; x <= 68; x++) g[y * 128 + x] = (byte) 185;
        assertTrue(PaleFilledHeadBody.isFilled(g, 128, 128, 50, 50, 68, 64, 59, 57, 13.5f));
        byte[] old = g.clone();
        var budget = new FiniteHollowShaftCap.Budget(0);
        assertFalse(
                PaleFilledHeadBody.isFilled(g, 128, 128, 50, 50, 68, 64, 59, 57, 13.5f, budget));
        assertTrue(budget.refused());
        assertArrayEquals(old, g);
    }

    @Test
    public void roundedOutsideStaffBoundaryAbstainsBeforeRasterRead() {
        var s = new FiniteHollowShaftCapOriginalTest.Scene(16);
        s.axis(s.x, s.ownRoot(), s.end);
        byte[] old = s.gray.clone();
        float bottom = FiniteHollowShaftCapOriginalTest.H - .1f;
        assertFalse(
                FiniteHollowShaftCap.proves(
                        s.gray,
                        FiniteHollowShaftCapOriginalTest.W,
                        FiniteHollowShaftCapOriginalTest.H,
                        s.l,
                        s.t,
                        s.r,
                        s.b,
                        s.cx,
                        s.cy,
                        s.gap,
                        new int[] {s.x, s.end, 1},
                        bottom - 4 * s.gap,
                        bottom));
        assertArrayEquals(old, s.gray);
    }
}
