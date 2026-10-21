// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original integer strokes; direct masks test a conditional contract, not rule provenance. */
public class RuleJoinedOpenOriginalTest {
    private static final int WIDTH = 96, LEFT = 20, TOP = 20, RIGHT = 40, BOTTOM = 44;
    private static final int RULES_TOP = 16, RULES_LENGTH = 33;

    // Five literal sites per column; two straight arms meet at transverse column ten.
    // u is upward distance from BOTTOM. No scan, font, or private glyph is used.
    private static int[] caret(boolean eraseRuleRows, boolean extraGap, int emptyColumn) {
        int[] out = new int[105];
        int n = 0;
        for (int t = 0; t <= 20; t++) {
            if (t == emptyColumn) continue;
            int low = 2 * Math.min(t, 20 - t);
            for (int u = low; u <= low + 4; u++) {
                if (eraseRuleRows && (u == 10 || u == 11)) continue;
                if (extraGap && t == 10 && (u == 21 || u == 22)) continue;
                out[n++] = (BOTTOM - u) * WIDTH + LEFT + t;
            }
        }
        return Arrays.copyOf(out, n);
    }

    private static boolean[] rules(int... absoluteRows) {
        boolean[] out = new boolean[RULES_LENGTH];
        for (int y : absoluteRows) out[y - RULES_TOP] = true;
        return out;
    }

    private static boolean joined(int[] ink, boolean[] mask, int maskTop) {
        return RoundedAngularArticulation.matchesRuleJoinedOpen(
                ink, WIDTH, LEFT, TOP, RIGHT, BOTTOM, mask, maskTop);
    }

    @Test
    public void intactOriginalRemainsAnOpenCaret() {
        int[] ink = caret(false, false, -1);
        assertEquals(105, ink.length);
        assertTrue(RoundedAngularArticulation.matchesOpen(ink, WIDTH, LEFT, TOP, RIGHT, BOTTOM, 1));
        assertTrue(joined(ink, rules(), RULES_TOP));
    }

    @Test
    public void bothProvedErasedRowsAccountForOnlyTheirMissingSupport() {
        int[] ink = caret(true, false, -1);
        // At t=4 the observed u values are 8,9,12: old support3/span5 fails.
        // Removing proved rows u=10,11 from the denominator gives support3/required3.
        assertFalse(
                RoundedAngularArticulation.matchesOpen(ink, WIDTH, LEFT, TOP, RIGHT, BOTTOM, 1));
        assertTrue(joined(ink, rules(33, 34), RULES_TOP));
    }

    @Test
    public void partialOrAbsentRuleProofCannotExplainTheGap() {
        int[] ink = caret(true, false, -1);
        assertFalse(joined(ink, rules(), RULES_TOP));
        assertFalse(joined(ink, rules(33), RULES_TOP));
        assertFalse(joined(ink, rules(34), RULES_TOP));
    }

    @Test
    public void aSecondUnprovedGapRemainsDisconnected() {
        // At t=10, u=20,23,24 remain; neither missing u=21,22 is a proved row.
        assertFalse(joined(caret(true, true, -1), rules(33, 34), RULES_TOP));
    }

    @Test
    public void erasedRowsNeverSupplyAnEntireMissingColumn() {
        assertFalse(joined(caret(true, false, 7), rules(33, 34), RULES_TOP));
    }

    @Test
    public void selectedInkOnADeclaredErasedRowIsInconsistent() {
        assertFalse(joined(caret(false, false, -1), rules(33, 34), RULES_TOP));
    }

    @Test
    public void ruleMaskMustCoverTheWholeDeclaredVerticalDomain() {
        int[] ink = caret(true, false, -1);
        assertFalse(joined(ink, null, RULES_TOP));
        assertFalse(joined(ink, new boolean[0], RULES_TOP));
        assertFalse(joined(ink, new boolean[BOTTOM - RULES_TOP], RULES_TOP));
        assertFalse(joined(ink, new boolean[40], TOP + 1));
    }
}
