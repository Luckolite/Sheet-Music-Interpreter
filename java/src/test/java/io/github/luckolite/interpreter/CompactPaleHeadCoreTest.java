// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original compact shaded ovals; no published notation pixels. */
public class CompactPaleHeadCoreTest {
    byte[] page(int paper, int fill) {
        byte[] g = new byte[120 * 100];
        Arrays.fill(g, (byte) paper);
        for (int y = 40; y <= 52; y++)
            for (int x = 40; x <= 62; x++) {
                double a = (x - 51) / 11.0, b = (y - 46) / 6.0;
                if (a * a + b * b <= 1) g[y * 120 + x] = (byte) (a * a + b * b > .72 ? 65 : fill);
            }
        return g;
    }

    boolean check(byte[] g) {
        return CompactPaleHeadCore.proved(g, 120, 100, 40, 40, 62, 52, 51, 46, 20);
    }

    @Test
    public void paleCenterIsFilled() {
        assertTrue(check(page(250, 195)));
    }

    @Test
    public void whiteCounterPreserved() {
        assertFalse(check(page(250, 250)));
    }

    @Test
    public void nearPaperCounterPreserved() {
        assertFalse(check(page(250, 225)));
    }

    @Test
    public void shadedPaperPreserved() {
        assertFalse(check(page(195, 155)));
    }

    @Test
    public void darkRuleAcrossCounterPreserved() {
        byte[] g = page(250, 250);
        for (int y = 45; y <= 47; y++) for (int x = 35; x <= 68; x++) g[y * 120 + x] = 30;
        assertFalse(check(g));
    }

    @Test
    public void grayRuleAcrossCounterPreserved() {
        byte[] g = page(250, 250);
        for (int x = 35; x <= 68; x++) g[46 * 120 + x] = (byte) 180;
        assertFalse(check(g));
    }

    @Test
    public void verticalShaftAcrossCounterPreserved() {
        byte[] g = page(250, 250);
        for (int y = 30; y <= 60; y++) g[y * 120 + 51] = 30;
        assertFalse(check(g));
    }

    @Test
    public void diagonalScratchPreserved() {
        byte[] g = page(250, 250);
        for (int d = -8; d <= 8; d++) g[(46 + d) * 120 + 51 + d] = 40;
        assertFalse(check(g));
    }

    @Test
    public void smallGraceOutsideProof() {
        assertFalse(
                CompactPaleHeadCore.proved(page(250, 195), 120, 100, 44, 42, 58, 50, 51, 46, 20));
    }

    @Test
    public void mergedChordOutsideProof() {
        assertFalse(
                CompactPaleHeadCore.proved(page(250, 195), 120, 100, 40, 30, 62, 60, 51, 46, 20));
    }

    @Test
    public void malformedOutsideProof() {
        assertFalse(check(new byte[5]));
    }

    @Test
    public void noInputMutation() {
        byte[] g = page(250, 195), b = g.clone();
        assertTrue(check(g));
        assertArrayEquals(b, g);
    }

    @Test
    public void scaleAndMirror() {
        byte[] g = page(250, 195), s = new byte[240 * 200];
        for (int y = 0; y < 200; y++)
            for (int x = 0; x < 240; x++) s[y * 240 + x] = g[(y / 2) * 120 + 119 - x / 2];
        assertTrue(CompactPaleHeadCore.proved(s, 240, 200, 114, 80, 159, 105, 137, 93, 40));
    }
}
