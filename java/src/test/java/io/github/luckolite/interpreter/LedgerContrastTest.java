// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LedgerContrastTest {
    static final int W = 100, H = 100;

    byte[] paper(int tone) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) tone);
        return g;
    }

    boolean proof(byte[] g) {
        return LedgerRuleContrast.proved(g, W, H, 40, 60, 50, 14);
    }

    @Test
    public void faintLedgerRemains() {
        byte[] g = paper(255);
        for (int x = 33; x <= 67; x++) g[50 * W + x] = (byte) 245;
        assertTrue(proof(g));
    }

    @Test
    public void faintShadedLedgerRemains() {
        byte[] g = paper(170);
        for (int x = 33; x <= 67; x++) g[50 * W + x] = (byte) 160;
        assertTrue(proof(g));
    }

    @Test
    public void oneSidedStrokeNotBilateralProof() {
        byte[] g = paper(255);
        for (int x = 33; x < 40; x++) g[50 * W + x] = 0;
        assertFalse(proof(g));
    }

    @Test
    public void broadShadeNotLedger() {
        byte[] g = paper(180);
        for (int y = 30; y < 75; y++)
            for (int x = 20; x < 80; x++) g[y * W + x] = (byte) (220 - 2 * y);
        assertFalse(proof(g));
        assertTrue(SmoothLedgerBackground.matches(g, W, H, 40, 40, 60, 60));
    }

    @Test
    public void blankPaperNotRejectionEvidence() {
        assertFalse(SmoothLedgerBackground.matches(paper(150), W, H, 40, 40, 60, 60));
    }

    @Test
    public void sharpPrintedInkNotBackground() {
        byte[] g = paper(180);
        for (int y = 40; y <= 60; y++) for (int x = 45; x <= 55; x++) g[y * W + x] = 40;
        assertFalse(SmoothLedgerBackground.matches(g, W, H, 40, 40, 60, 60));
    }

    @Test
    public void tinyToneRangeNotBackgroundEvidence() {
        byte[] g = paper(180);
        for (int y = 40; y <= 60; y++)
            for (int x = 40; x <= 60; x++) g[y * W + x] = (byte) (170 + y - 40);
        assertFalse(SmoothLedgerBackground.matches(g, W, H, 40, 40, 60, 60));
    }

    @Test
    public void absentPixelsNeverProveBackground() {
        assertFalse(SmoothLedgerBackground.matches(null, W, H, 40, 40, 60, 60));
    }
}
