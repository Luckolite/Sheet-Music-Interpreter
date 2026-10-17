// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;

/** Original geometry; no score pixels or score coordinates. */
public final class BlurredFullSizeTripleJunctionTest {
    private static final int W = 240, H = 180;

    private static byte[] page(int beams, boolean broken) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 250);
        for (int x = 100; x <= 132; x++)
            for (int i = 0; i < beams; i++)
                for (int dy = 0; dy < 6; dy++) {
                    if (x == 131 && dy > 1 || broken && i == 0 && x >= 114 && x <= 118) continue;
                    p[(73 + 11 * i + dy) * W + x] = 80;
                }
        for (int y = 38; y <= 100; y++) {
            p[y * W + 100] = 80;
            p[y * W + 132] = 80;
        }
        return p;
    }

    private static int count(int beams, boolean broken, boolean reverse) {
        int[] a = {100, 100, 1}, b = {132, 100, 1};
        return PairedGraceBeamInk.countPrintedSize(
                page(beams, broken), W, H, reverse ? b : a, reverse ? a : b, 14);
    }

    @Test
    public void tripleSurvivesOneBlurredJunction() {
        assertEquals(3, count(3, false, false));
    }

    @Test
    public void reversalRetainsTriple() {
        assertEquals(3, count(3, false, true));
    }

    @Test
    public void bareShaftsStayBare() {
        assertEquals(0, count(0, false, false));
    }

    @Test
    public void singleBeamDoesNotBecomeTriple() {
        assertEquals(0, count(1, false, false));
    }

    @Test
    public void twoRailsDoNotBecomeTriple() {
        assertTrue(count(2, false, false) < 3);
    }

    @Test
    public void brokenThirdRailIsRejected() {
        assertTrue(count(3, true, false) < 3);
    }
}
