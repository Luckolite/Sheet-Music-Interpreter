// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent procedural long-shaft, throughgoing-axis and closed-loop ownership roles. */
public final class FiniteHollowShaftCapOriginalTest {
    static final int W = 400, H = 360;

    static final class Scene {
        final byte[] gray = new byte[W * H];
        final int gap, cx, cy, rx, ry, l, t, r, b, x, staffTop, staffBottom, end;

        Scene(int g) {
            gap = g;
            cx = Math.round(g * 7.5f);
            cy = Math.round(g * 5.625f);
            rx = Math.round(g * .7f);
            ry = Math.round(g * .4f);
            l = cx - rx;
            r = cx + rx;
            int height = (int) Math.ceil(Math.sqrt(ry * ry + .09 * rx * rx));
            t = cy - height;
            b = cy + height;
            x = l;
            staffTop = cy - Math.round(g * .375f);
            staffBottom = staffTop + 4 * g;
            end = staffBottom + 1;
            Arrays.fill(gray, (byte) 250);
            for (int yy = t; yy <= b; yy++)
                for (int xx = l; xx <= r; xx++) {
                    double q =
                            (xx - cx) * (xx - cx) / (double) (rx * rx)
                                    + Math.pow(yy - cy + .3 * (xx - cx), 2) / (ry * ry);
                    if (q <= 1.08 && q >= .48) gray[yy * W + xx] = 45;
                }
            for (int yy = staffTop; yy <= staffBottom; yy += g)
                for (int xx = 20; xx < W - 20; xx++) gray[yy * W + xx] = 35;
        }

        void axis(int xx, int from, int to) {
            for (int yy = from; yy <= to; yy++)
                for (int dx = 0; dx < 2; dx++) gray[yy * W + xx + dx] = 30;
        }

        int ownRoot() {
            return cy + Math.round(gap * .2f);
        }

        boolean proves(int shaftX) {
            byte[] old = gray.clone();
            boolean result =
                    FiniteHollowShaftCap.proves(
                            gray,
                            W,
                            H,
                            l,
                            t,
                            r,
                            b,
                            cx,
                            cy,
                            gap,
                            new int[] {shaftX, end, 1},
                            staffTop,
                            staffBottom);
            assertArrayEquals(old, gray);
            return result;
        }

        void assertOldProjection() {
            int n = 0;
            for (int yy = staffTop; yy <= staffBottom; yy++)
                if ((gray[yy * W + x] & 255) < 170) n++;
            assertTrue(
                    "This must really exercise the rejected whole-staff projection",
                    n >= (staffBottom - staffTop + 1) * .85f);
        }
    }

    @Test
    public void ownDownShaftMayCoverWholeStaffAt16() {
        Scene s = new Scene(16);
        s.axis(s.x, s.ownRoot(), s.end);
        s.assertOldProjection();
        assertTrue(s.proves(s.x));
    }

    @Test
    public void ownDownShaftMayCoverWholeStaffAt24() {
        Scene s = new Scene(24);
        s.axis(s.x, s.ownRoot(), s.end);
        s.assertOldProjection();
        assertTrue(s.proves(s.x));
    }

    @Test
    public void completeOvalDoesNotOwnThroughgoingFiniteBarline() {
        Scene s = new Scene(16);
        s.axis(s.x, s.t - 16, s.end + 16);
        assertFalse(s.proves(s.x));
    }

    @Test
    public void axisThroughCounterIsNotAnEdgeRootedShaft() {
        Scene s = new Scene(16);
        s.axis(s.cx, s.ownRoot(), s.end);
        assertFalse(s.proves(s.cx));
    }

    @Test
    public void detachedForeignVerticalCannotBorrowOvalBoundary() {
        Scene s = new Scene(16);
        s.axis(s.x - 7, s.ownRoot(), s.end);
        assertFalse(s.proves(s.x - 7));
    }

    @Test
    public void twoRowDamageDoesNotTurnThroughgoingBarlineIntoOwnShaft() {
        Scene s = new Scene(16);
        s.axis(s.x, s.t - 16, s.end + 16);
        // The actual bar continues above the local paper interruption and beyond the staff.
        for (int yy = s.ownRoot() - 5; yy <= s.ownRoot() - 4; yy++)
            for (int dx = -1; dx <= 1; dx++) s.gray[yy * W + s.x + dx] = (byte) 250;
        assertFalse(s.proves(s.x));
    }

    @Test
    public void oppositeEndForeignHeadFlagLoopRemainsRejectedByWholeCaller() {
        var p = new UnbeamedHollowRecoveryTest();
        p.oval(222, 194, false, true);
        p.shaft(236, 132, 196);
        p.oval(250, 134, true, false);
        var notes = p.notes();
        assertEquals(1, notes.size());
        assertEquals(1, p.near(notes, 194).size());
        assertTrue(p.near(notes, 134).isEmpty());
    }

    @Test
    public void openSlurOverLongShaftCannotSupplyAClosedOvalInWholeCaller() {
        var p = new UnbeamedHollowRecoveryTest();
        p.oval(380, 174, false, true);
        p.shaft(394, 115, 175);
        for (int xx = 235; xx <= 264; xx++) {
            int yy = Math.round(140 - 10 * 4 * ((xx - 235) / 29f) * (1 - (xx - 235) / 29f));
            p.ink(xx, yy, 35, 5);
            p.ink(xx, yy + 1, 35, 5);
        }
        p.shaft(236, 140, 201);
        var notes = p.notes();
        assertTrue(p.near(notes, 134).isEmpty());
        assertFalse(
                "Unrelated real head ensures full caller is nonvacuous",
                p.near(notes, 174).isEmpty());
    }
}
