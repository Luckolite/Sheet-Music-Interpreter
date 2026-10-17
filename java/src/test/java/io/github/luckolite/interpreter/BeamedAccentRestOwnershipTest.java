// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/**
 * Original analytic open chevrons beside independently drawn beamed note shafts; no score pixels.
 */
public class BeamedAccentRestOwnershipTest {
    static BeamedHeadQuarterRestsTest.Page accent(float gap, int direction, boolean fringe) {
        var p =
                new BeamedHeadQuarterRestsTest.Page(
                        gap, false, true, true, direction, true, 0, 250, 110, 2.6f, -1.1f);
        float cx = p.hx - (direction == 1 ? .3f : 0) * gap, top = p.hy - 3.05f * gap;
        int l = Math.round(cx - .8f * gap),
                r = Math.round(cx + .8f * gap),
                a = Math.round(top),
                b = Math.round(top + 1.2f * gap);
        for (int y = a; y <= b; y++)
            for (int x = l; x <= r; x++) {
                float u = (x - l) / (float) (r - l),
                        upper = a + (b - a) * .5f * u,
                        lower = b - (b - a) * .5f * u;
                if (Math.abs(y - upper) <= 1.1f
                        || Math.abs(y - lower) <= 1.1f
                        || u >= .75f && y >= upper && y <= lower) p.pixel(x, y, 0, 35);
            }
        if (fringe) for (int x = l; x <= Math.min(r, l + 2); x++) p.pixel(x, b + 1, 0, 160);
        return p;
    }

    @Test
    public void completeAccentAboveDownwardBeamedHeadIsNotAQuarter() throws Exception {
        assertTrue(accent(16, -1, false).read().isEmpty());
    }

    @Test
    public void largerCompleteAccentIsNotAQuarter() throws Exception {
        assertTrue(accent(20, -1, false).read().isEmpty());
    }

    @Test
    public void accentFringeCannotInventTheRestFoot() throws Exception {
        assertTrue(accent(16, -1, true).read().isEmpty());
    }

    @Test
    public void upwardBeamedOwnerKeepsItsAccentOnly() throws Exception {
        assertTrue(accent(16, 1, false).read().isEmpty());
    }

    @Test
    public void genuineCoveredQuarterRetainsItsUpperZigzag() throws Exception {
        assertEquals(
                1,
                new BeamedHeadQuarterRestsTest.Page(
                                16, true, true, true, 1, true, 0, 250, 110, 2.6f)
                        .read()
                        .size());
    }

    @Test
    public void genuineDownwardQuarterRetainsItsUpperZigzag() throws Exception {
        assertEquals(
                1,
                new BeamedHeadQuarterRestsTest.Page(
                                16, true, true, true, -1, true, 0, 250, 110, 2.6f)
                        .read()
                        .size());
    }

    @Test
    public void faintGenuineQuarterIsNotDiscardedAsAnAccent() throws Exception {
        assertEquals(
                1,
                new BeamedHeadQuarterRestsTest.Page(
                                16, true, true, true, 1, true, 145, 250, 110, 2.6f)
                        .read()
                        .size());
    }

    @Test
    public void ordinaryBareBeamedHeadStaysRestFree() throws Exception {
        assertTrue(
                new BeamedHeadQuarterRestsTest.Page(
                                16, false, true, true, -1, true, 0, 250, 110, 2.6f)
                        .read()
                        .isEmpty());
    }
}
