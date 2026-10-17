// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original disconnected rectangles exercising proposal boundaries, without score pixels. */
public class TouchingDynamicProposalGuardsTest {
    static final int W = 160, H = 180;

    private static void rect(byte[] g, int l, int t, int r, int b) {
        for (int y = t; y < b; y++) for (int x = l; x < r; x++) g[y * W + x] = 20;
    }

    private List<PlayingTechniqueDetector.Word> boxes(
            int ah, int bh, int dx, List<PlayingTechniqueDetector.Staff> staffs) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        // The extents overlap, while their nearest pixels remain several columns apart.
        rect(g, 40, 80, 43, 80 + ah);
        rect(g, 30, 80, 43, 83);
        rect(g, 24 + dx, 80 + ah, 27 + dx, 80 + ah + bh);
        rect(g, 24 + dx, 77 + ah + bh, 37 + dx, 80 + ah + bh);
        byte[] before = g.clone();
        var result = ScoreDynamicsDetector.symbolBoxes(g, staffs, W, H);
        assertArrayEquals(before, g);
        return result;
    }

    private static List<PlayingTechniqueDetector.Staff> staff() {
        return List.of(new PlayingTechniqueDetector.Staff(20, 60, 10, 0, 1));
    }

    @Test
    public void excessiveCombinedHeightCannotAddUnion() {
        assertEquals(2, boxes(17, 17, 0, staff()).size());
    }

    @Test
    public void undersizedFragmentCannotSupplyCompleteBody() {
        assertEquals(1, boxes(5, 15, 0, staff()).size());
    }

    @Test
    public void horizontalEdgeContactCannotSupplyUnion() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        rect(g, 40, 80, 43, 94);
        rect(g, 30, 80, 43, 83);
        rect(g, 53, 94, 56, 109);
        rect(g, 43, 106, 56, 109);
        assertEquals(2, ScoreDynamicsDetector.symbolBoxes(g, staff(), W, H).size());
    }

    @Test
    public void fragmentsOwnedByDifferentStaffsCannotJoin() {
        var staffs =
                List.of(
                        new PlayingTechniqueDetector.Staff(30, 70, 10, 0, 1),
                        new PlayingTechniqueDetector.Staff(120, 160, 10, 0, 1));
        assertEquals(2, boxes(14, 15, 0, staffs).size());
    }
}
