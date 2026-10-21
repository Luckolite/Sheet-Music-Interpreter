// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original triangle/curved-flag contacts with independently complete printed rules. */
public class ContiguousOppositeRuleFlagTest {
    int marks(boolean mirror, boolean continuation, boolean head, boolean secondRule) {
        int w = 640, h = 400;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int row : new int[] {100, 117, 134, 151, 168}) {
            if (row == 117 && !secondRule) continue;
            for (int x = 230; x <= 370; x++)
                for (int d = -1; d <= 1; d++)
                    ink(
                            g,
                            l,
                            w,
                            x,
                            mirror ? 200 - row - d : row + d,
                            OmrMeasurePostProcessor.STAFF);
        }
        for (int row = 0; row < 19; row++) {
            int span = 1 + (int) Math.round(9 * row / 18d);
            for (int x = 300 - span / 2; x < 300 - span / 2 + span; x++)
                ink(g, l, w, x, mirror ? 100 - row : 100 + row, OmrMeasurePostProcessor.SYMBOL);
        }
        if (continuation) {
            for (int y = 119; y <= 126; y++)
                for (int dx = -1; dx <= 1; dx++)
                    ink(
                            g,
                            l,
                            w,
                            300 - (y - 119) + dx,
                            mirror ? 200 - y : y,
                            OmrMeasurePostProcessor.STEM_OR_REST);
            for (int y = 75; y <= 126; y++)
                ink(g, l, w, 293, mirror ? 200 - y : y, OmrMeasurePostProcessor.STEM_OR_REST);
        }
        if (head)
            for (int y = 70; y <= 80; y++)
                for (int x = 292; x <= 307; x++)
                    ink(g, l, w, x, mirror ? 200 - y : y, OmrMeasurePostProcessor.NOTEHEAD);
        if (continuation) {
            assertEquals(0, g[(mirror ? 81 : 119) * w + 300] & 255);
            assertEquals(0, g[(mirror ? 82 : 118) * w + 300] & 255);
            assertEquals(0, g[(mirror ? 74 : 126) * w + 293] & 255);
        }
        var gc = g.clone();
        var lc = l.clone();
        int mark =
                NoteArticulationDetector.detect(
                                l,
                                g,
                                w,
                                h,
                                List.of(
                                        new NoteArticulationDetector.Anchor(
                                                300, mirror ? 125 : 75, 17, 0)))[0]
                        & NoteArticulation.STACCATISSIMO;
        assertArrayEquals(gc, g);
        assertArrayEquals(lc, l);
        return mark;
    }

    void ink(byte[] g, byte[] l, int w, int x, int y, byte kind) {
        g[y * w + x] = 0;
        l[y * w + x] = kind;
    }

    @Test
    public void lowerCurvedFlagAcrossTwoRulesIsNotWedge() {
        assertEquals(0, marks(false, true, true, true));
    }

    @Test
    public void upperCurvedFlagAcrossTwoRulesIsNotWedge() {
        assertEquals(0, marks(true, true, true, true));
    }

    @Test
    public void lowerCompleteWedgeMayHaveBroadFaceOnSecondRule() {
        assertEquals(16, marks(false, false, false, true));
    }

    @Test
    public void upperCompleteWedgeMayHaveBroadFaceOnSecondRule() {
        assertEquals(16, marks(true, false, false, true));
    }

    @Test
    public void lowerSingleTipContactRemainsWedge() {
        assertEquals(16, marks(false, false, false, false));
    }

    @Test
    public void upperSingleTipContactRemainsWedge() {
        assertEquals(16, marks(true, false, false, false));
    }
}
