// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class DetachedQuarterSlurTest {
    static int count(int blank, boolean bowed, boolean up) throws Exception {
        int W = 400, H = 300, X = 180, Y = 160;
        byte[] g = new byte[W * H], l = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = Y - 9; y <= Y + 9; y++)
            for (int x = X - 24; x <= X; x++)
                if (Math.pow((x - (X - 12)) / 12.0, 2) + Math.pow((y - Y) / 9.0, 2) <= 1) {
                    g[y * W + x] = 20;
                    l[y * W + x] = 2;
                }
        for (int y = 96 + blank; y <= Y; y++)
            for (int x = X - 1; x <= X + 1; x++) {
                g[y * W + x] = 20;
                l[y * W + x] = 1;
            }
        for (int dx = -65; dx <= 65; dx++) {
            int cy = 92 + (bowed ? Math.round(dx * dx / 400f) : 0);
            for (int dy = -3; dy <= 3; dy++) g[(cy + dy) * W + X + dx] = 20;
        }
        if (!up) {
            byte[] gg = g.clone(), ll = l.clone();
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    g[y * W + x] = gg[(H - 1 - y) * W + W - 1 - x];
                    l[y * W + x] = ll[(H - 1 - y) * W + W - 1 - x];
                }
        }
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ct =
                c.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        ct.setAccessible(true);
        int cy = up ? Y : H - 1 - Y;
        Object head =
                ct.newInstance(
                        330,
                        up ? X - 24 : W - 1 - X,
                        up ? X : W - 1 - X + 24,
                        cy - 9,
                        cy + 9,
                        up ? X - 12f : W - 1 - X + 12f,
                        (float) cy);
        var t = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var st = t.getDeclaredConstructor(float.class, float.class, float.class);
        st.setAccessible(true);
        Object staff = st.newInstance(100f, 180f, 20f);
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
        return (int) m.invoke(null, l, g, W, H, head, staff, false, false, null);
    }

    @org.junit.Test
    public void upperDetachedCurveLeavesQuarter() throws Exception {
        org.junit.Assert.assertEquals(0, count(3, true, true));
    }

    @org.junit.Test
    public void lowerDetachedCurveLeavesQuarter() throws Exception {
        org.junit.Assert.assertEquals(0, count(3, true, false));
    }

    @org.junit.Test
    public void straightRailsRemainBeams() throws Exception {
        for (boolean up : new boolean[] {true, false})
            org.junit.Assert.assertEquals(1, count(3, false, up));
    }

    @org.junit.Test
    public void connectedCurvedInkIsNotDiscarded() throws Exception {
        for (boolean up : new boolean[] {true, false})
            org.junit.Assert.assertEquals(1, count(0, true, up));
    }

    @org.junit.Test
    public void remoteCurvesAlreadyLeaveQuarters() throws Exception {
        for (boolean up : new boolean[] {true, false})
            org.junit.Assert.assertEquals(0, count(5, true, up));
    }
}
