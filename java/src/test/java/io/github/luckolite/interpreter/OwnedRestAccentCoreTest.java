// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original core/fringe adversaries supplement the production-route regression. */
public class OwnedRestAccentCoreTest {
    static int[] bounds(BeamedHeadQuarterRestsTest.Page p) {
        return new int[] {
            Math.round(p.hx - .8f * p.g),
            Math.round(p.hy - 3.05f * p.g),
            Math.round(p.hx + .8f * p.g),
            Math.round(p.hy - 3.05f * p.g + 1.2f * p.g)
        };
    }

    static boolean owns(BeamedHeadQuarterRestsTest.Page p, int[] b) {
        return BeamedHeadQuarterRests.ownedAccent(
                p.gray, BeamedHeadQuarterRestsTest.W, b[0], b[1], b[2], b[3]);
    }

    @Test
    public void connectedChevronCoreHasPositiveOwnership() {
        var p = BeamedAccentRestOwnershipTest.accent(16, -1, false);
        assertTrue(owns(p, bounds(p)));
    }

    @Test
    public void onePixelFringeKeepsTheSameCompleteOwner() {
        var p = BeamedAccentRestOwnershipTest.accent(16, -1, true);
        var b = bounds(p);
        b[3]++;
        assertTrue(owns(p, b));
    }

    @Test
    public void distantWeakTailCannotBeDropped() {
        var p = BeamedAccentRestOwnershipTest.accent(16, -1, false);
        var b = bounds(p);
        for (int y = b[3] + 1; y <= b[3] + 5; y++) p.pixel(b[0], y, 0, 160);
        b[3] += 5;
        assertFalse(owns(p, b));
    }

    @Test
    public void weakTipCannotConnectTwoDarkSlashes() {
        var p = BeamedAccentRestOwnershipTest.accent(16, -1, false);
        var b = bounds(p);
        for (int y = b[1]; y <= b[3]; y++)
            for (int x = b[2] - 4; x <= b[2]; x++)
                if ((p.gray[y * BeamedHeadQuarterRestsTest.W + x] & 255) < 140)
                    p.pixel(x, y, 0, 160);
        assertFalse(owns(p, b));
    }

    @Test
    public void paperAndAnIndependentSpeckCannotProveAnAccent() {
        byte[] g = new byte[300];
        Arrays.fill(g, (byte) 240);
        g[20] = 10;
        assertFalse(BeamedHeadQuarterRests.ownedAccent(g, 20, 0, 0, 19, 14));
    }

    @Test
    public void unprovedFaintArmsDoNotEraseARest() {
        var p = BeamedAccentRestOwnershipTest.accent(16, -1, false);
        var b = bounds(p);
        for (int y = b[1]; y <= b[3]; y++)
            for (int x = b[0]; x <= b[2]; x++)
                if ((p.gray[y * BeamedHeadQuarterRestsTest.W + x] & 255) < 140)
                    p.pixel(x, y, 0, 145);
        assertFalse(owns(p, b));
    }

    @Test
    public void classifierNeverMutatesOriginalInk() {
        var p = BeamedAccentRestOwnershipTest.accent(16, -1, true);
        var before = p.gray.clone();
        var b = bounds(p);
        b[3]++;
        assertTrue(owns(p, b));
        assertArrayEquals(before, p.gray);
    }

    @Test
    public void invalidBoundsFailBeforeAllocating() {
        byte[] g = new byte[300];
        assertFalse(BeamedHeadQuarterRests.ownedAccent(g, 0, 0, 0, 2, 2));
        assertFalse(BeamedHeadQuarterRests.ownedAccent(g, 20, -1, 0, 2, 2));
        assertFalse(BeamedHeadQuarterRests.ownedAccent(g, 20, 0, 0, 20, 2));
        assertFalse(BeamedHeadQuarterRests.ownedAccent(g, 20, 0, 0, 2, 15));
    }
}
