// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original notehead ownership contrasts; staff-rule ink is not a retained head. */
public class IncomingTieHeadOwnershipTest {
    static final int W = WideSystemIncomingTieControlsTest.W,
            H = WideSystemIncomingTieControlsTest.H;

    WideSystemIncomingTieProof.Endpoint before() {
        return new WideSystemIncomingTieProof.Endpoint(
                WideSystemIncomingTieControlsTest.note(0, .7f, 0, 238),
                742,
                758,
                234,
                242,
                750,
                238,
                12);
    }

    WideSystemIncomingTieProof.Endpoint after() {
        return new WideSystemIncomingTieProof.Endpoint(
                WideSystemIncomingTieControlsTest.note(1, .1f, 0, 888),
                232,
                248,
                884,
                892,
                240,
                888,
                12);
    }

    byte[] page(boolean headBefore, boolean headAfter) {
        byte[] g = WideSystemIncomingTieControlsTest.frame();
        if (headBefore) WideSystemIncomingTieControlsTest.box(g, 742, 234, 758, 242, 0);
        if (headAfter) WideSystemIncomingTieControlsTest.box(g, 232, 884, 248, 892, 0);
        for (int x = 208; x <= 229; x++) {
            float t = (x - 208f) / 21;
            int y = Math.round(893 + 4 * 4 * t * (1 - t));
            WideSystemIncomingTieControlsTest.box(g, x, y - 1, x, y + 1, 0);
        }
        return g;
    }

    boolean prove(boolean headBefore, boolean headAfter) {
        return WideSystemIncomingTieProof.prove(
                        page(headBefore, headAfter), W, H, before(), after(), true, 1)
                .proved();
    }

    @Test
    public void completeFilledHeadsOnRulesRemainPositive() {
        assertTrue(prove(true, true));
    }

    @Test
    public void staffRulesAloneCannotSupplyBothClaimedHeads() {
        assertFalse("Only seventeen staff-rule pixels occur in each head box", prove(false, false));
    }

    @Test
    public void incomingStaffRuleAloneCannotSupplyClaimedHead() {
        assertFalse("The returning curve has no real incoming head", prove(true, false));
    }

    @Test
    public void outgoingStaffRuleAloneCannotSupplyClaimedHead() {
        assertFalse("The outgoing endpoint has only staff-rule ink", prove(false, true));
    }
}
