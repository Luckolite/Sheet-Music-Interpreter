// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original ruled-paper and boundary geometry; no score pixels, outlines, or source coordinates. */
public class PrintedBoundaryRestInkTest {
    static final int W = 400, H = 260, G = 14, T = 80, L = 40, R = 350;

    static class Page {
        final byte[] gray = new byte[W * H];
        final MeasureRegion region =
                new MeasureRegion(L / (float) W, R / (float) W, 40f / H, 170f / H);
        final ScoreRestEvent rest =
                new ScoreRestEvent(0, (343.5f - L) / (R - L), 99.5f / H, 43f / H, 0, 1, 1);

        Page() {
            Arrays.fill(gray, (byte) 255);
            for (int n = 0; n < 5; n++) rule(T + n * G);
            for (int y = T; y <= T + 4 * G + 2; y++)
                for (int x = R; x <= R + 1; x++) gray[y * W + x] = 20;
        }

        void rule(int y) {
            for (int yy = y; yy <= y + 2; yy++)
                for (int x = L; x <= R + 1; x++) gray[yy * W + x] = 35;
        }

        boolean owns() {
            return PrintedBoundaryRestInk.owns(gray, W, H, region, rest, G, 334, 353, 78, 120);
        }

        void quarter(int shade, int shift) {
            int[][] p = {{342, 85}, {349, 94}, {343, 100}, {351, 108}, {343, 114}, {346, 119}};
            for (int i = 1; i < p.length; i++) {
                int[] a = p[i - 1], b = p[i];
                int count = Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
                for (int k = 0; k <= count; k++) {
                    int x = Math.round(a[0] + (b[0] - a[0]) * k / (float) count) + shift,
                            y = Math.round(a[1] + (b[1] - a[1]) * k / (float) count);
                    for (int yy = y - 1; yy <= y + 1; yy++)
                        for (int xx = x - 1; xx <= x + 1; xx++) gray[yy * W + xx] = (byte) shade;
                }
            }
        }
    }

    @Test
    public void fiveRulesAndBoundaryContainNoIndependentQuarter() {
        assertTrue(new Page().owns());
    }

    @Test
    public void onePixelAntialiasFringeRemainsOwned() {
        Page p = new Page();
        for (int y = 84; y < 94; y++) p.gray[y * W + 352] = (byte) 150;
        assertTrue(p.owns());
    }

    @Test
    public void nearbyQuarterHasIndependentOffRuleBody() {
        Page p = new Page();
        p.quarter(20, -2);
        assertFalse(p.owns());
    }

    @Test
    public void crossingQuarterKeepsItsOffRuleBody() {
        Page p = new Page();
        p.quarter(20, 0);
        assertFalse(p.owns());
    }

    @Test
    public void faintQuarterBodyIsStillIndependent() {
        Page p = new Page();
        p.quarter(150, 0);
        assertFalse(p.owns());
    }

    @Test
    public void detachedVerticalPiecesDoNotProveBoundary() {
        Page p = new Page();
        for (int y = 103; y <= 105; y++)
            for (int x = R; x <= R + 1; x++) p.gray[y * W + x] = (byte) 255;
        assertFalse(p.owns());
    }

    @Test
    public void missingRuleCannotProveStaff() {
        Page p = new Page();
        for (int y = 108; y <= 110; y++) for (int x = L; x < R; x++) p.gray[y * W + x] = (byte) 255;
        assertFalse(p.owns());
    }

    @Test
    public void competingSixRuleFramesFailClosed() {
        Page p = new Page();
        p.rule(T - G);
        assertFalse(p.owns());
    }

    @Test
    public void wrongGapCannotBorrowFiveRules() {
        Page p = new Page();
        assertFalse(
                PrintedBoundaryRestInk.owns(p.gray, W, H, p.region, p.rest, 17, 334, 353, 78, 120));
    }

    @Test
    public void unrelatedDistantStaffCannotOwnTheClaim() {
        Page p = new Page();
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < H - 80; y++) System.arraycopy(p.gray, y * W, g, (y + 80) * W, W);
        assertFalse(PrintedBoundaryRestInk.owns(g, W, H, p.region, p.rest, G, 334, 353, 78, 120));
    }

    @Test
    public void wrongBoundaryCannotBorrowTheRealBarline() {
        Page p = new Page();
        MeasureRegion m = new MeasureRegion(L / (float) W, 330f / W, 40f / H, 170f / H);
        ScoreRestEvent r =
                new ScoreRestEvent(0, (323.5f - L) / (330 - L), 99.5f / H, 43f / H, 0, 1, 1);
        assertFalse(PrintedBoundaryRestInk.owns(p.gray, W, H, m, r, G, 314, 333, 78, 120));
    }

    @Test
    public void leftBoundaryUsesItsIndependentInteriorStrips() {
        Page p = new Page();
        byte[] g = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) g[y * W + x] = p.gray[y * W + W - 1 - x];
        MeasureRegion m =
                new MeasureRegion(
                        (W - 1 - R) / (float) W, (W - 1 - L) / (float) W, 40f / H, 170f / H);
        ScoreRestEvent r =
                new ScoreRestEvent(0, 1 - p.rest.positionInMeasure(), 99.5f / H, 43f / H, 0, 1, 1);
        assertTrue(
                PrintedBoundaryRestInk.owns(g, W, H, m, r, G, W - 1 - 353, W - 1 - 334, 78, 120));
    }

    @Test
    public void ownedSourceIsReadOnly() {
        Page p = new Page();
        byte[] g = p.gray.clone();
        assertTrue(p.owns());
        assertArrayEquals(g, p.gray);
    }

    @Test
    public void invalidDimensionsFailBeforeIndexing() {
        Page p = new Page();
        assertFalse(
                PrintedBoundaryRestInk.owns(
                        p.gray, W + 1, H, p.region, p.rest, G, 334, 353, 78, 120));
    }

    @Test
    public void oneColumnDoesNotProveFixedBoundary() {
        Page p = new Page();
        for (int y = T; y <= T + 4 * G + 2; y++) if (y % G > 2) p.gray[y * W + R + 1] = (byte) 255;
        assertFalse(p.owns());
    }

    @Test
    public void offRulePaperIslandCannotAttachToBoundary() {
        Page p = new Page();
        p.gray[102 * W + 346] = 20;
        assertFalse(p.owns());
    }

    @Test
    public void paleQuarterBodyAt180VetoesArtifactOwnership() {
        Page p = new Page();
        p.quarter(180, 0);
        assertFalse(p.owns());
    }

    @Test
    public void paleQuarterBodyAt200VetoesArtifactOwnership() {
        Page p = new Page();
        p.quarter(200, 0);
        assertFalse(p.owns());
    }

    @Test
    public void paleOffRuleBodyAt220IsNotPaper() {
        Page p = new Page();
        p.quarter(220, -2);
        assertFalse(p.owns());
    }
}
