// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Retained positives and the exact preexisting abstention for a slur crossing both heads. */
public class ConnectedSystemSlurBoundaryTest {
    @Test
    public void twoMainBowsWithoutAnOppositeSlurRemainProved() throws Exception {
        assertTrue(PairedSystemTieOwnershipTest.proof("bow", "bow"));
    }

    @Test
    public void distinctOppositeSlursBeyondBothFreeTipsPreserveTheTie() throws Exception {
        assertTrue(PairedSystemTieOwnershipTest.proof("oppositeSlur", "oppositeSlur"));
    }

    @Test
    public void aHeadCrossingSlurRemainsAnExistingAmbiguousAbstention() throws Exception {
        var r =
                PairedSystemTieOwnershipTest.draw(
                        "oppositeTouchingSlur", "oppositeTouchingSlur", true, false);
        for (int row = 0; row < 2; row++) {
            var track = row == 0 ? r.first() : r.last();
            int x = row == 0 ? PairedSystemTieOwnershipTest.AX : PairedSystemTieOwnershipTest.BX;
            int y = Math.round(PairedSystemTieOwnershipTest.pitch(track, x, r.printedStep()));
            assertEquals(
                    "Actual head at independently drawn slur crossing",
                    OmrMeasurePostProcessor.NOTEHEAD,
                    r.labels()[y * PairedSystemTieOwnershipTest.W + x]);
        }
        assertFalse(PairedSystemTieOwnershipTest.detect(r, false, false));
    }
}
