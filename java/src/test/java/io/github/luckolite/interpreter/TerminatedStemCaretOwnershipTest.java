// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original asymmetric angular strokes, a quarter-note shaft and independently long staff ink. */
public class TerminatedStemCaretOwnershipTest {
    static final int W = 600, H = 400;
    final byte[] g = new byte[W * H], l = new byte[W * H];
    final NoteArticulationDetector.Anchor n = new NoteArticulationDetector.Anchor(300, 250, 16, 0);

    void r(int x0, int x1, int y0, int y1, int v) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) g[y * W + x] = (byte) v;
    }

    void darkest(int a, int b, int c, int d, int value) {
        for (int y = c; y <= d; y++)
            for (int x = a; x <= b; x++) g[y * W + x] = (byte) Math.min(g[y * W + x] & 255, value);
    }

    void scene() {
        Arrays.fill(g, (byte) 250);
        for (int d = 0; d <= 18; d++) {
            int o = Math.round(d * 7 / 18f);
            r(300 - o, 301 - o, 170 + d, 170 + d, 35);
            r(299 + o, 303 + o, 170 + d, 170 + d, 35);
        }
        r(210, 390, 180, 181, 35);
        r(309, 310, 199, 250, 35);
        for (int y = 244; y <= 256; y++)
            for (int x = 290; x <= 310; x++)
                if ((x - 300) * (x - 300) / 100.0 + (y - 250) * (y - 250) / 36.0 <= 1) {
                    g[y * W + x] = 35;
                    l[y * W + x] = 2;
                }
    }

    int detect() {
        return NoteArticulationDetector.detect(l, g, W, H, List.of(n))[0];
    }

    @Test
    public void completeUnbeamedQuarterOwnsCrossedCaret() {
        scene();
        assertEquals(NoteArticulation.MARCATO, detect());
    }

    @Test
    public void crossedPeakAndInteriorPreserveTheSameAngularMark() {
        scene();
        r(210, 390, 169, 170, 35);
        assertEquals(NoteArticulation.MARCATO, detect());
    }

    @Test
    public void actualLetterCrossbarIsNotAnAngularMark() {
        scene();
        r(294, 309, 183, 184, 35);
        assertEquals(0, detect());
    }

    @Test
    public void faintCrossbar170RemainsText() {
        scene();
        r(294, 309, 183, 184, 170);
        assertEquals(0, detect());
    }

    @Test
    public void faintCrossbar195RemainsText() {
        scene();
        r(294, 309, 183, 184, 195);
        assertEquals(0, detect());
    }

    @Test
    public void faintCrossbar220RemainsText() {
        scene();
        r(294, 309, 183, 184, 220);
        assertEquals(0, detect());
    }

    @Test
    public void brokenShaftDoesNotOwnDistantCaret() {
        scene();
        r(309, 310, 220, 225, 250);
        assertEquals(0, detect());
    }

    @Test
    public void broadNonRuleAttachmentIsNotAThinShaft() {
        scene();
        r(303, 317, 210, 215, 35);
        assertEquals(0, detect());
    }

    @Test
    public void shaftWithoutObservedHeadConnectionCannotBorrowAnchor() {
        scene();
        r(290, 307, 244, 256, 250);
        assertEquals(0, detect());
    }

    @Test
    public void shaftContinuingIntoMarkDoesNotHaveASealedEndpoint() {
        scene();
        r(309, 310, 175, 198, 35);
        assertEquals(0, detect());
    }

    @Test
    public void displacedIndependentShaftCannotSupplyOwnership() {
        scene();
        r(309, 310, 199, 250, 250);
        r(330, 331, 199, 250, 35);
        assertEquals(0, detect());
    }

    @Test
    public void ambiguousOpposedStemIsNotUniqueOwnership() {
        scene();
        r(290, 291, 250, 296, 35);
        assertEquals(0, detect());
    }

    @Test
    public void completeThinLedgerCrossingDoesNotEraseTheOwnShaft() {
        scene();
        r(284, 316, 233, 234, 35);
        assertEquals(NoteArticulation.MARCATO, detect());
    }

    @Test
    public void physicallyCompositedAntialiasedLedgerHasObservedPaper() {
        scene();
        darkest(284, 316, 233, 233, 35);
        darkest(284, 316, 234, 234, 120);
        darkest(284, 316, 235, 235, 210);
        assertEquals(NoteArticulation.MARCATO, detect());
    }

    @Test
    public void incompleteSideDashCannotMasqueradeAsLedgerCrossing() {
        scene();
        r(300, 310, 233, 234, 35);
        assertEquals(0, detect());
    }

    @Test
    public void pixelsAndLabelsRemainUnchanged() {
        scene();
        byte[] a = g.clone(), b = l.clone();
        detect();
        assertArrayEquals(a, g);
        assertArrayEquals(b, l);
    }
}
