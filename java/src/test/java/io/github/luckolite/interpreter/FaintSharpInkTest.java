// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural local-contrast and bounded-gap controls. */
public class FaintSharpInkTest {
    private static final int W = 90, H = 100, L = 20, T = 20, R = 55, B = 80, CW = R - L + 1;

    private byte[] page() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 240);
        return g;
    }

    private byte[] crop(byte[] g) {
        return FaintSharpInk.crop(g, W, H, L, T, R, B, 15.5f);
    }

    private void vertical(byte[] g, int first, int last, int shade) {
        for (int y = first; y <= last; y++) g[y * W + 35] = (byte) shade;
    }

    private int at(int y) {
        return (y - T) * CW + 35 - L;
    }

    @Test
    public void weakButConsistentLocalSpineSurvives() {
        byte[] g = page();
        vertical(g, 30, 60, 239);
        assertNotEquals(0, crop(g)[at(45)]);
    }

    @Test
    public void flatGrayPaperDoesNotBecomeInk() {
        for (byte b : crop(page())) assertEquals(0, b);
    }

    @Test
    public void threePixelGapHasTwoSidedSupport() {
        byte[] g = page();
        vertical(g, 30, 60, 80);
        vertical(g, 44, 46, 240);
        byte[] m = crop(g);
        for (int y = 44; y <= 46; y++) assertNotEquals(0, m[at(y)]);
    }

    @Test
    public void fourPixelGapStaysOpen() {
        byte[] g = page();
        vertical(g, 30, 60, 80);
        vertical(g, 44, 47, 240);
        assertEquals(0, crop(g)[at(45)]);
    }

    @Test
    public void isolatedEndpointsDoNotBridge() {
        byte[] g = page();
        vertical(g, 40, 40, 80);
        vertical(g, 44, 44, 80);
        assertEquals(0, crop(g)[at(42)]);
    }

    @Test
    public void borderOrInvalidScaleAbstains() {
        assertNull(FaintSharpInk.crop(page(), W, H, 0, T, R, B, 15.5f));
        assertNull(FaintSharpInk.crop(page(), W, H, L, T, R, B, Float.NaN));
    }

    @Test
    public void inputUnchanged() {
        byte[] g = page();
        vertical(g, 30, 60, 239);
        byte[] before = g.clone();
        crop(g);
        assertArrayEquals(before, g);
    }

    @Test
    public void noClosingWriteResultsAreFreshAndIndependentlyOwned() {
        byte[] g = page(), before = g.clone();
        byte[] first = crop(g), second = crop(g);
        assertNotSame(first, second);
        assertNotSame(g, first);
        assertArrayEquals(new byte[CW * (B - T + 1)], first);
        assertArrayEquals(first, second);
        first[0] = 42;
        assertEquals(0, second[0]);
        assertArrayEquals(new byte[CW * (B - T + 1)], crop(g));
        assertArrayEquals(before, g);
    }

    @Test
    public void completeTwoSpineOutputClosesOnlyTheSupportedShortGap() {
        byte[] g = page();
        vertical(g, 30, 60, 80);
        vertical(g, 44, 46, 240);
        for (int y = 30; y <= 60; y++) g[y * W + 45] = 80;
        for (int y = 44; y <= 47; y++) g[y * W + 45] = (byte) 240;
        byte[] expected = new byte[CW * (B - T + 1)];
        for (int y = 30; y <= 60; y++) {
            expected[(y - T) * CW + 35 - L] = OmrMeasurePostProcessor.CLEF_OR_KEY;
            if (y < 44 || y > 47)
                expected[(y - T) * CW + 45 - L] = OmrMeasurePostProcessor.CLEF_OR_KEY;
        }
        byte[] before = g.clone(), first = crop(g), second = crop(g);
        assertArrayEquals(expected, first);
        assertArrayEquals(expected, second);
        assertNotSame(first, second);
        first[0] = 42;
        assertArrayEquals(expected, second);
        assertArrayEquals(expected, crop(g));
        assertArrayEquals(before, g);
    }
}
