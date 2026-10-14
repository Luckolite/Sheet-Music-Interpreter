// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class WideRestBarlineTest {
    void shaft(byte[] g, int x, int top, int bottom) {
        for (int y = top; y <= bottom; y++)
            for (int dx = 0; dx < 3; dx++) g[y * WideSixteenthContourTest.W + x + dx] = 0;
    }

    boolean spanning(byte[] g) {
        return SixteenthRestDetector.staffSpanningShaft(g, 400, 240, 170, 208, 80, 144);
    }

    @Test
    public void fullStaffShaftIsProved() {
        var g = WideSixteenthContourTest.page(1.8);
        shaft(g, 172, 80, 144);
        assertTrue(spanning(g));
    }

    @Test
    public void broadRestAloneHasNoVerticalStaffShaft() {
        assertFalse(spanning(WideSixteenthContourTest.page(1.8)));
    }

    @Test
    public void partialShaftDoesNotProveBarline() {
        var g = WideSixteenthContourTest.page(1.8);
        shaft(g, 172, 100, 144);
        assertFalse(spanning(g));
    }

    @Test
    public void disconnectedVerticalSegmentsDoNotProveBarline() {
        var g = WideSixteenthContourTest.page(1.8);
        shaft(g, 172, 80, 105);
        shaft(g, 172, 113, 144);
        assertFalse(spanning(g));
    }

    @Test
    public void genuineRestSurvivesTouchingBarlineThroughComponentRecovery() {
        var g = WideSixteenthContourTest.page(1.8);
        shaft(g, 172, 80, 144);
        var r = WideSixteenthContourTest.detect(g);
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.25, r.get(0).durationBeats(), 0);
    }

    @Test
    public void separateAdjacentBarlinePreservesRealRest() {
        var g = WideSixteenthContourTest.page(1.8);
        shaft(g, 163, 80, 144);
        var r = WideSixteenthContourTest.detect(g);
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.25, r.get(0).durationBeats(), 0);
    }

    @Test
    public void distantBarlinePreservesRealRest() {
        var g = WideSixteenthContourTest.page(1.8);
        shaft(g, 80, 80, 144);
        var r = WideSixteenthContourTest.detect(g);
        assertEquals(r.toString(), 1, r.size());
        assertEquals(.25, r.get(0).durationBeats(), 0);
    }

    @Test
    public void sourceUnmodified() {
        var g = WideSixteenthContourTest.page(1.8);
        shaft(g, 172, 80, 144);
        var before = g.clone();
        spanning(g);
        assertArrayEquals(before, g);
    }

    @Test
    public void roundedBottomOutsideImageRejected() {
        assertFalse(
                SixteenthRestDetector.staffSpanningShaft(
                        new byte[400 * 240], 400, 240, 170, 208, 80, 239.9f));
    }
}
