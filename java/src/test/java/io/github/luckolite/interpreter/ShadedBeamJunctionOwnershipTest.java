// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original single sloping beam, with a small semantic island on its owning shaft. */
public class ShadedBeamJunctionOwnershipTest {
    static final int W = 440, H = 300;
    static final float G = 16;
    byte[] g;
    Object tiny, main, staff;

    Object component(int area, int l, int r, int t, int b, float x, float y) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(area, l, r, t, b, x, y);
    }

    void rect(int l, int r, int t, int b, int shade) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = (byte) shade;
    }

    void draw(boolean white, boolean oval, boolean broken, boolean up) throws Exception {
        g = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) g[y * W + x] = (byte) (white ? 250 : 126 + x / 50);
        for (int y = 144; y <= 208; y += 16) rect(30, 400, y, y, 55);
        for (int y = 94; y <= 106; y++)
            for (int x = 140; x <= 160; x++)
                if (Math.pow((x - 150) / 10., 2) + Math.pow((y - 100) / 6., 2) <= 1)
                    g[y * W + x] = 20;
        rect(140, 142, 100, 160, 40);
        for (int x = 140; x <= 230; x++) {
            int y = Math.round(160 - (x - 140) * .2f);
            rect(x, x, y - 2, y + 2, 20);
        }
        if (oval) {
            for (int y = 150; y <= 170; y++)
                for (int x = 141; x <= 161; x++)
                    if (Math.pow((x - 151) / 10., 2) + Math.pow((y - 160) / 10., 2) <= 1)
                        g[y * W + x] = 20;
        }
        if (broken) rect(138, 144, 120, 136, white ? 250 : 130);
        main = component(185, 140, 160, 94, 106, 150, up ? 200 : 100);
        tiny = component(35, 140, 146, 157, 162, 143, up ? 140 : 160);
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Staff")
                        .getDeclaredConstructor(float.class, float.class, float.class);
        c.setAccessible(true);
        staff = c.newInstance(144f, 208f, G);
        if (up) {
            byte[] mirrored = new byte[g.length];
            for (int i = 0; i < g.length; i++) mirrored[g.length - 1 - i] = g[i];
            g = mirrored;
            main = component(185, 279, 299, 193, 205, 289, 199);
            tiny = component(35, 293, 299, 137, 142, 296, 139);
            staff = c.newInstance(91f, 155f, G);
        }
    }

    int rejected(boolean owner) throws Exception {
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "beamJunctionHeads",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        List.class);
        m.setAccessible(true);
        return ((List<?>)
                        m.invoke(
                                null,
                                g,
                                W,
                                H,
                                owner ? List.of(main, tiny) : List.of(tiny),
                                List.of(staff)))
                .size();
    }

    @Test
    public void shadedDownStemCornerBelongsToTheBeam() throws Exception {
        draw(false, false, false, false);
        assertEquals(1, rejected(true));
    }

    @Test
    public void shadedUpStemCornerBelongsToTheBeam() throws Exception {
        draw(false, false, false, true);
        assertEquals(1, rejected(true));
    }

    @Test
    public void brightPrintedCornerStillBelongsToTheBeam() throws Exception {
        draw(true, false, false, false);
        assertEquals(1, rejected(true));
    }

    @Test
    public void actualOvalBulgeRetainsItsNote() throws Exception {
        draw(false, true, false, false);
        assertEquals(0, rejected(true));
    }

    @Test
    public void disconnectedShaftCannotOwnTheCorner() throws Exception {
        draw(false, false, true, false);
        assertEquals(0, rejected(true));
    }

    @Test
    public void missingFullSizedHeadCannotOwnTheCorner() throws Exception {
        draw(false, false, false, false);
        assertEquals(0, rejected(false));
    }

    @Test
    public void sourcePixelsRemainUnchanged() throws Exception {
        draw(false, false, false, false);
        var before = g.clone();
        rejected(true);
        assertArrayEquals(before, g);
    }
}
