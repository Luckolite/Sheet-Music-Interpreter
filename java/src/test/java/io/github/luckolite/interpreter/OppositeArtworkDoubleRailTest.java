// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original rectangles/ellipses; an unrelated opposite ink column competes with owned beams. */
public final class OppositeArtworkDoubleRailTest {
    static final int W = 340, H = 330;

    private int result(boolean mirror, int beams, int kind) throws Exception {
        byte[] g = new byte[W * H], l = new byte[W * H];
        Arrays.fill(g, (byte) 250);
        for (int i = 0; i < 2; i++) {
            int cx = 110 + i * 55, cy = 200 + i * 8, sx = cx - 10, tip = 270 + i * 7;
            for (int y = cy; y <= tip; y++)
                for (int x = sx - 1; x <= sx + 1; x++) {
                    if (kind == 2 && i == 0 && y > 225 && y < 235 || kind == 4 && i == 1) continue;
                    int yy = mirror ? H - 1 - y : y;
                    g[yy * W + x] = 50;
                    if (!(kind == 5 && i == 0)) l[yy * W + x] = 1;
                }
            if (kind == 3 && i == 0)
                for (int y = 210; y <= 269; y++)
                    for (int x = sx - 12; x <= sx + 12; x++)
                        g[(mirror ? H - 1 - y : y) * W + x] = 90;
            if (kind == 6 && i == 1)
                for (int y = cy - 60; y <= cy; y++)
                    for (int x = cx + 9; x <= cx + 11; x++) {
                        int yy = mirror ? H - 1 - y : y;
                        g[yy * W + x] = 50;
                        l[yy * W + x] = 1;
                    }
            for (int y = cy - 7; y <= cy + 7; y++)
                for (int x = cx - 10; x <= cx + 10; x++)
                    if (Math.pow((x - cx) / 10., 2) + Math.pow((y - cy) / 7., 2) <= 1) {
                        int yy = mirror ? H - 1 - y : y;
                        g[yy * W + x] = 40;
                        l[yy * W + x] = 2;
                    }
        }
        // No semantic shaft labels on the unrelated opposite column.
        if (kind != 7)
            for (int y = 55; y <= 200; y++)
                for (int x = 119; x <= 121; x++) g[(mirror ? H - 1 - y : y) * W + x] = 70;
        for (int b = 0; b < beams; b++)
            for (int x = 100; x <= 155; x++)
                for (int dy = 0; dy < 7; dy++) {
                    if (kind == 1 && x >= 122 && x <= 131) continue;
                    int y = 264 + Math.round((x - 100) * 7 / 55f) - b * 12 + dy,
                            yy = mirror ? H - 1 - y : y;
                    g[yy * W + x] = 25;
                    l[yy * W + x] = 5;
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
        Object
                a =
                        hc.newInstance(
                                210,
                                100,
                                120,
                                mirror ? H - 1 - 207 : 193,
                                mirror ? H - 1 - 193 : 207,
                                110f,
                                (float) (mirror ? H - 1 - 200 : 200)),
                b =
                        hc.newInstance(
                                210,
                                155,
                                175,
                                mirror ? H - 1 - 215 : 201,
                                mirror ? H - 1 - 201 : 215,
                                165f,
                                (float) (mirror ? H - 1 - 208 : 208));
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
                m.invoke(
                        null,
                        l,
                        g,
                        W,
                        H,
                        a,
                        sc.newInstance(mirror ? 81f : 184f, mirror ? 145f : 248f, 16f),
                        List.of(a, b));
    }

    @Test
    public void ownDownRailsSurviveOppositeArtwork() throws Exception {
        assertEquals(2, result(false, 2, 0));
    }

    @Test
    public void ownUpRailsSurviveOppositeArtwork() throws Exception {
        assertEquals(2, result(true, 2, 0));
    }

    @Test
    public void missingSemanticOwnershipIsInsufficient() throws Exception {
        assertTrue(result(false, 2, 5) < 2);
    }

    @Test
    public void brokenRailsCannotBeBorrowed() throws Exception {
        assertTrue(result(false, 2, 1) < 2);
    }

    @Test
    public void detachedShaftCannotBorrowRails() throws Exception {
        assertTrue(result(false, 2, 2) < 2);
    }

    @Test
    public void broadInkCannotSupplyNarrowBody() throws Exception {
        assertTrue(result(false, 2, 3) < 2);
    }

    @Test
    public void missingCompanionCannotSupplyPair() throws Exception {
        assertTrue(result(false, 2, 4) < 2);
    }

    @Test
    public void opposingCompanionVoicesStaySeparate() throws Exception {
        assertTrue(result(false, 2, 6) < 2);
    }

    @Test
    public void genuineBareQuarterStaysUnbeamed() throws Exception {
        assertEquals(0, result(false, 0, 0));
    }

    @Test
    public void oneRailDoesNotBecomeSixteenth() throws Exception {
        assertTrue(result(false, 1, 0) < 2);
    }

    @Test
    public void unambiguousOrdinaryBeamsRemainTwo() throws Exception {
        assertEquals(2, result(false, 2, 7));
    }
}
