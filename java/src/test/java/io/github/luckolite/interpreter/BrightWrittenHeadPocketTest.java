// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class BrightWrittenHeadPocketTest {
    final ClosedWrittenHeadPocketTest f = new ClosedWrittenHeadPocketTest();

    @Test
    public void brightHollowsRetainBothStemDirections() {
        for (int p : new int[] {185, 210, 235, 255})
            for (int d : new int[] {-1, 1})
                assertTrue("paper=" + p + " direction=" + d, f.proved(f.page(p, d, 0, true), 0));
    }

    @Test
    public void brightFilledHeadsStayShort() {
        for (int p : new int[] {185, 210, 235, 255})
            assertFalse(f.proved(f.page(p, -1, 0, false), 0));
    }

    @Test
    public void brightOpenNotchStaysShort() {
        for (int p : new int[] {185, 210, 235, 255}) {
            byte[] g = f.page(p, -1, 0, true);
            for (int x = 80; x <= 87; x++) g[80 * 180 + x] = (byte) p;
            assertFalse(f.proved(g, 0));
        }
    }

    @Test
    public void brightStemlessHeadNotHalf() {
        byte[] g = f.page(235, -1, 0, true);
        for (int y = 87; y <= 118; y++) for (int x = 71; x <= 75; x++) g[y * 180 + x] = (byte) 235;
        assertFalse(f.proved(g, 0));
    }

    @Test
    public void brightTwoOpposedStemsStayUncertain() {
        assertFalse(f.proved(f.page(235, 0, 0, true), 0));
    }
}
