// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original analytic grayscale geometry, independent of any score pixels or outline. */
public final class BlurredLeaningDoubleRailTest {
    private static final int W = 300, H = 260;

    private int decoded(int shade, int beamInk, int mask, int rails, int kind) throws Exception {
        byte[] g = new byte[W * H], l = new byte[W * H];
        Arrays.fill(g, (byte) 250);
        for (int index = 0; index < 2; index++) {
            int cx = index == 0 ? 160 : 125,
                    cy = index == 0 ? 170 : 177,
                    sx = index == 0 ? 168 : 135,
                    tip = index == 0 ? 116 : 121;
            for (int y = kind == 5 && index == 1 ? cy : tip;
                    y <= (kind == 5 && index == 1 ? cy + 60 : cy);
                    y++)
                for (int x = sx - 5; x <= sx + 5; x++) {
                    if (kind == 2 && index == 0 && y > 145 && y < 155 || kind == 4 && index == 1)
                        continue;
                    double axis = sx - (index == 0 ? 2 * (cy - y) / (double) (cy - tip) : 0);
                    int ink =
                            index == 0
                                    ? (int)
                                            Math.round(
                                                    250
                                                            - (250 - shade)
                                                                    * Math.exp(
                                                                            -Math.pow(x - axis, 2)
                                                                                    / 2))
                                    : Math.abs(x - sx) <= 1 ? 100 : 250;
                    g[y * W + x] = (byte) Math.min(g[y * W + x] & 255, ink);
                    if (index == 1 && Math.abs(x - sx) <= 1) l[y * W + x] = 1;
                    if (index == 0
                            && Math.abs(x - axis) < .6
                            && (mask == 1 && y > cy - 13 || mask == 2 && y < tip + 8))
                        l[y * W + x] = 1;
                }
            if (kind == 3 && index == 0)
                for (int y = 142; y < 160; y++)
                    for (int x = 157; x < 179; x++) g[y * W + x] = (byte) 225;
            for (int y = cy - 7; y <= cy + 7; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if (Math.pow((x - cx) / 10., 2) + Math.pow((y - cy) / 7., 2) <= 1) {
                        g[y * W + x] = 80;
                        l[y * W + x] = 2;
                    }
        }
        for (int b = 0; b < rails; b++)
            for (int x = 135; x <= 166; x++)
                for (int dy = 0; dy < 7; dy++) {
                    if (kind == 1 && x >= 146 && x <= 154) continue;
                    int y = 124 + Math.round((166 - x) * 5 / 31f) + b * 10 + dy;
                    g[y * W + x] = (byte) beamInk;
                }
        Class<?> r = OmrScoreInterpreter.class,
                c = Class.forName(r.getName() + "$Component"),
                st = Class.forName(r.getName() + "$Staff");
        var hc =
                c.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        hc.setAccessible(true);
        Object a = hc.newInstance(210, 150, 170, 163, 177, 160f, 170f),
                b = hc.newInstance(210, 115, 135, 170, 184, 125f, 177f);
        var sc = st.getDeclaredConstructor(float.class, float.class, float.class);
        sc.setAccessible(true);
        var m =
                r.getDeclaredMethod(
                        "detectBeamCount",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        st,
                        List.class);
        m.setAccessible(true);
        return (int)
                m.invoke(null, l, g, W, H, a, sc.newInstance(157f, 214f, 14.25f), List.of(a, b));
    }

    @Test
    public void noSemanticShaftStillRetainsSixteenth() throws Exception {
        assertEquals(2, decoded(235, 140, 0, 2, 0));
    }

    @Test
    public void clippedSemanticBaseStillRetainsSixteenth() throws Exception {
        assertEquals(2, decoded(235, 140, 1, 2, 0));
    }

    @Test
    public void clippedSemanticTipStillRetainsSixteenth() throws Exception {
        assertEquals(2, decoded(235, 140, 2, 2, 0));
    }

    @Test
    public void stillFainterShaftRetainsSixteenth() throws Exception {
        assertEquals(2, decoded(240, 100, 0, 2, 0));
    }

    @Test
    public void bareQuarterDoesNotBorrowBeams() throws Exception {
        assertEquals(0, decoded(235, 140, 0, 0, 0));
    }

    @Test
    public void brokenRailCannotJoinThePair() throws Exception {
        assertTrue(decoded(235, 140, 0, 2, 1) < 2);
    }

    @Test
    public void detachedShaftCannotBorrowRails() throws Exception {
        assertTrue(decoded(235, 140, 0, 2, 2) < 2);
    }

    @Test
    public void broadGrayCannotProveTheBody() throws Exception {
        assertTrue(decoded(235, 140, 0, 2, 3) < 2);
    }

    @Test
    public void missingCompanionCannotProveThePair() throws Exception {
        assertTrue(decoded(235, 140, 0, 2, 4) < 2);
    }

    @Test
    public void oppositeCompanionCannotProveThePair() throws Exception {
        assertTrue(decoded(235, 140, 0, 2, 5) < 2);
    }

    @Test
    public void singleRailCannotBecomeSixteenth() throws Exception {
        assertTrue(decoded(235, 140, 0, 1, 0) < 2);
    }
}
