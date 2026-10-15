// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class TieBarlineCornerTest {
    static final int W = 300, H = 220;

    static byte[] drawing(boolean bar, boolean curve, boolean oval) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 60; y <= 124; y += 16) for (int x = 25; x < 193; x++) g[y * W + x] = 0;
        if (bar) for (int y = 60; y <= 124; y++) for (int x = 188; x <= 191; x++) g[y * W + x] = 0;
        if (curve)
            for (int x = 140; x < 182; x++) {
                int y = 124 + Math.round((182 - x) * .25f);
                for (int dy = -1; dy <= 1; dy++) g[(y + dy) * W + x] = 0;
            }
        if (oval)
            for (int y = 115; y <= 131; y++)
                for (int x = 167; x <= 187; x++)
                    if (Math.pow((x - 177) / 10., 2) + Math.pow((y - 123) / 8., 2) <= 1)
                        g[y * W + x] = 0;
        return g;
    }

    static boolean match(byte[] g) {
        return TieBarlineCorner.matches(g, W, H, 178, 185, 120, 126, 182, 123, 60, 124, 16);
    }

    @Test
    public void fullBarAndDepartingCurveRejectCorner() {
        assertTrue(match(drawing(true, true, false)));
    }

    @Test
    public void noBarPreservesHead() {
        assertFalse(match(drawing(false, true, false)));
    }

    @Test
    public void noCurvePreservesHead() {
        assertFalse(match(drawing(true, false, false)));
    }

    @Test
    public void rawOvalPreservesTinySemanticIsland() {
        assertFalse(match(drawing(true, true, true)));
    }

    @Test
    public void noPixelsPreservesHead() {
        assertFalse(match(null));
    }

    @Test
    public void largeHeadPreserved() {
        assertFalse(
                TieBarlineCorner.matches(
                        drawing(true, true, false),
                        W,
                        H,
                        168,
                        185,
                        113,
                        129,
                        177,
                        123,
                        60,
                        124,
                        16));
    }

    @Test
    public void mirroredTopLeftCornerHandled() {
        byte[] a = drawing(true, true, false), b = new byte[a.length];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) b[(H - 1 - y) * W + W - 1 - x] = a[y * W + x];
        assertTrue(
                TieBarlineCorner.matches(
                        b,
                        W,
                        H,
                        W - 1 - 185,
                        W - 1 - 178,
                        H - 1 - 126,
                        H - 1 - 120,
                        W - 1 - 182,
                        H - 1 - 123,
                        H - 1 - 124,
                        H - 1 - 60,
                        16));
    }
}
