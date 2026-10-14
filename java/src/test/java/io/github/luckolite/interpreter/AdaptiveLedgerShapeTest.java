// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original faded ovals with either thin ledger rules or broad illustration bands. */
public class AdaptiveLedgerShapeTest {
    private RemoteLedgerEvidenceTest.Page page(boolean above, int ink, int paper, boolean broad) {
        var p = new RemoteLedgerEvidenceTest.Page(above, false, true, false, true);
        for (int i = 0; i < p.gray.length; i++)
            p.gray[i] = (byte) ((p.gray[i] & 255) == 255 ? paper : ink);
        if (broad)
            for (int n = 0; n < 4; n++)
                for (int dy = -6; dy <= 6; dy++)
                    for (int x = 84; x <= 126; x++)
                        p.gray[(p.y + p.direction * n * 16 + dy) * RemoteLedgerEvidenceTest.W + x] =
                                (byte) ink;
        return p;
    }

    @Test
    public void broadFadedBandsCannotSupplyLedgerChain() {
        for (boolean above : new boolean[] {false, true})
            for (int ink : new int[] {110, 140, 170})
                for (int paper : new int[] {230, 240, 250}) {
                    var p = page(above, ink, paper, true);
                    assertNull("above=" + above + " ink=" + ink + " paper=" + paper, p.remote());
                }
    }

    @Test
    public void thinFadedLedgerWholeNotesRetainPitchAndInput() {
        for (boolean above : new boolean[] {false, true})
            for (int ink : new int[] {110, 140, 170})
                for (int paper : new int[] {230, 240, 250}) {
                    var p = page(above, ink, paper, false);
                    var a = p.labels.clone();
                    var b = p.gray.clone();
                    var n = p.remote();
                    assertNotNull(n);
                    assertEquals(above ? 16 : -8, n.staffStep());
                    assertArrayEquals(a, p.labels);
                    assertArrayEquals(b, p.gray);
                }
    }
}
