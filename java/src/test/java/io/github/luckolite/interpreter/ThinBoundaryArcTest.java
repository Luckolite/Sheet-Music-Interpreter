// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Procedural separator and crossing-curve controls; no source-score pixels. */
public class ThinBoundaryArcTest {
    final int w = 600, h = 260, x = 300, a = 80, gap = 16, b = 144;

    byte[] image(boolean bar) {
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int i = 0; i < 5; i++) rect(g, 30, a + i * gap, 570, a + i * gap);
        if (bar) rect(g, x, a, x + 1, b);
        return g;
    }

    void rect(byte[] g, int l, int t, int r, int bottom) {
        for (int y = t; y <= bottom; y++) for (int xx = l; xx <= r; xx++) g[y * w + xx] = 20;
    }

    void arc(byte[] g) {
        for (int dx = -20; dx <= 20; dx++) {
            int y = a + 5 + Math.round(dx * .15f);
            rect(g, x + dx, y, x + dx, y + 1);
        }
    }

    int pieces(byte[] g) {
        var full = new MeasureRegion(.05f, .95f, .10f, .90f);
        return PrintedMeasureRhythmGuard.preservePrintedBoundaries(
                        List.of(
                                new MeasureRegion(.05f, .495f, .10f, .90f),
                                new MeasureRegion(.505f, .95f, .10f, .90f)),
                        List.of(full),
                        g,
                        w,
                        h)
                .size();
    }

    @Test
    public void crossingCurvePreservesIndependentSeparator() {
        byte[] g = image(true);
        arc(g);
        byte[] before = g.clone();
        assertEquals(2, pieces(g));
        assertArrayEquals(before, g);
    }

    @Test
    public void curveWithoutSeparatorCannotSplit() {
        byte[] g = image(false);
        arc(g);
        assertEquals(1, pieces(g));
    }

    @Test
    public void extendedNoteStemCannotSplit() {
        byte[] g = image(true);
        arc(g);
        rect(g, x, b, x + 1, b + 16);
        assertEquals(1, pieces(g));
    }

    @Test
    public void broadAttachedHeadCannotSplit() {
        byte[] g = image(true);
        rect(g, x - 12, a - 3, x + 12, a + 8);
        assertEquals(1, pieces(g));
    }

    @Test
    public void detachedFragmentsDoNotProveCurve() {
        byte[] g = image(true);
        arc(g);
        for (int y = a + 3; y < a + 14; y++) g[y * w + x + 8] = (byte) 255;
        assertFalse(ThinBoundaryArc.proved(g, w, h, x, a, gap, 1));
    }

    @Test
    public void symmetricBandIsNotCrossingCurve() {
        byte[] g = image(true);
        rect(g, x - 20, a + 4, x + 20, a + 5);
        assertFalse(ThinBoundaryArc.proved(g, w, h, x, a, gap, 1));
    }
}
