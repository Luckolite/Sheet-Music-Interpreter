// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedTupletBeamOwnerTest {
    private static final int W = 400, H = 500;

    private static byte[] ink(boolean stems, boolean thick, boolean gap) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = thick ? 167 : 170; y <= (thick ? 173 : 170); y++)
            for (int x = 108; x <= 228; x++) g[y * W + x] = 0;
        if (stems)
            for (int x : new int[] {108, 168, 228})
                for (int y = 100; y <= 170; y++) {
                    if (gap && x == 228 && y >= 130 && y <= 140) continue;
                    for (int dx = -1; dx <= 1; dx++) g[y * W + x + dx] = 0;
                }
        return g;
    }

    private static boolean owns(byte[] g, int top, int bottom, int direction) {
        return PrintedTupletBeamOwner.owns(
                g, W, H, 120, 100, 240, 100, 20, direction, 162, top, 174, bottom);
    }

    @Test
    public void numeralBesideContinuousBeamHasPhysicalOwner() {
        assertTrue(owns(ink(true, true, false), 190, 211, -1));
    }

    @Test
    public void aLongWhiteShaftGapCannotBridgeIntoAnotherBeam() {
        assertFalse(owns(ink(true, true, true), 190, 211, -1));
    }

    @Test
    public void aStaffRuleAndMissingShaftCannotProveOwnership() {
        assertFalse(owns(ink(true, false, false), 190, 211, -1));
        assertFalse(owns(ink(false, true, false), 190, 211, -1));
    }

    @Test
    public void foreignNumeralFarFromOrInsideBeamKeepsIndependentOwnership() {
        assertFalse(owns(ink(true, true, false), 225, 246, -1));
        assertFalse(owns(ink(true, true, false), 160, 181, -1));
    }

    @Test
    public void oppositeStemDirectionCannotBorrowThisBeam() {
        assertFalse(owns(ink(true, true, false), 190, 211, 1));
    }
}
