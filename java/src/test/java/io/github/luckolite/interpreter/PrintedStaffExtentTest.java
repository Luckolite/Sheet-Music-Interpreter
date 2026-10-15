// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedStaffExtentTest {
    static final int W = 600, H = 200;
    static final float GAP = 10;

    byte[] page(boolean textured, int start, int value, boolean curved) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        if (textured)
            for (int y = 40; y < 130; y++)
                for (int x = 20; x < 170; x++)
                    g[y * W + x] =
                            (byte) Math.round(205 + 20 * Math.sin(x / 3.) + 10 * Math.cos(y / 30.));
        for (int x = start; x < 580; x++)
            for (int line = 0; line < 5; line++) {
                int y = Math.round(60 + line * GAP + (curved ? x * .04f : 0));
                g[y * W + x] = (byte) value;
            }
        return g;
    }

    int left(byte[] g, boolean curved) {
        return PrintedStaffExtent.left(
                g, W, H, 20, 580, GAP, (line, x) -> 60 + line * GAP + (curved ? x * .04f : 0));
    }

    @Test
    public void unsupportedTexturedMarginIsTrimmed() {
        assertEquals(180, left(page(true, 200, 20, false), false));
    }

    @Test
    public void faintRulesAcrossMarginArePreserved() {
        assertEquals(20, left(page(false, 20, 245, false), false));
    }

    @Test
    public void blankMissingRulesAreNotTextureProof() {
        assertEquals(20, left(page(false, 200, 20, false), false));
    }

    @Test
    public void curvedFaintRulesArePreserved() {
        assertEquals(20, left(page(false, 20, 245, true), true));
    }

    @Test
    public void missingRasterPreservesExtent() {
        assertEquals(20, left(null, false));
    }

    byte[] mirror(byte[] a) {
        if (a == null) return null;
        byte[] b = new byte[a.length];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) b[y * W + W - 1 - x] = a[y * W + x];
        return b;
    }

    int right(byte[] g, boolean curved) {
        return PrintedStaffExtent.right(
                mirror(g),
                W,
                H,
                W - 1 - 580,
                W - 1 - 20,
                GAP,
                (line, x) -> 60 + line * GAP + (curved ? (W - 1 - x) * .04f : 0));
    }

    @Test
    public void rightTexturedMarginIsTrimmed() {
        assertEquals(W - 1 - 180, right(page(true, 200, 20, false), false));
    }

    @Test
    public void rightFaintRulesArePreserved() {
        assertEquals(W - 1 - 20, right(page(false, 20, 245, false), false));
    }

    @Test
    public void rightBlankMissingRulesPreserved() {
        assertEquals(W - 1 - 20, right(page(false, 200, 20, false), false));
    }

    @Test
    public void rightCurvedFaintRulesPreserved() {
        assertEquals(W - 1 - 20, right(page(false, 20, 245, true), true));
    }

    @Test
    public void rightMissingRasterPreserved() {
        assertEquals(W - 1 - 20, right(null, false));
    }
}
