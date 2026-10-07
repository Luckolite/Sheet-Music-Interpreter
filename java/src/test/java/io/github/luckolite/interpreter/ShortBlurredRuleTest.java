// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-row rule with a five-row local blur beside a stem probe. */
public class ShortBlurredRuleTest {
    private static final int W = 400, H = 180;

    private byte[][] page(boolean finiteBeam) {
        byte[] l = new byte[W * H], g = new byte[W * H];
        Arrays.fill(g, (byte) 240);
        for (int y = 30; y <= 94; y += 16)
            for (int dy = -1; dy <= 0; dy++)
                for (int x = 20; x < 380; x++) {
                    l[(y + dy) * W + x] = 4;
                    g[(y + dy) * W + x] = 70;
                }
        for (int y = 77; y <= 80; y++) for (int x = 194; x <= 198; x++) g[y * W + x] = 70;
        for (int y = 76; y <= 80; y++) for (int x = 199; x <= 201; x++) g[y * W + x] = 70;
        if (finiteBeam)
            for (int y = 75; y <= 82; y++) for (int x = 140; x <= 260; x++) g[y * W + x] = 20;
        return new byte[][] {l, g};
    }

    private int count(byte[][] p) throws Exception {
        var s = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var c = s.getDeclaredConstructor(float.class, float.class, float.class);
        c.setAccessible(true);
        var staff = c.newInstance(30f, 94f, 16f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "thickNonHeadBands",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        s,
                        int.class);
        m.setAccessible(true);
        return (int) m.invoke(null, p[1], p[0], W, H, 200, 70, 87, staff, 196);
    }

    @Test
    public void localBlurDoesNotAddAnotherBeam() throws Exception {
        assertEquals(0, count(page(false)));
    }

    @Test
    public void finiteBeamOnTheRuleRemainsABeam() throws Exception {
        assertEquals(1, count(page(true)));
    }

    @Test
    public void preservesSourceRaster() throws Exception {
        var p = page(false);
        byte[] a = p[0].clone(), b = p[1].clone();
        count(p);
        assertArrayEquals(a, p[0]);
        assertArrayEquals(b, p[1]);
    }
}
