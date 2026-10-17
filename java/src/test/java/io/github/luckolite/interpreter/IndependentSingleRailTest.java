// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;

public final class IndependentSingleRailTest {
    private static final int W = 240, H = 180;

    private byte[] page(int thickness, boolean broken, boolean flag) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 250);
        for (int y = 40; y <= 100; y++) {
            p[y * W + 90] = (byte) 130;
            p[y * W + 150] = (byte) 130;
        }
        for (int x = 90; x <= 150; x++)
            for (int y = 101 - thickness; y <= 100; y++)
                if (!(broken && x > 117 && x < 123) && !(flag && x > 105))
                    p[y * W + x] = (byte) 130;
        return p;
    }

    private int count(byte[] p, boolean reverse) {
        int[] a = {90, 100, 1}, b = {150, 100, 1};
        return PairedGraceBeamInk.countSinglePrinted(
                p, W, H, reverse ? b : a, reverse ? a : b, 14, 40, null);
    }

    @Test
    public void completePaleRail() {
        assertEquals(1, count(page(6, false, false), false));
    }

    @Test
    public void completePaleRailReversed() {
        assertEquals(1, count(page(6, false, false), true));
    }

    @Test
    public void bareQuarters() {
        assertEquals(0, count(page(0, false, false), false));
    }

    @Test
    public void narrowStaffRule() {
        assertEquals(0, count(page(2, false, false), false));
    }

    @Test
    public void interruptedRail() {
        assertEquals(0, count(page(6, true, false), false));
    }

    @Test
    public void genuineFlagCannotBridgeNeighbor() {
        assertEquals(0, count(page(6, false, true), false));
    }
}
