// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently drawn stem, staff and beam intersections; no score-derived pixels. */
public class PartialBeamNumeralTest {
    static final int W = 300, H = 250;

    byte[] paper() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        return g;
    }

    void rect(byte[] g, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) g[yy * W + xx] = 0;
    }

    boolean visible(byte[] g) throws Exception {
        Method m =
                RepeatedTupletNumeral.class.getDeclaredMethod(
                        "visibleBeyondLongStem",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        boolean[].class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, g, W, H, 100, 100, 12, 22, 14f, new boolean[22]);
    }

    void stem(byte[] g) {
        rect(g, 108, 80, 1, 60);
    }

    @Test
    public void oneSidedDoubleBeamCannotSupplyNumeralInk() throws Exception {
        byte[] g = paper();
        stem(g);
        rect(g, 80, 104, 61, 1);
        rect(g, 108, 105, 45, 5);
        rect(g, 108, 116, 45, 5);
        assertFalse(visible(g));
    }

    @Test
    public void slopedOneSidedBeamsCannotSupplyNumeralInk() throws Exception {
        byte[] g = paper();
        stem(g);
        for (int x = 108; x < 155; x++) {
            int rise = (x - 108) / 5;
            rect(g, x, 104 - rise, 1, 5);
            rect(g, x, 116 - rise, 1, 5);
        }
        assertFalse(visible(g));
    }

    @Test
    public void isolatedShaftHasNoNumeralInk() throws Exception {
        byte[] g = paper();
        stem(g);
        assertFalse(visible(g));
    }

    @Test
    public void genuineLobesBesideShaftRemainVisible() throws Exception {
        byte[] g = paper();
        stem(g);
        rect(g, 102, 100, 6, 2);
        rect(g, 106, 102, 2, 7);
        rect(g, 102, 110, 6, 2);
        rect(g, 106, 112, 2, 7);
        rect(g, 102, 120, 6, 2);
        assertTrue(visible(g));
    }
}
