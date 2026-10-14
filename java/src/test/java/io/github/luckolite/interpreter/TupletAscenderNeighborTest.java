// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural text silhouettes, with no retained score pixels. */
public class TupletAscenderNeighborTest {
    static final int W = 180, H = 100, L = 70, T = 40, R = 80, B = 55;
    final byte[] gray = new byte[W * H];

    public TupletAscenderNeighborTest() {
        Arrays.fill(gray, (byte) 255);
        line(L, T, R, T);
        line(L, T, L, T + 6);
        line(L, T + 6, R, T + 6);
        line(R, T + 6, R, B);
        line(L, B, R, B);
    }

    void line(int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) gray[y * W + x] = 0;
    }

    void ascender(int x, int top, int bottom) {
        line(x, top, x + 2, bottom);
        line(x, top + 10, x + 10, top + 12);
        line(x + 8, top + 10, x + 10, bottom);
    }

    boolean joined() {
        return TupletNumeralNeighbors.joinedText(gray, W, H, L, T, R, B);
    }

    @Test
    public void rightBaselineAscenderIsText() {
        ascender(R + 3, T - 9, B);
        assertTrue(joined());
    }

    @Test
    public void leftBaselineAscenderIsText() {
        ascender(L - 13, T - 9, B);
        assertTrue(joined());
    }

    @Test
    public void isolatedNumeralRemainsValid() {
        assertFalse(joined());
    }

    @Test
    public void distantAscenderDoesNotJoin() {
        ascender(R + 12, T - 9, B);
        assertFalse(joined());
    }

    @Test
    public void verticallyDisplacedLetterDoesNotJoin() {
        ascender(R + 3, T - 16, B - 7);
        assertFalse(joined());
    }

    @Test
    public void thinNoteStemDoesNotJoin() {
        line(R + 3, T - 9, R + 4, B);
        assertFalse(joined());
    }

    @Test
    public void clippedTallStrokeDoesNotJoin() {
        ascender(R + 3, T - 30, B);
        assertFalse(joined());
    }

    @Test
    public void sameHeightLetterStillJoins() {
        ascender(R + 3, T, B);
        assertTrue(joined());
    }

    @Test
    public void originalPixelsArePreserved() {
        ascender(R + 3, T - 9, B);
        byte[] before = gray.clone();
        joined();
        assertArrayEquals(before, gray);
    }
}
