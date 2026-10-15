// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PaleHeaderFlatTest {
    final int W = 160, H = 150;
    final byte[] g = new byte[W * H];

    public PaleHeaderFlatTest() {
        Arrays.fill(g, (byte) 255);
    }

    void rect(int x, int y, int w, int h, int v) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) g[yy * W + xx] = (byte) v;
    }

    void flat(int spine, int bowl) {
        rect(60, 50, 3, 41, spine);
        for (int y = 69; y <= 87; y++)
            for (int x = 61; x <= 74; x++) {
                double r = Math.pow((x - 63) / 11d, 2) + Math.pow((y - 78) / 9d, 2);
                if (r >= .4 && r <= 1) g[y * W + x] = (byte) bowl;
            }
    }

    boolean read() {
        return PrintedFlatGlyph.matches(g, W, H, 60, 50, 74, 90, 16);
    }

    @Test
    public void paleSpineDarkBowl() {
        flat(210, 0);
        byte[] before = g.clone();
        assertTrue(read());
        assertArrayEquals(before, g);
    }

    @Test
    public void allPaleRequiresDarkBowl() {
        flat(210, 210);
        assertFalse(read());
    }

    @Test
    public void disconnectedBowlIsNotFlat() {
        flat(255, 0);
        assertFalse(read());
    }

    @Test
    public void paleStemWithoutBowl() {
        rect(60, 50, 3, 41, 210);
        assertFalse(read());
    }

    @Test
    public void paleNaturalRetainsSecondStem() {
        rect(60, 50, 3, 32, 210);
        rect(71, 60, 3, 31, 210);
        rect(60, 60, 14, 3, 0);
        rect(60, 79, 14, 3, 0);
        assertFalse(read());
    }

    @Test
    public void paleSharpRetainsBothSpines() {
        rect(60, 50, 3, 41, 210);
        rect(71, 50, 3, 41, 210);
        rect(57, 62, 21, 3, 0);
        rect(57, 77, 21, 3, 0);
        assertFalse(read());
    }
}
