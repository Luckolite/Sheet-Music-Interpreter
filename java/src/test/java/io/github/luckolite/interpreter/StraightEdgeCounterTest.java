// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original curved pockets beside a straight rule, independent of score pixels. */
public class StraightEdgeCounterTest {
    static byte[] page(boolean curved, boolean mirror) {
        byte[] g = new byte[200 * 200];
        Arrays.fill(g, (byte) 255);
        for (int i = 0; i < 4; i++) {
            int cy = 86 + i * 16;
            for (int y = cy - 7; y <= cy + 7; y++)
                for (int x = 90; x <= 110; x++) {
                    boolean outer =
                            !curved
                                    || Math.pow((x - 100) / 10., 2) + Math.pow((y - cy) / 7., 2)
                                            <= 1;
                    boolean inner =
                            curved
                                    ? Math.pow((x - 100) / 6., 2) + Math.pow((y - cy) / 5., 2) < 1
                                    : Math.abs(y - cy) < 5
                                            && x >= 96
                                            && x
                                                    < 100
                                                            + 10
                                                                    * Math.sqrt(
                                                                            1
                                                                                    - Math.pow(
                                                                                            (y - cy)
                                                                                                    / 5.,
                                                                                            2));
                    if (outer && !inner) g[y * 200 + (mirror ? 200 - x : x)] = 0;
                }
        }
        return g;
    }

    @Test
    public void straightLeftEdgeIsNotMeter() {
        assertFalse(RoundedMeterCounters.matches(page(false, false), 200, 200, 94, 80, 144, 16));
    }

    @Test
    public void straightRightEdgeIsNotMeter() {
        assertFalse(RoundedMeterCounters.matches(page(false, true), 200, 200, 106, 80, 144, 16));
    }

    @Test
    public void bothRoundedEdgesStillProveMeter() {
        assertTrue(RoundedMeterCounters.matches(page(true, false), 200, 200, 94, 80, 144, 16));
    }

    @Test
    public void mirroredRoundedEdgesStillProveMeter() {
        assertTrue(RoundedMeterCounters.matches(page(true, true), 200, 200, 106, 80, 144, 16));
    }

    @Test
    public void doubleScaleStraightEdgeStillRejected() {
        byte[] a = page(false, false), b = new byte[400 * 400];
        for (int y = 0; y < 400; y++)
            for (int x = 0; x < 400; x++) b[y * 400 + x] = a[(y / 2) * 200 + x / 2];
        assertFalse(RoundedMeterCounters.matches(b, 400, 400, 188, 160, 288, 32));
    }

    @Test
    public void shallowRoundedLeftEdgeStillProvesMeter() {
        byte[] g = page(false, false);
        for (int i = 0; i < 4; i++) {
            int cy = 86 + i * 16;
            for (int dy : new int[] {-4, -3, 3, 4}) g[(cy + dy) * 200 + 96] = 0;
        }
        assertTrue(RoundedMeterCounters.matches(g, 200, 200, 94, 80, 144, 16));
    }

    @Test
    public void pixelsPreserved() {
        byte[] a = page(false, false), b = a.clone();
        RoundedMeterCounters.matches(a, 200, 200, 94, 80, 144, 16);
        assertArrayEquals(b, a);
    }
}
