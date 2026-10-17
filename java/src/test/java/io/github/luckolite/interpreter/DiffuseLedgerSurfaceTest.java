// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** New rejection proof must abstain even when an older acquisition stage cannot retain a body. */
public class DiffuseLedgerSurfaceTest {
    boolean rejects(DiffuseLedgerHeadOwnershipTest t) {
        return DiffuseLedgerSurface.matches(t.gray, 500, 300, 194, 208, 206, 216, 212, 16);
    }

    @Test
    public void paleSoftFilledBodyIsNotBackgroundWhenOneLedgerSideIsHidden() {
        var t = new DiffuseLedgerHeadOwnershipTest();
        t.softLedgerBody(false);
        for (int x = 182; x < 194; x++) t.gray[212 * 500 + x] = (byte) 160;
        assertFalse(rejects(t));
    }

    @Test
    public void paleSoftHollowBodyIsNotBackgroundWhenOneLedgerSideIsHidden() {
        var t = new DiffuseLedgerHeadOwnershipTest();
        t.softLedgerBody(true);
        for (int x = 182; x < 194; x++) t.gray[212 * 500 + x] = (byte) 160;
        assertFalse(rejects(t));
    }

    @Test
    public void thinLedgerRemainsIndependentNotationEvidence() {
        var t = new DiffuseLedgerHeadOwnershipTest();
        t.oval(200, 212, 6, 4, -1, false);
        for (int x = 182; x <= 218; x++) t.gray[212 * 500 + x] = (byte) 100;
        assertFalse(rejects(t));
    }

    @Test
    public void absentOrIncompleteRasterIsNotNegativeEvidence() {
        assertFalse(DiffuseLedgerSurface.matches(null, 500, 300, 194, 208, 206, 216, 212, 16));
        assertFalse(
                DiffuseLedgerSurface.matches(new byte[5], 500, 300, 194, 208, 206, 216, 212, 16));
    }
}
