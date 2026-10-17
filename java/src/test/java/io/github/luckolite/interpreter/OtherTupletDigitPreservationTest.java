// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original licensed-font phase/scale negatives; complements the existing 2/5/8 controls. */
public class OtherTupletDigitPreservationTest {
    private void reject(int digit) throws Exception {
        for (int size = 14; size <= 30; size++)
            for (float phase : new float[] {0, .25f, .5f, .75f})
                for (float shear : new float[] {0, -.15f, .15f})
                    for (float width : new float[] {.8f, 1, 1.2f})
                        assertFalse(
                                "digit" + digit + " size" + size + " phase" + phase,
                                CurledTupletThreeTest.match(
                                        CurledTupletThreeTest.glyph(
                                                digit, size, phase, shear, width)));
    }

    @Test
    public void zeroNeverSuppliesTwoOpenBowls() throws Exception {
        reject(0);
    }

    @Test
    public void oneNeverSuppliesTwoCurvedBowls() throws Exception {
        reject(1);
    }

    @Test
    public void newCurledFourAliasIsNotAThree() throws Exception {
        assertFalse(
                CurledTupletThreeTest.match(CurledTupletThreeTest.glyph(4, 14, .25f, -.15f, .8f)));
    }

    @Test
    public void closedLowerBowlOfSixRemainsRejected() throws Exception {
        reject(6);
    }

    @Test
    public void sevenNeverSuppliesLowerBowl() throws Exception {
        reject(7);
    }

    @Test
    public void closedUpperBowlOfNineRemainsRejected() throws Exception {
        reject(9);
    }
}
