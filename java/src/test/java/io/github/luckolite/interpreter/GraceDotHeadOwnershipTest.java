// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original separate small noteheads and dot masks, with independently drawn stems. */
public class GraceDotHeadOwnershipTest {
    static final int W = 160, H = 160;

    static Class<?> type() throws Exception {
        return Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
    }

    Object component(int area, int l, int r, int t, int b, float x, float y) throws Exception {
        Constructor<?> c =
                type().getDeclaredConstructor(
                                int.class,
                                int.class,
                                int.class,
                                int.class,
                                int.class,
                                float.class,
                                float.class);
        c.setAccessible(true);
        return c.newInstance(area, l, r, t, b, x, y);
    }

    Object note(byte[] p, int x, int y, boolean stem) throws Exception {
        int area = 0;
        for (int yy = y - 4; yy <= y + 4; yy++)
            for (int xx = x - 6; xx <= x + 6; xx++)
                if (Math.pow((xx - x) / 6., 2) + Math.pow((yy - y) / 4., 2) <= 1) {
                    p[yy * W + xx] = 0;
                    area++;
                }
        if (stem) for (int yy = y - 30; yy <= y; yy++) p[yy * W + x + 5] = 0;
        return component(area, x - 6, x + 6, y - 4, y + 4, x, y);
    }

    boolean owned(byte[] p, Object head, Object dot, List<Object> heads) throws Exception {
        Method m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "graceOwnedDot",
                        byte[].class,
                        int.class,
                        int.class,
                        type(),
                        type(),
                        float.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, W, H, head, dot, 14f, heads);
    }

    byte[] page() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 250);
        return p;
    }

    @Test
    public void independentlyStemmedNextHeadCannotBeADot() throws Exception {
        byte[] p = page();
        Object h = note(p, 60, 100, true),
                next = note(p, 80, 105, true),
                dot = component(21, 78, 82, 103, 107, 80, 105);
        assertTrue(owned(p, h, dot, List.of(h, next)));
    }

    @Test
    public void unstemmedIslandDoesNotProveNoteOwnership() throws Exception {
        byte[] p = page();
        Object h = note(p, 60, 100, true),
                next = note(p, 80, 105, false),
                dot = component(21, 78, 82, 103, 107, 80, 105);
        assertFalse(owned(p, h, dot, List.of(h, next)));
    }

    @Test
    public void missingNeighborPreservesDot() throws Exception {
        byte[] p = page();
        Object h = note(p, 60, 100, true), dot = component(21, 78, 82, 103, 107, 80, 105);
        assertFalse(owned(p, h, dot, List.of(h)));
    }

    @Test
    public void separateDotBesideStemmedNeighborRemains() throws Exception {
        byte[] p = page();
        Object h = note(p, 60, 100, true),
                next = note(p, 80, 105, true),
                dot = component(13, 86, 90, 103, 107, 88, 105);
        assertFalse(owned(p, h, dot, List.of(h, next)));
    }

    @Test
    public void similarSizedIslandDoesNotProveSmallerDot() throws Exception {
        byte[] p = page();
        Object h = note(p, 60, 100, true),
                next = note(p, 80, 105, true),
                dot = component(73, 74, 86, 101, 109, 80, 105);
        assertFalse(owned(p, h, dot, List.of(h, next)));
    }

    @Test
    public void sourcePixelsAreUnchanged() throws Exception {
        byte[] p = page();
        Object h = note(p, 60, 100, true),
                next = note(p, 80, 105, true),
                dot = component(21, 78, 82, 103, 107, 80, 105);
        byte[] copy = p.clone();
        owned(p, h, dot, List.of(h, next));
        assertArrayEquals(copy, p);
    }
}
