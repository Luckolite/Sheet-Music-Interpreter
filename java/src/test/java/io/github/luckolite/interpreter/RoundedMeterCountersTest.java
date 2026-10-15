// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometric enclosed counters, independent of any source score. */
public class RoundedMeterCountersTest {
    static final int W = 200, H = 200;

    private byte[] page(boolean rounded, int count) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int i = 0; i < count; i++) {
            int cy = 86 + i * 16;
            for (int y = cy - 7; y <= cy + 7; y++)
                for (int x = 90; x <= 110; x++) {
                    boolean outer =
                            rounded
                                    ? Math.pow((x - 100) / 10.0, 2) + Math.pow((y - cy) / 7.0, 2)
                                            <= 1
                                    : true;
                    boolean inner =
                            rounded
                                    ? Math.pow((x - 100) / 6.0, 2) + Math.pow((y - cy) / 5.0, 2) < 1
                                    : Math.abs(x - 100) <= 4 && Math.abs(y - cy) <= 3;
                    if (outer && !inner) g[y * W + x] = 0;
                }
        }
        return g;
    }

    @Test
    public void roundedStackRejectsEitherOuterStroke() {
        var g = page(true, 4);
        assertTrue(RoundedMeterCounters.matches(g, W, H, 94, 80, 144, 16));
        assertTrue(RoundedMeterCounters.matches(g, W, H, 106, 80, 144, 16));
    }

    @Test
    public void rectangularDoubleBarSpacesRemainBars() {
        assertFalse(RoundedMeterCounters.matches(page(false, 4), W, H, 94, 80, 144, 16));
    }

    @Test
    public void singleRoundedHeadDoesNotRejectBar() {
        assertFalse(RoundedMeterCounters.matches(page(true, 1), W, H, 94, 80, 144, 16));
    }

    @Test
    public void distantColumnDoesNotBelongToDigits() {
        assertFalse(RoundedMeterCounters.matches(page(true, 4), W, H, 77, 80, 144, 16));
    }

    @Test
    public void sourcePixelsPreserved() {
        var g = page(true, 4);
        var before = g.clone();
        RoundedMeterCounters.matches(g, W, H, 94, 80, 144, 16);
        assertArrayEquals(before, g);
    }
}
