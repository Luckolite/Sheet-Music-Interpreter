// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccidentalDotInkTest {
    private static final int W = 140, H = 140;

    private byte[] page(boolean left, boolean right, boolean bridge) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        if (left) for (int y = 35; y <= 83; y++) g[y * W + 60] = (byte) 180;
        if (right) for (int y = 50; y <= 98; y++) g[y * W + 70] = (byte) 180;
        if (bridge) for (int y = 59; y <= 63; y++) for (int x = 60; x <= 70; x++) g[y * W + x] = 80;
        return g;
    }

    @Test
    public void faintPairedShaftsOwnTheirDarkCrossbar() {
        assertTrue(AccidentalDotInk.matches(page(true, true, true), W, H, 61, 59, 69, 63, 16));
    }

    @Test
    public void isolatedRealDotIsPreserved() {
        assertFalse(AccidentalDotInk.matches(page(false, false, true), W, H, 61, 59, 69, 63, 16));
    }

    @Test
    public void oneAdjacentStemIsInsufficient() {
        assertFalse(AccidentalDotInk.matches(page(true, false, true), W, H, 61, 59, 69, 63, 16));
    }

    @Test
    public void separateInkBetweenTwoBarsIsNotConnected() {
        byte[] g = page(true, true, false);
        for (int y = 59; y <= 63; y++) for (int x = 64; x <= 66; x++) g[y * W + x] = 80;
        assertFalse(AccidentalDotInk.matches(g, W, H, 64, 59, 66, 63, 16));
    }

    @Test
    public void interruptedShaftsDoNotProveAnAccidental() {
        byte[] g = page(true, true, true);
        for (int y = 40; y <= 55; y++) {
            g[y * W + 60] = (byte) 255;
            g[y * W + 70] = (byte) 255;
        }
        assertFalse(AccidentalDotInk.matches(g, W, H, 61, 59, 69, 63, 16));
    }

    @Test
    public void faintPairedShaftLevelsRepeatWithoutChangingTheCaller() {
        for (int level : new int[] {164, 180, 198, 210}) {
            byte[] g = page(true, true, true);
            for (int i = 0; i < g.length; i++) if ((g[i] & 255) == 180) g[i] = (byte) level;
            byte[] before = g.clone();
            assertTrue(AccidentalDotInk.matches(g, W, H, 61, 59, 69, 63, 16));
            assertTrue(AccidentalDotInk.matches(g, W, H, 61, 59, 69, 63, 16));
            assertArrayEquals(before, g);
            Arrays.fill(g, (byte) 255);
            before = g.clone();
            assertFalse(AccidentalDotInk.matches(g, W, H, 61, 59, 69, 63, 16));
            assertArrayEquals(before, g);
        }
        byte[] g = page(true, true, true);
        for (int i = 0; i < g.length; i++) if ((g[i] & 255) == 180) g[i] = (byte) 211;
        byte[] before = g.clone();
        assertFalse(AccidentalDotInk.matches(g, W, H, 61, 59, 69, 63, 16));
        assertFalse(AccidentalDotInk.matches(g, W, H, 61, 59, 69, 63, 16));
        assertArrayEquals(before, g);
    }

    @Test
    public void translatedDifferentStrideStillRequiresTheConnectedCrossbar() {
        byte[] source = page(true, true, true);
        byte[] sourceBefore = source.clone();
        byte[] g = new byte[177 * 163];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) g[(y + 11) * 177 + x + 7] = source[y * W + x];
        byte[] before = g.clone();
        assertTrue(AccidentalDotInk.matches(g, 177, 163, 68, 70, 76, 74, 16));
        assertTrue(AccidentalDotInk.matches(g, 177, 163, 68, 70, 76, 74, 16));
        assertArrayEquals(before, g);
        for (int y = 70; y <= 74; y++) for (int x = 71; x <= 73; x++) g[y * 177 + x] = (byte) 255;
        before = g.clone();
        assertFalse(AccidentalDotInk.matches(g, 177, 163, 68, 70, 76, 74, 16));
        assertFalse(AccidentalDotInk.matches(g, 177, 163, 68, 70, 76, 74, 16));
        assertArrayEquals(before, g);
        assertArrayEquals(sourceBefore, source);
    }
}
