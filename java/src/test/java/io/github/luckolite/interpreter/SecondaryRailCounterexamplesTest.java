// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Additional original procedural counterexamples, separate from the frozen candidate. */
public class SecondaryRailCounterexamplesTest {
    @Test
    public void finiteSecondaryWithSameInkAsThickStaffMustRemainTwo() throws Exception {
        var p = new CompleteStaffRuleBeamBandTest();
        p.setup();
        p.rect(580, 620, 110, 114, 100, 5);
        assertEquals(
                "Two physically separate finite printed beam rails must remain two", 2, p.bands());
    }

    @Test
    public void finiteLightSecondaryWithAntialiasedBoundaryMustRemainTwo() throws Exception {
        var p = new CompleteStaffRuleBeamBandTest();
        p.setup();
        p.rect(580, 620, 110, 114, 110, 5);
        p.rect(580, 620, 111, 113, 100, 5);
        assertEquals("Pale outer beam fringe does not become staff-only", 2, p.bands());
    }

    @Test
    public void completePseudoLedgerFrameRequiresExternalStaffIdentity() {
        int w = 1200, h = 240;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 200);
        // Five adjacent ledger rows can form a complete parallel rail pattern. No semantic
        // staff/clef/part ownership is supplied by this helper's signature.
        for (int k = 0; k < 5; k++)
            for (int x = 200; x <= 1000; x++)
                for (int y = 79 + k * 16; y <= 82 + k * 16; y++) g[y * w + x] = 100;
        assertTrue(
                "Observational contract: caller must already establish this is a physical staff",
                CompleteStaffRuleBeamBand.matches(g, w, h, 600, 80, 16, 165, 111, 114));
    }

    @Test
    public void finiteSecondaryNearOneEndpointRemainsTwo() throws Exception {
        var p = new CompleteStaffRuleBeamBandTest();
        p.setup();
        p.rect(580, 610, 110, 114, 100, 5);
        assertEquals(2, p.bands());
    }

    @Test
    public void shortFinitePartialSecondaryRemainsTwo() throws Exception {
        var p = new CompleteStaffRuleBeamBandTest();
        p.setup();
        p.rect(590, 610, 110, 114, 100, 5);
        assertEquals(2, p.bands());
    }

    @Test
    public void secondaryWithOnlyOneVisibleFiniteEndRemainsTwo() throws Exception {
        var p = new CompleteStaffRuleBeamBandTest();
        p.setup();
        p.rect(580, 1199, 110, 114, 100, 5);
        assertEquals(2, p.bands());
    }

    @Test
    public void slopedPaleFiniteSecondaryRemainsTwo() throws Exception {
        var p = new CompleteStaffRuleBeamBandTest();
        p.setup();
        for (int x = 580; x <= 620; x++) {
            int center = 112 + Math.round((x - 600) * .1f);
            p.rect(x, x, center - 2, center + 2, 100, 5);
        }
        assertEquals(2, p.bands());
    }

    @Test
    public void substantialCurvatureConservativelyAbstains() {
        int w = 1200, h = 240;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 200);
        for (int x = 0; x < w; x++) {
            int shift = Math.round((x - 600) * (x - 600) * .0004f);
            for (int k = 0; k < 5; k++)
                for (int y = 79 + k * 16 + shift; y <= 82 + k * 16 + shift; y++)
                    if (y >= 0 && y < h) g[y * w + x] = 100;
        }
        assertFalse(CompleteStaffRuleBeamBand.matches(g, w, h, 600, 80, 16, 165, 111, 114));
    }
}
