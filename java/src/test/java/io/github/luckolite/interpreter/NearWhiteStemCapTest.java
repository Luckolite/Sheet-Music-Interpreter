// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original bright-paper caps and independently contrasted faint shafts. */
public class NearWhiteStemCapTest {
    static final int W = 80, H = 80;

    byte[] image(int paper, int shaft) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) paper);
        for (int y = 25; y < 40; y++) for (int x = 39; x <= 41; x++) g[y * W + x] = (byte) shaft;
        return g;
    }

    boolean cap(byte[] g) {
        return PaleBeamRecovery.crossesBlankCap(
                g, W, H, new int[] {40, 40, -1}, new int[] {40, 24, -1}, 16f);
    }

    @Test
    public void brightPaperDoesNotNeedClippedWhite() {
        for (int paper : new int[] {235, 240, 245, 249, 255})
            assertTrue("paper=" + paper, cap(image(paper, paper)));
    }

    @Test
    public void visibleFaintShaftRetainsItsContrast() {
        for (int paper : new int[] {240, 245, 250, 255}) assertFalse(cap(image(paper, paper - 20)));
    }

    @Test
    public void tenLevelsOfShaftContrastRemain() {
        assertFalse(cap(image(245, 235)));
    }

    @Test
    public void aSinglePaperRowCannotBreakAShaft() {
        byte[] g = image(245, 210);
        for (int x = 39; x <= 41; x++) g[34 * W + x] = (byte) 245;
        assertFalse(cap(g));
    }

    @Test
    public void darkerPaperIsOutsideThisProof() {
        assertFalse(cap(image(220, 220)));
    }

    @Test
    public void inputPixelsRemainExact() {
        byte[] g = image(245, 245), copy = g.clone();
        cap(g);
        assertArrayEquals(copy, g);
    }
}
