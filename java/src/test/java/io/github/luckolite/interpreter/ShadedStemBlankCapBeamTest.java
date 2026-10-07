// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShadedStemBlankCapBeamTest {
    static final int W = 480, H = 320, G = 16, X = 220, Y = 120;

    static int count(boolean continuous, int paper, int slope, int thickness, boolean upward)
            throws Exception {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) paper);
        for (int yy = Y - 7; yy <= Y + 7; yy++)
            for (int xx = X + 1; xx <= X + 22; xx++) {
                gray[yy * W + xx] = 20;
                labels[yy * W + xx] = 2;
            }
        for (int yy = Y; yy <= 183; yy++)
            for (int xx = X - 1; xx <= X + 1; xx++) {
                gray[yy * W + xx] = 20;
                labels[yy * W + xx] = 1;
            }
        for (int yy = 178; yy <= 183; yy++)
            for (int xx = X - 45; xx <= X + 65; xx++) gray[yy * W + xx] = 20;
        for (int xx = X - 45; xx <= X + 65; xx++) {
            int center = 189 + Math.round((xx - X) * slope * .025f);
            int localThickness = xx < X - 7 ? thickness : 1;
            for (int d = -localThickness; d <= localThickness; d++)
                gray[(center + d) * W + xx] = 75;
        }
        for (int yy = 186; yy <= 192; yy++)
            for (int xx = X - 2; xx <= X + 2; xx++) gray[yy * W + xx] = 80;
        if (continuous)
            for (int yy = 184; yy <= 185; yy++)
                for (int xx = X - 2; xx <= X + 2; xx++) gray[yy * W + xx] = 20;
        if (upward) {
            byte[] g = gray.clone(), l = labels.clone();
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    gray[y * W + x] = g[(H - 1 - y) * W + W - 1 - x];
                    labels[y * W + x] = l[(H - 1 - y) * W + W - 1 - x];
                }
        }
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor =
                c.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        ctor.setAccessible(true);
        int center = upward ? H - 1 - Y : Y;
        Object head =
                ctor.newInstance(
                        330,
                        upward ? W - 1 - X - 22 : X + 1,
                        upward ? W - 2 - X : X + 22,
                        center - 7,
                        center + 7,
                        upward ? W - 1 - X - 11.5f : X + 11.5f,
                        (float) center);
        Class<?> t = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var sc = t.getDeclaredConstructor(float.class, float.class, float.class);
        sc.setAccessible(true);
        Object staff = sc.newInstance(upward ? 175f : 80f, upward ? 239f : 144f, 16f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        t,
                        boolean.class,
                        boolean.class,
                        int[].class);
        m.setAccessible(true);
        return (int) m.invoke(null, labels, gray, W, H, head, staff, false, false, null);
    }

    @Test
    public void detachedSlopingInkDoesNotBecomeSecondDownwardBeam() throws Exception {
        for (int paper : new int[] {125, 140, 150})
            for (int slope : new int[] {-8, 8})
                assertEquals(1, count(false, paper, slope, 2, false));
    }

    @Test
    public void detachedSlopingInkDoesNotBecomeSecondUpwardBeam() throws Exception {
        for (int paper : new int[] {125, 140, 150})
            for (int slope : new int[] {-8, 8})
                assertEquals(1, count(false, paper, slope, 2, true));
    }

    @Test
    public void genuineSecondBeamRetainsContinuousDownwardShaft() throws Exception {
        for (int paper : new int[] {125, 140, 150})
            for (int slope : new int[] {-8, 8})
                assertEquals(2, count(true, paper, slope, 2, false));
    }

    @Test
    public void genuineSecondBeamRetainsContinuousUpwardShaft() throws Exception {
        for (int paper : new int[] {125, 140, 150})
            for (int slope : new int[] {-8, 8}) assertEquals(2, count(true, paper, slope, 2, true));
    }

    @Test
    public void originalThinDetachedLineIsAlreadySingleBeam() throws Exception {
        assertEquals(1, count(false, 140, -8, 1, false));
        assertEquals(1, count(false, 140, 8, 1, true));
    }
}
