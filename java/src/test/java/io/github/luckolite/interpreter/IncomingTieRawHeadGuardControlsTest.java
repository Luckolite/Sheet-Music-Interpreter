// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original filled/hollow owned-head and rule/shaft-only contrasts. */
public class IncomingTieRawHeadGuardControlsTest {
    final IncomingTieHeadOwnershipTest fixture = new IncomingTieHeadOwnershipTest();

    byte[] withHeads(String mode) {
        var g = fixture.page(false, false);
        for (int xCenter : new int[] {750, 240}) {
            int yCenter = xCenter == 750 ? 238 : 888;
            if (mode.equals("hollow") || mode.equals("filled"))
                for (int y = yCenter - 4; y <= yCenter + 4; y++)
                    for (int x = xCenter - 8; x <= xCenter + 8; x++) {
                        float outer =
                                (x - xCenter) * (x - xCenter) / 64f
                                        + (y - yCenter) * (y - yCenter) / 16f;
                        float inner =
                                (x - xCenter) * (x - xCenter) / 36f
                                        + (y - yCenter) * (y - yCenter) / 4f;
                        if (outer <= 1 && (mode.equals("filled") || inner >= 1))
                            g[y * WideSystemIncomingTieControlsTest.W + x] = 0;
                    }
            if (mode.equals("threePixelRule"))
                WideSystemIncomingTieControlsTest.box(
                        g, xCenter - 8, yCenter - 1, xCenter + 8, yCenter + 1, 0);
            if (mode.equals("ruleAndShaft"))
                WideSystemIncomingTieControlsTest.box(
                        g, xCenter - 8, yCenter - 4, xCenter - 7, yCenter + 4, 0);
        }
        return g;
    }

    boolean proved(String mode) {
        return WideSystemIncomingTieProof.prove(
                        withHeads(mode),
                        WideSystemIncomingTieControlsTest.W,
                        WideSystemIncomingTieControlsTest.H,
                        fixture.before(),
                        fixture.after(),
                        true,
                        1)
                .proved();
    }

    @Test
    public void filledEllipseOnRuleProvesOwnedHeads() {
        assertTrue(proved("filled"));
    }

    @Test
    public void hollowEllipseOnRuleProvesOwnedHeads() {
        assertTrue(proved("hollow"));
    }

    @Test
    public void thickRuleAloneCannotProveEitherHead() {
        assertFalse(proved("threePixelRule"));
    }

    @Test
    public void staffRuleCrossingShaftCannotProveEitherHead() {
        assertFalse(proved("ruleAndShaft"));
    }
}
