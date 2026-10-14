// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original padded five-rule staff and bar/stem counterexamples. */
public class PaddedStaffBarlineTest {
    final int W = 600, H = 260;
    byte[] gray = new byte[W * H];

    public PaddedStaffBarlineTest() {
        Arrays.fill(gray, (byte) 255);
        for (int y = 100; y <= 140; y += 10) box(30, y, 540, 1);
    }

    void box(int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) gray[yy * W + xx] = 0;
    }

    List<MeasureRegion> raw =
            List.of(
                    new MeasureRegion(.05f, .495f, 60f / 260, 180f / 260),
                    new MeasureRegion(.505f, .95f, 60f / 260, 180f / 260));
    List<MeasureRegion> fitted = List.of(new MeasureRegion(.05f, .95f, 60f / 260, 180f / 260));

    List<MeasureRegion> read() {
        return PrintedMeasureRhythmGuard.preservePrintedBoundaries(raw, fitted, gray, W, H);
    }

    @Test
    public void paddedSingleStaffKeepsPrintedBar() {
        box(300, 100, 2, 41);
        assertEquals(raw, read());
    }

    @Test
    public void unprintedBoundaryRemainsMerged() {
        assertEquals(fitted, read());
    }

    @Test
    public void shortStemCannotSupplyStaffHeight() {
        box(300, 108, 2, 33);
        assertEquals(fitted, read());
    }

    @Test
    public void overlongStemDoesNotBecomeBar() {
        box(300, 94, 2, 47);
        assertEquals(fitted, read());
    }

    @Test
    public void headAtStaffEdgeDoesNotBecomeBar() {
        box(300, 100, 2, 41);
        box(296, 137, 10, 7);
        assertEquals(fitted, read());
    }

    @Test
    public void lineWithoutPrintedStaffDoesNotQualify() {
        Arrays.fill(gray, (byte) 255);
        box(300, 100, 2, 41);
        assertEquals(fitted, read());
    }
}
