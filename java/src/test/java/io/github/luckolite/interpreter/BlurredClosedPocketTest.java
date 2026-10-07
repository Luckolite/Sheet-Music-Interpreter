// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original opaque oval with a small, blur-contracted central counter. */
public class BlurredClosedPocketTest {
    static final int W = 120, H = 100;

    byte[] page(int dx, int radius, boolean open) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 130);
        for (int y = 41; y <= 59; y++)
            for (int x = 49; x <= 71; x++)
                if (Math.pow((x - 60) / 11., 2) + Math.pow((y - 50) / 9., 2) <= 1)
                    g[y * W + x] = 35;
        for (int y = 50 - radius; y <= 50 + radius; y++)
            for (int x = 60 + dx - radius; x <= 60 + dx + radius; x++)
                if ((x - 60 - dx) * (x - 60 - dx) + (y - 50) * (y - 50) <= radius * radius)
                    g[y * W + x] = 110;
        if (open) for (int x = 60; x <= 73; x++) g[50 * W + x] = 110;
        return g;
    }

    boolean hollow(byte[] g) {
        return ShadedHollowPocket.proved(g, W, H, 49, 41, 71, 59, 16);
    }

    @Test
    public void smallClosedCentralCounterSurvivesBlur() {
        assertTrue(hollow(page(0, 2, false)));
    }

    @Test
    public void shiftedCentralCounterSurvivesBlur() {
        assertTrue(hollow(page(2, 2, false)));
    }

    @Test
    public void filledOvalWithSinglePixelHighlightRemainsFilled() {
        assertFalse(hollow(page(0, 0, false)));
    }

    @Test
    public void offCenterPocketCannotProveHollow() {
        assertFalse(hollow(page(9, 2, false)));
    }

    @Test
    public void openCounterCannotProveHollow() {
        assertFalse(hollow(page(0, 2, true)));
    }

    @Test
    public void solidHeadRemainsFilled() {
        var g = page(0, 0, false);
        g[50 * W + 60] = 35;
        assertFalse(hollow(g));
    }

    @Test
    public void sourcePixelsRemainUnchanged() {
        var g = page(0, 2, false);
        var b = g.clone();
        hollow(g);
        assertArrayEquals(b, g);
    }
}
