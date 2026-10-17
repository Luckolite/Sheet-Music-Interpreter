// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Complete original ovals cannot acquire a remote owner through an admitted foreign core. */
public class UpperLedgerForeignCoreTest {
    static UpperLedgerAdversarialProbe.Page scene(float dx, boolean hollow, int tone) {
        var p = WholeNeighborPhaseProbe.scene(150 + dx, 376.5f, hollow, 0);
        for (int y = 311; y <= 375; y += 16)
            for (int x = 20; x <= 620; x++)
                if (p.l[y * 640 + x] == 4) p.g[y * 640 + x] = (byte) tone;
        return p;
    }

    static void foreign(boolean hollow, boolean faded) {
        for (float dx : new float[] {-.4f, 0, .4f})
            for (int tone :
                    faded
                            ? new int[] {171, 200, 224, 225, 226, 230, 238, 240, 250, 254}
                            : new int[] {0})
                assertTrue(
                        "new fallback abstains, dx=" + dx + ",tone=" + tone,
                        UpperLedgerPhysicalOwnershipTest.body(scene(dx, hollow, tone)).isEmpty());
    }

    @Test
    public void filledForeignCoreCannotBecomeRemoteLedgerHead() {
        foreign(false, false);
    }

    @Test
    public void hollowForeignCoreCannotBecomeRemoteLedgerHead() {
        foreign(true, false);
    }

    @Test
    public void filledBodyDoesNotBorrowFadedForeignStaff() {
        foreign(false, true);
    }

    @Test
    public void hollowBodyDoesNotBorrowFadedForeignStaff() {
        foreign(true, true);
    }
}
