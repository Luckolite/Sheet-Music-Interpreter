// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic edge strips; no private score pixels. */
public final class ShiftedBeamBoundsTest {
    private boolean probe(boolean bottom, boolean shifted) throws Exception {
        int w = 80, h = 80;
        byte[] gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        int py = bottom ? 63 : 16;
        int edge = shifted ? py + (bottom ? 1 : -1) : py;
        for (int y = bottom ? edge : 0; y <= (bottom ? 79 : edge); y++) gray[y * w + 40] = 0;
        var component =
                Class.forName("io.github.luckolite.interpreter.OmrScoreInterpreter$Component");
        var ctor =
                component.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        ctor.setAccessible(true);
        Object head = ctor.newInstance(25, 38, 42, 30, 34, 40f, 32f);
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "shortParallelBeam",
                        byte[].class,
                        int.class,
                        int.class,
                        int[].class,
                        int[].class,
                        component,
                        float.class);
        method.setAccessible(true);
        try {
            return (Boolean)
                    method.invoke(
                            null,
                            gray,
                            w,
                            h,
                            new int[] {20, py, bottom ? -1 : 1},
                            new int[] {60, py, bottom ? -1 : 1},
                            head,
                            16f);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw e;
        }
    }

    @Test
    public void shiftedTopWindowCannotReadNegativeRow() throws Exception {
        assertFalse(probe(false, true));
    }

    @Test
    public void shiftedBottomWindowCannotReadPastImage() throws Exception {
        assertFalse(probe(true, true));
    }

    @Test
    public void originalTopWindowRejectsThickStrip() throws Exception {
        assertFalse(probe(false, false));
    }

    @Test
    public void originalBottomWindowRejectsThickStrip() throws Exception {
        assertFalse(probe(true, false));
    }
}
