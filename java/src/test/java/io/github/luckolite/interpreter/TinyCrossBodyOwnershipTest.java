// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Authored complete crosses with tiny semantic corners, oversized semantic dots and oval controls. */
public final class TinyCrossBodyOwnershipTest {
    static class Scene extends UnpitchedCrossHeadTest.Page {
        void tiny(int x, int y, boolean dot) {
            cross(x, y, false);
            for (int yy = y - 8; yy <= y + 8; yy++)
                for (int xx = x - 12; xx <= x + 12; xx++)
                    if (a[yy * w + xx] == 2) a[yy * w + xx] = 5;
            for (int yy = y + 2; yy <= y + 5; yy++)
                for (int xx = x + 6; xx <= x + 9; xx++)
                    if ((g[yy * w + xx] & 255) < 165) a[yy * w + xx] = 2;
            if (dot)
                for (int yy = -3; yy <= 3; yy++)
                    for (int xx = -3; xx <= 3; xx++)
                        if (xx * xx + yy * yy <= 10) rect(x + 25 + xx, y - 8 + yy, 1, 1, 2);
        }
    }

    @Test
    public void tinyCornerNeedsWholeCrossAdmission() {
        Scene s = new Scene();
        s.tiny(120, 148, false);
        var n = s.notes();
        assertEquals(1, n.size());
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, n.get(0).kind());
        assertEquals(120 / 420f, n.get(0).positionInMeasure() * .92f + .04f, .00001f);
    }

    @Test
    public void wholeCrossOwnsSemanticDotLargerThanCorner() {
        Scene s = new Scene();
        s.tiny(120, 148, true);
        var n = s.notes();
        assertEquals(1, n.size());
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, n.get(0).kind());
        assertEquals(1, n.get(0).augmentationDots());
    }

    @Test
    public void twoTinyCrossesRemainIndependentDottedAttacks() {
        Scene s = new Scene();
        s.tiny(100, 148, true);
        s.tiny(260, 148, true);
        var n = s.notes();
        assertEquals(2, n.size());
        for (var x : n) {
            assertEquals(ScoreNoteEvent.Kind.UNPITCHED, x.kind());
            assertEquals(1, x.augmentationDots());
            assertEquals(1f, x.unbeamedDurationBeats(), 0);
        }
        assertTrue(n.get(0).positionInMeasure() < n.get(1).positionInMeasure());
    }

    @Test
    public void separateRealOvalIsPreservedExactly() {
        Scene a = new Scene();
        a.oval(260, 156, false, 11, 7);
        var expected = a.notes().get(0);
        Scene s = new Scene();
        s.tiny(120, 148, true);
        s.oval(260, 156, false, 11, 7);
        var n = s.notes();
        assertEquals(2, n.size());
        assertEquals(expected, n.get(1));
    }

    @Test
    public void sourceArraysRemainUnchanged() {
        Scene s = new Scene();
        s.tiny(120, 148, true);
        byte[] g = s.g.clone(), a = s.a.clone();
        s.notes();
        assertArrayEquals(g, s.g);
        assertArrayEquals(a, s.a);
    }
}
