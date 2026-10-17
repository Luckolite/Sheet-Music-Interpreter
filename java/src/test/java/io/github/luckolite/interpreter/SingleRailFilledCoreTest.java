// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;

/** Original small oval fixtures, not rasterized published notation. */
public class SingleRailFilledCoreTest {
    private byte[] page(int paper, int fill) {
        byte[] p = new byte[120 * 100];
        Arrays.fill(p, (byte) paper);
        for (int y = 40; y <= 58; y++)
            for (int x = 40; x <= 65; x++) {
                double r = Math.pow((x - 52.5) / 12.5, 2) + Math.pow((y - 49) / 9.0, 2);
                if (r <= 1) p[y * 120 + x] = (byte) (r > .7 ? 25 : fill);
            }
        return p;
    }

    private boolean filled(byte[] p) {
        return PrintedSingleRailCore.proved(p, 120, 100, 40, 40, 65, 58, 52.5f, 49, 20);
    }

    @Test
    public void greyFilledCoreContradictsAbsoluteWhiteThreshold() {
        assertTrue(filled(page(235, 189)));
    }

    @Test
    public void darkMixedCoreStillHasPositiveFilledArea() {
        byte[] p = page(250, 130);
        for (int y = 45; y <= 52; y++)
            for (int x = 48; x <= 57; x++) if ((x + y) % 5 == 0) p[y * 120 + x] = (byte) 175;
        assertTrue(filled(p));
    }

    @Test
    public void ordinaryHollowCounterIsPreserved() {
        assertFalse(filled(page(235, 235)));
    }

    @Test
    public void ledgerAcrossHollowCounterStillPreserved() {
        byte[] p = page(235, 235);
        for (int y = 46; y <= 51; y++) for (int x = 32; x <= 73; x++) p[y * 120 + x] = 20;
        assertFalse(filled(p));
    }

    @Test
    public void shadedPaperDoesNotSupplyGrayFillProof() {
        assertFalse(filled(page(190, 170)));
    }

    @Test
    public void nearPaperPaleCounterIsNotSolidFill() {
        assertFalse(filled(page(235, 216)));
    }

    @Test
    public void smallGraceHeadIsOutsideOverride() {
        assertFalse(
                PrintedSingleRailCore.proved(page(235, 189), 120, 100, 40, 43, 55, 55, 47, 49, 20));
    }

    @Test
    public void mergedChordOutsideOverride() {
        assertFalse(
                PrintedSingleRailCore.proved(page(235, 189), 120, 100, 40, 30, 65, 68, 52, 49, 20));
    }

    @Test
    public void malformedArrayIsRejected() {
        assertFalse(
                PrintedSingleRailCore.proved(new byte[5], 120, 100, 40, 40, 65, 58, 52, 49, 20));
    }

    @Test
    public void inputPixelsUnchanged() {
        byte[] p = page(235, 189), before = p.clone();
        assertTrue(filled(p));
        assertArrayEquals(before, p);
    }
}
