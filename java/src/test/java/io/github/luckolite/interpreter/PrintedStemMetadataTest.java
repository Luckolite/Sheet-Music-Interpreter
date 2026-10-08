// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original filled/hollow/grace heads with connected, opposed, disconnected and inclined shafts. */
public final class PrintedStemMetadataTest {
    static final int W = 180, H = 180, X = 80, Y = 80, G = 12, L = 73, R = 87;

    byte[] page(
            int direction, int paper, float slope, boolean rules, boolean broken, boolean hollow) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        if (rules)
            for (int cy : new int[] {56, 68, 80, 92, 104})
                for (int x = 10; x < W - 10; x++)
                    p[(cy + Math.round(slope * (x - X))) * W + x] = (byte) (paper - 45);
        for (int yy = 74; yy <= 86; yy++)
            for (int x = L; x <= R; x++) {
                double e = Math.pow((x - X) / 7., 2) + Math.pow((yy - Y) / 5., 2);
                if (e <= 1 && (!hollow || e > .5)) p[yy * W + x] = (byte) (paper - 80);
            }
        for (int d : new int[] {-1, 1})
            if (direction == 0 || direction == -d)
                for (int n = 0; n <= 38; n++) {
                    if (broken && n >= 10 && n <= 17) continue;
                    int xx = Math.round((d < 0 ? R : L) - slope * d * n), yy = Y + d * n;
                    for (int q = -1; q <= 1; q++) p[yy * W + xx + q] = (byte) (paper - 80);
                }
        return p;
    }

    int detect(byte[] p, float slope) {
        return PrintedStemMetadata.detect(p, W, H, L, R, 74, 86, X, Y, G, slope);
    }

    @Test
    public void shadedUpAndDownShaftsAreRead() {
        assertEquals(1, detect(page(1, 140, 0, false, false, false), 0));
        assertEquals(-1, detect(page(-1, 140, 0, false, false, false), 0));
    }

    @Test
    public void darkPaperRetainsContrastProof() {
        assertEquals(-1, detect(page(-1, 100, 0, true, false, false), 0));
    }

    @Test
    public void whitePaperHasSamePhysicalDirection() {
        assertEquals(1, detect(page(1, 250, 0, true, false, false), 0));
    }

    @Test
    public void risingPageUsesItsPhysicalAxis() {
        assertEquals(-1, detect(page(-1, 150, -.12f, true, false, false), -.12f));
    }

    @Test
    public void fallingPageUsesItsPhysicalAxis() {
        assertEquals(1, detect(page(1, 150, .12f, true, false, false), .12f));
    }

    @Test
    public void twoOpposedVoicesRemainUnknown() {
        assertEquals(0, detect(page(0, 140, .08f, true, false, false), .08f));
    }

    @Test
    public void disconnectedShaftDoesNotSupplyMetadata() {
        assertEquals(0, detect(page(1, 140, 0, true, true, false), 0));
    }

    @Test
    public void hollowHalfHeadCanOwnShaft() {
        assertEquals(-1, detect(page(-1, 150, 0, true, false, true), 0));
    }

    @Test
    public void stemlessWholeRemainsUnknown() {
        byte[] p = page(-1, 150, 0, false, false, true);
        for (int y = 86; y <= 118; y++)
            for (int x = L - 3; x <= L + 3; x++) p[y * W + x] = (byte) 150;
        assertEquals(0, detect(p, 0));
    }

    @Test
    public void horizontalRulesCannotInventDirection() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 140);
        for (int cy : new int[] {56, 68, 80, 92, 104})
            for (int x = 10; x < W - 10; x++) p[cy * W + x] = 60;
        assertEquals(0, detect(p, 0));
    }

    @Test
    public void reducedGraceHeadOwnsShortPrintedShaft() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 150);
        for (int y = 77; y <= 83; y++)
            for (int x = 76; x <= 84; x++)
                if (Math.pow((x - X) / 4., 2) + Math.pow((y - Y) / 3., 2) <= 1) p[y * W + x] = 50;
        for (int y = 60; y <= 80; y++) p[y * W + 84] = 50;
        assertEquals(1, PrintedStemMetadata.detect(p, W, H, 76, 84, 77, 83, X, Y, 6, 0));
    }

    @Test
    public void obscuredChordKeepsUnknownAndClearLowerShaft() {
        byte[] p = page(-1, 150, 0, true, false, false);
        for (int y = 90; y <= 102; y++)
            for (int x = L; x <= R; x++)
                if (Math.pow((x - X) / 7., 2) + Math.pow((y - 96) / 5., 2) <= 1) p[y * W + x] = 70;
        for (int y = 96; y <= 134; y++) for (int x = L - 1; x <= L + 1; x++) p[y * W + x] = 70;
        assertEquals(0, detect(p, 0));
        assertEquals(-1, PrintedStemMetadata.detect(p, W, H, L, R, 90, 102, X, 96, G, 0));
    }

    @Test
    public void neighboringSeparateStemDoesNotBelongToHead() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 150);
        for (int y = 74; y <= 86; y++)
            for (int x = L; x <= R; x++)
                if (Math.pow((x - X) / 7., 2) + Math.pow((y - Y) / 5., 2) <= 1) p[y * W + x] = 70;
        for (int y = 80; y <= 118; y++) p[y * W + 96] = 70;
        assertEquals(0, detect(p, 0));
    }

    @Test
    public void broadDarkBodyCannotSupplyNarrowShaft() {
        byte[] p = page(-1, 140, 0, false, false, false);
        for (int y = 80; y <= 118; y++) for (int x = L - 7; x <= L + 7; x++) p[y * W + x] = 60;
        assertEquals(0, detect(p, 0));
    }

    @Test
    public void originalImageRemainsUnchanged() {
        byte[] p = page(-1, 140, -.12f, true, false, false), b = p.clone();
        detect(p, -.12f);
        assertArrayEquals(b, p);
    }

    @Test
    public void malformedPixelsAndPhaseAreRejected() {
        assertEquals(0, PrintedStemMetadata.detect(new byte[1], W, H, L, R, 74, 86, X, Y, G, 0));
        assertEquals(
                0,
                PrintedStemMetadata.detect(
                        page(-1, 140, 0, false, false, false),
                        W,
                        H,
                        L,
                        R,
                        74,
                        86,
                        X,
                        Y,
                        G,
                        Float.NaN));
    }

    @Test
    public void oversizedGapAndInvalidOwnerCenterAreRejected() {
        byte[] p = page(-1, 140, 0, false, false, false);
        assertEquals(
                0, PrintedStemMetadata.detect(p, W, H, L, R, 74, 86, X, Y, Float.MAX_VALUE, 0));
        assertEquals(0, PrintedStemMetadata.detect(p, W, H, L, R, 74, 86, 160, Y, G, 0));
    }
}
