// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;

/** Original detached short bows paired across two systems; no score pixels. */
public class DetachedShortOutgoingTieTest {
    MiniatureBoundaryTieTest page(int mode, boolean above) {
        var p = new MiniatureBoundaryTieTest();
        Arrays.fill(p.gray, (byte) 255);
        for (int top : new int[] {90, 300})
            for (int k = 0; k < 5; k++) for (int x = 40; x < 780; x++) p.ink(x, top + k * 16, 4);
        int side = above ? -1 : 1, cy = mode == 3 ? 388 : 396;
        p.head(650, 186);
        p.head(180, cy);
        if (mode != 1) p.arc(672, 686, 186, side, mode == 5);
        if (mode != 2) p.arc(136, 168, cy, mode == 4 ? -side : side, mode == 5);
        if (mode == 6)
            for (int y = 170; y < 202; y++)
                for (int x = 677; x <= 681; x++) {
                    p.gray[y * 800 + x] = (byte) 255;
                    p.labels[y * 800 + x] = 0;
                }
        return p;
    }

    @Test
    public void shortDetachedAboveJoins() {
        page(0, true).expected(true);
    }

    @Test
    public void shortDetachedBelowJoins() {
        page(0, false).expected(true);
    }

    @Test
    public void incomingOnlyCannotJoin() {
        page(1, true).expected(false);
    }

    @Test
    public void outgoingOnlyCannotJoin() {
        page(2, true).expected(false);
    }

    @Test
    public void differentPitchCannotJoin() {
        page(3, true).expected(false);
    }

    @Test
    public void oppositeSidesCannotJoin() {
        page(4, true).expected(false);
    }

    @Test
    public void straightStrokeCannotJoin() {
        page(5, true).expected(false);
    }

    @Test
    public void brokenBowCannotJoin() {
        page(6, true).expected(false);
    }

    @Test
    public void arraysUnchanged() {
        var p = page(0, true);
        var g = p.gray.clone();
        var l = p.labels.clone();
        p.expected(true);
        assertArrayEquals(g, p.gray);
        assertArrayEquals(l, p.labels);
    }
}
