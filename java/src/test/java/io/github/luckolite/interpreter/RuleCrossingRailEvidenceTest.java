// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original continuous rail/rule geometry, independent of any score pixels. */
public class RuleCrossingRailEvidenceTest {
    static final int W = 180, H = 180;

    byte[] page(int mode) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 250);
        for (int y = 85; y <= 141; y += 14)
            for (int d = -2; d <= 2; d++)
                for (int x = 10; x < W - 10; x++) {
                    if ((mode & 2) != 0 && y >= 113) continue;
                    int yy = y + d + (((mode & 16) != 0 && (y == 113 || y == 127)) ? 5 : 0);
                    g[yy * W + x] = 80;
                }
        for (int x = 50; x <= 110; x++)
            for (int y = (mode & 4) != 0 ? 70 : 76; y <= ((mode & 4) != 0 ? 103 : 84); y++) {
                if ((mode & 1) != 0 && x >= 77 && x <= 82) continue;
                if ((mode & 8) != 0 && y < 83) continue;
                g[y * W + x] = 80;
            }
        return g;
    }

    boolean path(byte[] g) {
        return PrintedRuleCrossingBeamPath.connects(
                new byte[g.length],
                g,
                W,
                H,
                new int[] {50, 76, -1},
                new int[] {110, 76, -1},
                14,
                85,
                null);
    }

    @Test
    public void crossingKeepsWholeConnectedRail() {
        assertTrue(path(page(0)));
    }

    @Test
    public void brokenRailRejected() {
        assertFalse(path(page(1)));
    }

    @Test
    public void missingThreeOtherRulesRejected() {
        assertFalse(path(page(2)));
    }

    @Test
    public void broadInkRejected() {
        assertFalse(path(page(4)));
    }

    @Test
    public void staffLineAloneRejected() {
        assertFalse(path(page(8)));
    }

    @Test
    public void incompatibleRulePhasesRejected() {
        assertFalse(path(page(16)));
    }

    @Test
    public void originalStrictPathStillRejectsCrossing() {
        assertFalse(
                PrintedSingleBeamPath.connects(
                        page(0), W, H, new int[] {50, 76, -1}, new int[] {110, 76, -1}, 14));
    }

    @Test
    public void sourcePreserved() {
        byte[] g = page(0), copy = g.clone();
        assertTrue(path(g));
        assertArrayEquals(copy, g);
    }

    @Test
    public void opposedDirectionsRejected() {
        assertFalse(
                PrintedRuleCrossingBeamPath.connects(
                        new byte[W * H],
                        page(0),
                        W,
                        H,
                        new int[] {50, 76, -1},
                        new int[] {110, 76, 1},
                        14,
                        85,
                        null));
    }

    @Test
    public void malformedRejected() {
        assertFalse(path(new byte[2]));
    }
}
