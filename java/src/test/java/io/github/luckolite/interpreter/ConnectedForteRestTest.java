// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

/** Original Bezier lettering with connected neighboring arches, no score pixels. */
public class ConnectedForteRestTest {
    static ForteHookHeadTest page(boolean cross, boolean foot) {
        var p = new ForteHookHeadTest();
        p.letter(cross, foot);
        for (int x : new int[] {180, 186, 192}) {
            p.curve(x - 6, 238, x - 3, 231, x, 222, x, 218, 1.5);
            p.curve(x - 3, 220, x, 215, x + 3, 215, x + 2, 220, 1.5);
        }
        return p;
    }

    static boolean guard(ForteHookHeadTest p) {
        return ForteRestGuard.owns(p.gray, 420, 300, 190, 214, 179, 216, 16);
    }

    @Test
    public void connectedLetterCannotTurnForteIntoRest() {
        assertTrue(guard(page(true, true)));
    }

    @Test
    public void crossStrokeRequired() {
        assertFalse(guard(page(false, true)));
    }

    @Test
    public void lowerFootRequired() {
        assertFalse(guard(page(true, false)));
    }

    @Test
    public void blankPixelsCannotOwnRest() {
        var p = page(true, true);
        java.util.Arrays.fill(p.gray, (byte) 255);
        assertFalse(guard(p));
    }

    @Test
    public void ordinaryEighthRestIsNotForte() {
        var p = new BracketedLowerEighthRestTest.Page(true, true, false, true);
        assertFalse(ForteRestGuard.owns(p.gray, 800, 360, 173, 191, 160, 191, 16));
    }

    @Test
    public void translatedCandidateNeedsSameConnectedInk() {
        var p = page(true, true);
        assertFalse(ForteRestGuard.owns(p.gray, 420, 300, 230, 254, 179, 216, 16));
    }

    @Test
    public void doubleScaleRetainsContourProof() {
        var p = page(true, true);
        byte[] g = new byte[840 * 600];
        for (int y = 0; y < 600; y++)
            for (int x = 0; x < 840; x++) g[y * 840 + x] = p.gray[(y / 2) * 420 + x / 2];
        assertTrue(ForteRestGuard.owns(g, 840, 600, 380, 429, 358, 433, 32));
    }

    @Test
    public void sourcePixelsPreserved() {
        var p = page(true, true);
        byte[] b = p.gray.clone();
        guard(p);
        assertArrayEquals(b, p.gray);
    }
}
