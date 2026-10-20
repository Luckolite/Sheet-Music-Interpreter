// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original hollow heads, independent shafts, and an opposite-end flag countercase. */
public class UnbeamedHollowRecoveryTest {
    static final int W = 520, H = 320;
    final byte[] gray = new byte[W * H], labels = new byte[W * H];
    static final List<MeasureRegion> M = List.of(new MeasureRegion(.04f, .96f, .2f, .85f));

    public UnbeamedHollowRecoveryTest() {
        Arrays.fill(gray, (byte) 255);
        for (int y = 120; y <= 200; y += 20) for (int x = 20; x < 500; x++) ink(x, y, 30, 4);
    }

    void ink(int x, int y, int tone, int label) {
        gray[y * W + x] = (byte) tone;
        labels[y * W + x] = (byte) label;
    }

    void oval(int cx, int cy, boolean open, boolean labelled) {
        for (int dy = -15; dy <= 15; dy++)
            for (int dx = -16; dx <= 16; dx++) {
                double q = dx * dx / (13.8 * 13.8) + (dy + .4 * dx) * (dy + .4 * dx) / 64;
                if (q <= 1.25 && (!open || q >= .48)) ink(cx + dx, cy + dy, 45, 5);
                if (labelled && q <= 1.15) labels[(cy + dy) * W + cx + dx] = 2;
            }
    }

    void shaft(int x, int from, int to) {
        for (int y = from; y <= to; y++) for (int dx = 0; dx <= 1; dx++) ink(x + dx, y, 25, 1);
    }

    List<ScoreNoteEvent> notes() {
        return OmrScoreInterpreter.analyze(labels, gray, W, H, M).notes();
    }

    List<ScoreNoteEvent> near(List<ScoreNoteEvent> n, float cy) {
        return n.stream().filter(x -> Math.abs(x.pageY() * H - cy) < 9).toList();
    }

    @Test
    public void unlabelledHollowWithDownShaftEntersAsHalf() {
        oval(250, 134, true, false);
        shaft(236, 132, 194);
        var n = near(notes(), 134);
        assertEquals(1, n.size());
        assertEquals(2, n.get(0).unbeamedDurationBeats(), .001);
        assertEquals(0, n.get(0).beamCount());
    }

    @Test
    public void unlabelledHollowWithUpShaftEntersAsHalf() {
        oval(250, 194, true, false);
        shaft(264, 134, 196);
        var n = near(notes(), 194);
        assertEquals(1, n.size());
        assertEquals(2, n.get(0).unbeamedDurationBeats(), .001);
    }

    @Test
    public void existingLabelledHalfIsNotDuplicated() {
        oval(250, 134, true, true);
        shaft(236, 132, 194);
        var n = near(notes(), 134);
        assertEquals(1, n.size());
        assertEquals(2, n.get(0).unbeamedDurationBeats(), .001);
    }

    @Test
    public void genuineFilledQuarterIsNotMadeHollow() {
        oval(250, 194, false, true);
        shaft(264, 134, 196);
        var n = near(notes(), 194);
        assertEquals(1, n.size());
        assertEquals(1, n.get(0).unbeamedDurationBeats(), .001);
    }

    @Test
    public void oppositeEndFlagLoopCannotAddSecondHead() {
        oval(222, 194, false, true);
        shaft(236, 132, 196);
        oval(250, 134, true, false);
        var n = notes();
        assertEquals(1, n.size());
        assertEquals(1, near(n, 194).size());
        assertTrue(near(n, 134).isEmpty());
    }

    @Test
    public void hollowCounterWithoutShaftIsNotAdmitted() {
        oval(250, 134, true, false);
        assertTrue(near(notes(), 134).isEmpty());
    }

    @Test
    public void filledUnlabelledStrokeDoesNotUseHollowRoute() {
        oval(250, 134, false, false);
        shaft(236, 132, 194);
        assertTrue(near(notes(), 134).isEmpty());
    }

    @Test
    public void inputArraysStayReadOnly() {
        oval(250, 134, true, false);
        shaft(236, 132, 194);
        var g = gray.clone();
        var l = labels.clone();
        notes();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }
}
