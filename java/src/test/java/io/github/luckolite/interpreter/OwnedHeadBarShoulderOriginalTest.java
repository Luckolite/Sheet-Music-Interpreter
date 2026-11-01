// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original paired-staff bars with a separately stemmed filled head touching the bar shoulder. */
public final class OwnedHeadBarShoulderOriginalTest {
    static IndependentGrandStaffBarOriginalTest.Page page(String mode) {
        var p = new IndependentGrandStaffBarOriginalTest.Page("noNeighbor");
        p.oval(210, 80, true);
        p.rect(201, 80, 1, 41, 1);
        p.oval(212, 210, true);
        if (!mode.equals("noOwnShaft")) p.rect(203, 210, 1, 41, 1);
        p.rect(221, 79, 3, 181, 1);
        if (mode.equals("clippedBody")) p.oval(214, 210, false);
        if (mode.equals("unownedBranch")) p.rect(209, 164, 12, 2, 5);
        if (mode.equals("headOwnsAxis")) {
            p.oval(223, 164, true);
            p.rect(214, 164, 1, 41, 1);
        }
        if (mode.equals("brokenAxis"))
            for (int y = 165; y <= 166; y++)
                for (int x = 221; x <= 223; x++) {
                    p.g[y * 420 + x] = (byte) 255;
                    p.l[y * 420 + x] = 0;
                }
        if (mode.equals("extendedAxis")) p.rect(221, 55, 3, 24, 1);
        if (mode.equals("hollowBody"))
            for (int y = 208; y <= 212; y++)
                for (int x = 209; x <= 215; x++) p.g[y * 420 + x] = (byte) 255;
        if (mode.equals("mirror")) {
            // Reflect placement, retaining ordinary head slant and the appropriate stem corner.
            java.util.Arrays.fill(p.g, (byte) 255);
            java.util.Arrays.fill(p.l, (byte) 0);
            for (int top : new int[] {80, 210})
                for (int y = top; y <= top + 48; y += 12) p.rect(20, y, 380, 1, 4);
            p.rect(19, 79, 1, 181, 1);
            p.rect(399, 79, 1, 181, 1);
            p.rect(196, 79, 3, 181, 1);
            p.oval(329, 104, true);
            p.rect(338, 60, 1, 45, 1);
            p.oval(99, 234, true);
            p.rect(108, 190, 1, 45, 1);
            p.oval(209, 80, true);
            p.rect(218, 40, 1, 41, 1);
            p.oval(207, 210, true);
            p.rect(216, 170, 1, 41, 1);
        }
        return p;
    }

    @Test
    public void completeHeadShoulderKeepsOrdinaryGrandStaffBar() {
        assertEquals(2, page("valid").result().size());
    }

    @Test
    public void mirroredCompleteHeadShoulderKeepsBar() {
        assertEquals(2, page("mirror").result().size());
    }

    @Test
    public void missingIndependentShaftDoesNotCreateAuthority() {
        assertEquals(1, page("noOwnShaft").result().size());
    }

    @Test
    public void unlabelledRawBodyFringeCannotBeErased() {
        assertEquals(1, page("clippedBody").result().size());
    }

    @Test
    public void unownedHorizontalBranchStillVetoesBar() {
        assertEquals(1, page("unownedBranch").result().size());
    }

    @Test
    public void completeHeadOnAxisStillOwnsItsStem() {
        assertEquals(1, page("headOwnsAxis").result().size());
    }

    @Test
    public void brokenAxisCannotBorrowHeadEvidence() {
        assertEquals(1, page("brokenAxis").result().size());
    }

    @Test
    public void extendedAxisCannotBorrowStaffBounds() {
        assertEquals(1, page("extendedAxis").result().size());
    }

    @Test
    public void unavailableFilledBodyProofRefusesRecovery() {
        assertEquals(1, page("hollowBody").result().size());
    }

    @Test
    public void originalInputsStayUnchanged() {
        var p = page("valid");
        byte[] l = p.l.clone(), g = p.g.clone();
        p.result();
        assertArrayEquals(l, p.l);
        assertArrayEquals(g, p.g);
    }
}
