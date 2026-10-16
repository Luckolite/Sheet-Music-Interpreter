// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original silent staff with a full rest centered before a two-stroke ending. */
public class DoubleBarRestCenterTest {
    static final int W = 600, H = 260;
    byte[] g = new byte[W * H];

    DoubleBarRestCenterTest setup(boolean inner, boolean outer, int center) {
        Arrays.fill(g, (byte) 255);
        for (int line = 0; line < 5; line++) box(20, 80 + line * 16, 580, 80 + line * 16);
        box(center - 10, 97, center + 10, 103);
        if (inner) box(486, 80, 487, 144);
        if (outer) box(500, 80, 508, 144);
        return this;
    }

    void box(int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = 0;
    }

    List<WholeRestClassifier.Decision> read() {
        return WholeRestClassifier.detect(
                g,
                W,
                H,
                List.of(new MeasureRegion(300f / W, 509f / W, 60f / H, 170f / H)),
                List.of(new SixteenthRestDetector.Staff(80, 144, 16, 0, 1)),
                List.of(),
                List.of());
    }

    boolean full() {
        var d = read();
        assertEquals(1, d.size());
        return d.get(0).event().isFullMeasure();
    }

    @Test
    public void independentDoubleBarOwnsCenter() {
        assertTrue(setup(true, true, 393).full());
    }

    @Test
    public void loneStrokeCannotMoveCenter() {
        assertFalse(setup(false, true, 393).full());
    }

    @Test
    public void missingOuterStrokeCannotMoveCenter() {
        assertFalse(setup(true, false, 393).full());
    }

    @Test
    public void offCenterLiteralRemainsUnresolved() {
        assertFalse(setup(true, true, 360).full());
    }

    @Test
    public void extendedStemCannotOwnCenter() {
        setup(true, true, 393);
        box(486, 65, 487, 80);
        assertFalse(full());
    }

    @Test
    public void incompleteSecondStrokeCannotOwnCenter() {
        setup(true, true, 393);
        for (int y = 105; y < 125; y++) for (int x = 500; x <= 508; x++) g[y * W + x] = (byte) 255;
        assertFalse(full());
    }

    @Test
    public void restGeometryAndPixelsRemainUnchanged() {
        setup(true, true, 393);
        var before = g.clone();
        var e = read().get(0).event();
        assertEquals((393f - 300) / 209, e.positionInMeasure(), .001f);
        assertArrayEquals(before, g);
    }
}
