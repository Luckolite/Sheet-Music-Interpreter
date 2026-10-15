// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original parallel thin-rail geometry with displaced and missing witnesses. */
public class LocalMeterStaffWindowsTest {
    static final int W = 160, H = 120;
    static final int[] ROWS = {20, 33, 46, 59, 72};

    static byte[] page(int count, int shift, int thickness) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int n = 0; n < count; n++)
            for (int y = ROWS[n] + shift - thickness; y <= ROWS[n] + shift + thickness; y++)
                for (int x = 45; x < 90; x++) g[y * W + x] = (byte) 135;
        return g;
    }

    static List<LocalMeterStaffWindows.Window> find(byte[] g) {
        return LocalMeterStaffWindows.detect(g, W, H, ROWS, 13, 0);
    }

    @Test
    public void fiveDisplacedRailsSupplyBoundedWindow() {
        var w = find(page(5, 6, 0));
        assertEquals(1, w.size());
        assertEquals(6, w.get(0).shift());
        assertTrue(w.get(0).left() < 45);
        assertTrue(w.get(0).right() > 90);
        assertTrue(w.get(0).right() < W);
    }

    @Test
    public void fourRailsInsufficient() {
        assertTrue(find(page(4, 6, 0)).isEmpty());
    }

    @Test
    public void alignedRailsNeedNoRetry() {
        assertTrue(find(page(5, 0, 0)).isEmpty());
    }

    @Test
    public void thickBarsAreNotRails() {
        assertTrue(find(page(5, 6, 4)).isEmpty());
    }

    @Test
    public void inconsistentSpacingRejected() {
        byte[] g = page(4, 6, 0);
        for (int x = 45; x < 90; x++) g[(ROWS[4] + 3) * W + x] = (byte) 135;
        assertTrue(find(g).isEmpty());
    }

    @Test
    public void narrowMarksInsufficient() {
        byte[] g = page(5, 6, 0);
        for (int y = 0; y < H; y++) for (int x = 50; x < 90; x++) g[y * W + x] = (byte) 255;
        assertTrue(find(g).isEmpty());
    }

    @Test
    public void distantStaffNotRetargeted() {
        assertTrue(find(page(5, 10, 0)).isEmpty());
    }

    @Test
    public void sourceUnchanged() {
        byte[] g = page(5, 6, 0), copy = g.clone();
        find(g);
        assertArrayEquals(copy, g);
    }
}
