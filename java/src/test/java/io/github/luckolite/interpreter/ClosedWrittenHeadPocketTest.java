// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original closed ovals and raw notches, with independently connected shafts. */
public final class ClosedWrittenHeadPocketTest {
    static final int W = 180, H = 180, X = 80, Y = 80, G = 12, L = 73, R = 87, T = 74, B = 86;

    byte[] page(int paper, int direction, float slope, boolean hollow) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        for (int y = T; y <= B; y++)
            for (int x = L; x <= R; x++) {
                double oval = Math.pow((x - X) / 7., 2) + Math.pow((y - Y) / 5., 2);
                if (oval <= 1 && (!hollow || oval > .5)) p[y * W + x] = (byte) (paper - 80);
            }
        for (int d : new int[] {-1, 1})
            if (direction == 0 || direction == -d)
                for (int n = 0; n <= 38; n++) {
                    int x = Math.round((d < 0 ? R : L) - slope * d * n), y = Y + d * n;
                    for (int q = -1; q <= 1; q++) p[y * W + x + q] = (byte) (paper - 80);
                }
        return p;
    }

    boolean proved(byte[] p, float slope) {
        return ClosedWrittenHeadPocket.proved(p, W, H, L, T, R, B, X, Y, G, slope);
    }

    byte[] corner(int cornerTone) {
        byte[] p = page(140, -1, 0, false);
        for (int y = 79; y <= 81; y++) for (int x = 77; x <= 82; x++) p[y * W + x] = (byte) 110;
        for (int x = 79; x <= 81; x++) p[Y * W + x] = (byte) 140;
        p[82 * W + 83] = (byte) cornerTone;
        p[83 * W + 84] = (byte) cornerTone;
        return p;
    }

    @Test
    public void shadedHalfRequiresOwnUpShaft() {
        assertTrue(proved(page(140, 1, 0, true), 0));
    }

    @Test
    public void shadedHalfRequiresOwnDownShaft() {
        assertTrue(proved(page(150, -1, 0, true), 0));
    }

    @Test
    public void fallingPrintedAxisKeepsSustainedOval() {
        assertTrue(proved(page(150, 1, .12f, true), .12f));
    }

    @Test
    public void risingPrintedAxisKeepsSustainedOval() {
        assertTrue(proved(page(150, -1, -.12f, true), -.12f));
    }

    @Test
    public void shadedDiagonalAntialiasDoesNotOpenPrintedContour() {
        byte[] p = corner(110);
        assertFalse(ShadedHollowPocket.proved(p, W, H, L, T, R, B, G));
        assertTrue(proved(p, 0));
    }

    @Test
    public void clearPaperDiagonalNotchRemainsOpen() {
        assertFalse(proved(corner(140), 0));
    }

    @Test
    public void directPaperNotchRemainsOpen() {
        byte[] p = page(140, -1, 0, true);
        for (int x = X; x <= R; x++) p[Y * W + x] = (byte) 140;
        assertFalse(proved(p, 0));
    }

    @Test
    public void solidPrintedHeadDoesNotBecomeHalf() {
        assertFalse(proved(page(140, -1, 0, false), 0));
    }

    @Test
    public void fourPixelWhiteSpeckleIsInsufficient() {
        byte[] p = page(140, -1, 0, false);
        for (int y = 79; y <= 80; y++) for (int x = 79; x <= 80; x++) p[y * W + x] = (byte) 140;
        assertFalse(proved(p, 0));
    }

    @Test
    public void compactWhiteSpeckleIsNotSustainedContour() {
        byte[] p = page(140, -1, 0, false);
        for (int y = 79; y <= 81; y++) for (int x = 78; x <= 81; x++) p[y * W + x] = (byte) 140;
        assertFalse(proved(p, 0));
    }

    @Test
    public void twoOpposedShaftsDoNotInventHalfOwnership() {
        assertFalse(proved(page(140, 0, 0, true), 0));
    }

    @Test
    public void disconnectedShaftDoesNotOwnHollowBody() {
        byte[] p = page(140, -1, 0, true);
        for (int y = 90; y <= 98; y++)
            for (int x = L - 2; x <= L + 2; x++) p[y * W + x] = (byte) 140;
        assertFalse(proved(p, 0));
    }

    @Test
    public void stemlessWholeIsNotDurationFallback() {
        byte[] p = page(140, -1, 0, true);
        for (int y = B + 1; y <= Y + 38; y++)
            for (int x = L - 2; x <= L + 2; x++) p[y * W + x] = (byte) 140;
        assertFalse(proved(p, 0));
    }

    @Test
    public void tinyGraceBodyDoesNotUseFullSizeFallback() {
        byte[] p = page(140, 1, 0, false);
        for (int y = T; y <= B; y++) for (int x = L; x <= R; x++) p[y * W + x] = (byte) 140;
        for (int y = 78; y <= 82; y++)
            for (int x = 77; x <= 83; x++)
                if (x == 77 || x == 83 || y == 78 || y == 82) p[y * W + x] = 60;
        assertFalse(ClosedWrittenHeadPocket.proved(p, W, H, 77, 78, 83, 82, X, Y, G, 0));
    }

    @Test
    public void originalGrayAndInputBoundsRemainProtected() {
        byte[] p = page(140, -1, 0, true), copy = p.clone();
        assertTrue(proved(p, 0));
        assertArrayEquals(copy, p);
        assertFalse(ClosedWrittenHeadPocket.proved(new byte[1], W, H, L, T, R, B, X, Y, G, 0));
        assertFalse(ClosedWrittenHeadPocket.proved(p, W, H, L, T, R, B, X, Y, G, Float.NaN));
    }
}
