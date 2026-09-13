// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original geometry: a printed ledger has a dark core and lighter antialiased ends. */
public class SoftLedgerRuleTest {
    private RemoteLedgerEvidenceTest.Page page(boolean above, boolean complete, boolean core) {
        var p = new RemoteLedgerEvidenceTest.Page(above, false, complete, false, false);
        for (int x = 86; x <= 124; x++)
            if (x < 95 || x > 115 || !core) p.gray[p.y * RemoteLedgerEvidenceTest.W + x] = (byte) 175;
        return p;
    }

    @Test public void upperLedgerKeepsDarkHeadAndSoftRuleEnds() {
        var note = page(true, true, true).remote();
        assertNotNull(note); assertEquals(16, note.staffStep());
    }

    @Test public void lowerLedgerKeepsDarkHeadAndSoftRuleEnds() {
        var note = page(false, true, true).remote();
        assertNotNull(note); assertEquals(-8, note.staffStep());
    }

    @Test public void softOuterRuleDoesNotReplaceMissingInnerLedgers() {
        assertNull(page(true, false, true).remote());
        assertNull(page(false, false, true).remote());
    }

    @Test public void softRuleMustStillExtendBothSidesOfStemlessHead() {
        var p = page(true, true, true);
        for (int x = 86; x <= 124; x++) if (x > 115)
            p.gray[p.y * RemoteLedgerEvidenceTest.W + x] = (byte) 255;
        assertNull(p.remote());
    }

    @Test public void inputArraysArePreserved() {
        var p = page(true, true, true);
        byte[] gray = p.gray.clone(), labels = p.labels.clone();
        p.remote(); assertArrayEquals(gray, p.gray); assertArrayEquals(labels, p.labels);
    }

    private RemoteLedgerEvidenceTest.Page middle(boolean above, boolean both, boolean core) {
        var p = new RemoteLedgerEvidenceTest.Page(above, false, true, false, false);
        for (int rule = 1; rule <= (both ? 2 : 1); rule++) {
            int y = p.y + p.direction * rule * RemoteLedgerEvidenceTest.GAP;
            for (int x = 86; x <= 124; x++)
                p.gray[y * RemoteLedgerEvidenceTest.W + x] = (byte) (core && x >= 100 && x <= 109 ? 0 : 175);
        }
        return p;
    }

    @Test public void completeUpperSeriesSupportsOneFadedMiddleRule() {
        var n = middle(true, false, true).remote(); assertNotNull(n); assertEquals(16, n.staffStep());
    }

    @Test public void completeLowerSeriesSupportsOneFadedMiddleRule() {
        var n = middle(false, false, true).remote(); assertNotNull(n); assertEquals(-8, n.staffStep());
    }

    @Test public void twoWeakRulesCannotSupplyStrongLedgerSeries() {
        assertNull(middle(true, true, true).remote()); assertNull(middle(false, true, true).remote());
    }

    @Test public void strongNeighborCannotReplaceMissingDarkCore() {
        assertNull(middle(true, false, false).remote()); assertNull(middle(false, false, false).remote());
    }
}
