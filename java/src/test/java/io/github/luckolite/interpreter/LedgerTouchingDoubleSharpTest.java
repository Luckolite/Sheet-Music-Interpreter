// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original diagonal cross whose accidental mask also contains a short ledger extension. */
public class LedgerTouchingDoubleSharpTest extends TouchingChordAccidentalTest {
    void drawing(boolean incomplete) {
        head(237, 136);
        for (int y = 0; y < 18; y++)
            for (int x = 0; x < 18; x++)
                if ((Math.abs(x - y) <= 4 || Math.abs(x + y - 17) <= 4) && !(incomplete && y > 11))
                    rect(204 + x, 127 + y, 204 + x, 127 + y, y >= 7 && y <= 9 && x < 8 ? 1 : 5);
        rect(220, 143, 233, 144, 5);
    }

    @Test
    public void fullCrossSurvivesLedgerExtendingIntoHeadColumn() {
        drawing(false);
        var n = notes();
        assertEquals(1, n.size());
        assertEquals(ScoreNoteEvent.ACCIDENTAL_DOUBLE_SHARP, accidental(n, 136));
    }

    @Test
    public void ledgerCannotSupplyMissingCrossArms() {
        drawing(true);
        assertNotEquals(ScoreNoteEvent.ACCIDENTAL_DOUBLE_SHARP, accidental(notes(), 136));
    }

    @Test
    public void ledgerRecoveryPreservesSourceArrays() {
        drawing(false);
        var l = labels.clone();
        var g = gray.clone();
        notes();
        assertArrayEquals(l, labels);
        assertArrayEquals(g, gray);
    }
}
