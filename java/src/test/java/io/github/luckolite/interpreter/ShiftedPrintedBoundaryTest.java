// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ShiftedPrintedBoundaryTest {
    static final int W = 500, H = 180;
    static final List<MeasureRegion> RAW =
            List.of(
                    new MeasureRegion(.04f, .296f, .22f, .82f),
                    new MeasureRegion(.304f, .96f, .22f, .82f));
    static final List<MeasureRegion> FIT =
            List.of(
                    new MeasureRegion(.04f, .496f, .22f, .82f),
                    new MeasureRegion(.504f, .96f, .22f, .82f));

    static byte[] page() {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 60; y <= 124; y += 16) rect(g, 20, y, 460, 1, 0);
        rect(g, 150, 60, 1, 65, 0);
        return g;
    }

    static void rect(byte[] g, int x, int y, int w, int h, int value) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) g[yy * W + xx] = (byte) value;
    }

    static List<MeasureRegion> run(byte[] g) {
        return PrintedMeasureRhythmGuard.preserveShiftedBoundary(RAW, FIT, g, W, H);
    }

    @Test
    public void restoresUnequalPrintedDivision() {
        assertEquals(RAW, run(page()));
    }

    @Test
    public void tallNoteStemCannotProveInferredBar() {
        var g = page();
        rect(g, 250, 35, 2, 90, 0);
        assertEquals(RAW, run(g));
    }

    @Test
    public void twoPrintedAlternativesRemainAmbiguous() {
        var g = page();
        rect(g, 250, 60, 1, 65, 0);
        assertEquals(FIT, run(g));
    }

    @Test
    public void brokenOriginalBarCannotMoveBoundary() {
        var g = page();
        rect(g, 150, 77, 1, 15, 255);
        assertEquals(FIT, run(g));
    }

    @Test
    public void originalStemCannotMoveBoundary() {
        var g = page();
        rect(g, 150, 35, 1, 26, 0);
        assertEquals(FIT, run(g));
    }

    @Test
    public void changedOuterSpanIsNotPairRelocation() {
        var different = List.of(new MeasureRegion(.06f, .496f, .22f, .82f), FIT.get(1));
        assertEquals(
                different,
                PrintedMeasureRhythmGuard.preserveShiftedBoundary(RAW, different, page(), W, H));
    }

    @Test
    public void overlappingExpandedRestBoxesStayUntouched() {
        var repeated = List.of(RAW.get(0), RAW.get(0));
        assertEquals(
                FIT,
                PrintedMeasureRhythmGuard.preserveShiftedBoundary(repeated, FIT, page(), W, H));
    }
}
