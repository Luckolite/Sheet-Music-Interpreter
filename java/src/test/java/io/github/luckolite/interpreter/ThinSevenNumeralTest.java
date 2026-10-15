// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Generated top bar and diagonal, independent of private score pixels. */
public class ThinSevenNumeralTest {
    byte[] glyph(int scale, boolean top, boolean reverse, boolean vertical) {
        int w = 11 * scale, h = 19 * scale;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < h; y++) {
            if (y < 2 * scale && top) {
                for (int x = 0; x < w; x++) g[y * w + x] = 0;
            } else {
                int x =
                        vertical
                                ? w / 2
                                : Math.round(
                                        (w - 1) * (1f - (y - 2f * scale) / (h - 1 - 2f * scale)));
                x = Math.max(0, Math.min(w - 1, reverse ? w - 1 - x : x));
                for (int dx = 0; dx < (scale == 1 ? 1 : 3); dx++)
                    if (x + dx < w) g[y * w + x + dx] = 0;
            }
        }
        return g;
    }

    boolean read(byte[] g, int s) throws Exception {
        var m =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "looksLikeSeven",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, g, 11 * s, 0, 0, 11 * s, 19 * s);
    }

    @Test
    public void narrowContinuousDiagonalRemainsSeven() throws Exception {
        assertTrue(read(glyph(1, true, false, false), 1));
    }

    @Test
    public void largerThickDiagonalRemainsSeven() throws Exception {
        assertTrue(read(glyph(2, true, false, false), 2));
    }

    @Test
    public void diagonalWithoutTopIsNotSeven() throws Exception {
        assertFalse(read(glyph(1, false, false, false), 1));
    }

    @Test
    public void reverseDiagonalIsNotSeven() throws Exception {
        assertFalse(read(glyph(1, true, true, false), 1));
    }

    @Test
    public void verticalStemIsNotSeven() throws Exception {
        assertFalse(read(glyph(1, true, false, true), 1));
    }

    @Test
    public void filledBlockIsNotSeven() throws Exception {
        assertFalse(read(new byte[11 * 19], 1));
    }
}
