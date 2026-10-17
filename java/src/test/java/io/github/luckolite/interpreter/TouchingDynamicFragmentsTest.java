// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original disconnected hook/stem shapes; proposals still need independent glyph recognition. */
public class TouchingDynamicFragmentsTest {
    static final int W = 120, H = 150;

    private static void ink(byte[] g, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) g[y * W + x] = 30;
    }

    private static List<PlayingTechniqueDetector.Word> boxes(int dy, int dx) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        ink(g, 42, 72, 45, 86);
        ink(g, 30, 72, 45, 75);
        ink(g, 24 + dx, 86 + dy, 27 + dx, 101 + dy);
        ink(g, 24 + dx, 98 + dy, 38 + dx, 101 + dy);
        return ScoreDynamicsDetector.symbolBoxes(
                g, List.of(new PlayingTechniqueDetector.Staff(20, 60, 10, 0, 1)), W, H);
    }

    @Test
    public void touchingRowsRetainCompleteFragmentedBody() {
        var b = boxes(0, 0);
        assertEquals(3, b.size());
        assertTrue(
                b.stream()
                        .anyMatch(
                                x ->
                                        Math.abs(x.left() - 24f / W) < .00001
                                                && Math.abs(x.top() - 72f / H) < .00001
                                                && Math.abs(x.right() - 45f / W) < .00001
                                                && Math.abs(x.bottom() - 101f / H) < .00001));
    }

    @Test
    public void overlappingFragmentsRemainGrouped() {
        assertEquals(1, boxes(-1, 0).size());
    }

    @Test
    public void detachedVerticalMarkRemainsSeparate() {
        assertEquals(2, boxes(3, 0).size());
    }

    @Test
    public void distantHorizontalMarkRemainsSeparate() {
        assertEquals(2, boxes(0, 35).size());
    }

    @Test
    public void verticalGapDoesNotCreateIntermediateInk() {
        assertEquals(2, boxes(1, 0).size());
    }
}
