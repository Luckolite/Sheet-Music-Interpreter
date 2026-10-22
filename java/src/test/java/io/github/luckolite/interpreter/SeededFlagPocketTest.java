// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original semantic-seed controls, separate from faded-head recovery. */
public class SeededFlagPocketTest {
    private boolean retained(
            boolean other, boolean broken, boolean second, boolean wide, boolean inverted) {
        int w = 220, h = 180, cx = 92, cy = 104, otherY = second ? 97 : 78;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int rail : new int[] {60, 74, 88, 102, 116})
            for (int x = 20; x < 200; x++) {
                g[rail * w + x] = 35;
                l[rail * w + x] = 4;
            }
        oval(g, l, w, cx, cy, wide ? 10 : 4, wide ? 6 : 4);
        if (other) oval(g, l, w, 110, otherY, 9, 6);
        for (int y = otherY; y <= 126; y++) {
            if (broken && y >= 89 && y <= 96) continue;
            g[y * w + 100] = 35;
            l[y * w + 100] = 1;
        }
        if (inverted) {
            byte[] gg = g.clone(), ll = l.clone();
            for (int i = 0; i < g.length; i++) {
                g[i] = gg[g.length - 1 - i];
                l[i] = ll[l.length - 1 - i];
            }
            cy = h - 1 - cy;
            otherY = h - 1 - otherY;
        }
        byte[] originalG = g.clone(), originalL = l.clone();
        var notes =
                OmrScoreInterpreter.analyze(
                                l, g, w, h, List.of(new MeasureRegion(.08f, .92f, .28f, .75f)))
                        .notes();
        assertArrayEquals(originalG, g);
        assertArrayEquals(originalL, l);
        if (other) {
            float expected = otherY / (float) h;
            assertTrue(
                    "The independent recognized head must remain",
                    notes.stream().anyMatch(n -> Math.abs(n.pageY() - expected) < .025f));
        }
        float expected = cy / (float) h;
        return notes.stream().anyMatch(n -> Math.abs(n.pageY() - expected) < .025f);
    }

    private static void oval(byte[] g, byte[] l, int w, int cx, int cy, int rx, int ry) {
        for (int y = cy - ry; y <= cy + ry; y++)
            for (int x = cx - rx; x <= cx + rx; x++)
                if ((x - cx) * (x - cx) / (double) (rx * rx)
                                + (y - cy) * (y - cy) / (double) (ry * ry)
                        <= 1) {
                    g[y * w + x] = 35;
                    l[y * w + x] = 2;
                }
    }

    @Test
    public void semanticSeedCannotReintroduceTheOwnedFlagPocket() {
        assertFalse(retained(true, false, false, false, false));
    }

    @Test
    public void invertedSemanticSeedUsesTheSameOwnershipProof() {
        assertFalse(retained(true, false, false, false, true));
    }

    @Test
    public void noIndependentHeadLeavesTheSmallHeadAvailable() {
        assertTrue(retained(false, false, false, false, false));
    }

    @Test
    public void disconnectedShaftDoesNotEstablishOwnership() {
        assertTrue(retained(true, true, false, false, false));
    }

    @Test
    public void aRealOppositeSideSecondRemainsAvailable() {
        assertTrue(retained(true, false, true, false, false));
    }

    @Test
    public void aFullSizeRecognizedHeadIsPreserved() {
        assertTrue(retained(true, false, false, true, false));
    }

    private void independentGrace(boolean inverted) {
        int w = 220, h = 180;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int rail : new int[] {60, 74, 88, 102, 116})
            for (int x = 20; x < 200; x++) {
                g[rail * w + x] = 35;
                l[rail * w + x] = 4;
            }
        oval(g, l, w, 92, 104, 4, 4);
        oval(g, l, w, 116, 78, 9, 6);
        for (int y = 70; y <= 104; y++) {
            g[y * w + 96] = 35;
            l[y * w + 96] = 1;
        }
        for (int y = 40; y <= 78; y++) {
            g[y * w + 125] = 35;
            l[y * w + 125] = 1;
        }
        if (inverted) {
            byte[] gg = g.clone(), ll = l.clone();
            for (int i = 0; i < g.length; i++) {
                g[i] = gg[g.length - 1 - i];
                l[i] = ll[l.length - 1 - i];
            }
        }
        byte[] beforeG = g.clone(), beforeL = l.clone();
        var notes =
                OmrScoreInterpreter.analyze(
                                l, g, w, h, List.of(new MeasureRegion(.08f, .92f, .15f, .8f)))
                        .notes();
        float small = (inverted ? h - 1 - 104 : 104) / (float) h,
                other = (inverted ? h - 1 - 78 : 78) / (float) h;
        assertTrue(
                "The small note has its own complete shaft",
                notes.stream().anyMatch(n -> Math.abs(n.pageY() - small) < .02f));
        assertTrue(
                "The unrelated neighboring note keeps its different shaft",
                notes.stream().anyMatch(n -> Math.abs(n.pageY() - other) < .025f));
        assertArrayEquals(beforeG, g);
        assertArrayEquals(beforeL, l);
    }

    @Test
    public void anUnjoinedNeighborCannotOwnTheGraceShaft() {
        independentGrace(false);
    }

    @Test
    public void invertedUnjoinedNeighborCannotOwnTheGraceShaft() {
        independentGrace(true);
    }
}
