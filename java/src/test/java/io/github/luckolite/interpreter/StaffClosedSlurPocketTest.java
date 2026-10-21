// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Authored staff-closed wedge and complete oval contours; no score pixels. */
public class StaffClosedSlurPocketTest {
    static final int W = 240, H = 200;
    byte[] gray = new byte[W * H];

    public StaffClosedSlurPocketTest() {
        Arrays.fill(gray, (byte) 255);
    }

    void ink(int x, int y) {
        gray[y * W + x] = 30;
    }

    void rules() {
        for (int y : new int[] {100, 101, 120, 121}) for (int x = 20; x < 220; x++) ink(x, y);
    }

    Object head(int l, int r, int t, int b, float x, float y) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(100, l, r, t, b, x, y);
    }

    boolean independent(Object h) throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "independentHollowContours",
                        byte[].class,
                        int.class,
                        int.class,
                        h.getClass(),
                        float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, gray, W, H, h, 20f);
    }

    boolean oldPocket(Object h) throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "enclosedHollowInk", byte[].class, int.class, h.getClass(), boolean.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, gray, W, h, true);
    }

    Object wedge() throws Exception {
        rules();
        for (int y = 100; y <= 121; y++) {
            int edge = 128 - (y - 100);
            for (int dx = 0; dx < 3; dx++) ink(edge + dx, y);
            for (int x = 130; x <= 132; x++) ink(x, y);
        }
        for (int y = 45; y <= 125; y++) for (int x = 130; x <= 132; x++) ink(x, y);
        return head(106, 132, 100, 122, 119, 111);
    }

    Object oval(boolean ruled) throws Exception {
        if (ruled) rules();
        for (int y = 96; y <= 126; y++)
            for (int x = 106; x <= 144; x++) {
                double dx = x - 125, dy = y - 111;
                double q = dx * dx / 196 + (dy + .3 * dx) * (dy + .3 * dx) / 64;
                if (q <= 1.2 && q >= .48) ink(x, y);
            }
        return head(109, 141, 98, 124, 125, 111);
    }

    @Test
    public void staffClosedSlurWedgeHasPocketButNoIndependentOvalCaps() throws Exception {
        var h = wedge();
        assertTrue(oldPocket(h));
        assertFalse(independent(h));
    }

    @Test
    public void completeHollowOvalHasIndependentContours() throws Exception {
        var h = oval(false);
        assertTrue(oldPocket(h));
        assertTrue(independent(h));
    }

    @Test
    public void completeOvalCrossingRulesKeepsIndependentContours() throws Exception {
        var h = oval(true);
        assertTrue(oldPocket(h));
        assertTrue(independent(h));
    }

    @Test
    public void mirroredStaffClosedWedgeStillAbstains() throws Exception {
        var h = wedge();
        byte[] original = gray.clone();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) gray[y * W + x] = original[y * W + W - 1 - x];
        assertFalse(independent(head(W - 1 - 132, W - 1 - 106, 100, 122, W - 1 - 119, 111)));
    }

    @Test
    public void contourCheckDoesNotWritePixels() throws Exception {
        var h = oval(true);
        byte[] old = gray.clone();
        independent(h);
        assertArrayEquals(old, gray);
    }
}
