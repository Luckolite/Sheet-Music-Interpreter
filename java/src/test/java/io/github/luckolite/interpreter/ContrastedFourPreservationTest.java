// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent licensed-font source glyphs: fixed phase/scale cohort, no score pixels. */
public class ContrastedFourPreservationTest {
    static Object acquire(TupletConsensusFont.Raster r) throws Exception {
        byte[] g = new byte[400 * 280];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < r.height(); y++)
            for (int x = 0; x < r.width(); x++)
                g[(70 + y) * 400 + 128 + x] = r.gray()[(r.top() + y) * 100 + r.left() + x];
        for (int yy : new int[] {105, 106, 107, 108, 96, 97, 98, 99})
            for (int xx = 108; xx <= 178; xx++) g[yy * 400 + xx] = 0;
        for (int xx : new int[] {108, 143, 178})
            for (int yy = 96; yy <= 152; yy++) g[yy * 400 + xx] = 0;
        byte[] before = g.clone();
        var m =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "findContrastedNumeral",
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        boolean.class,
                        float.class,
                        float.class,
                        int.class,
                        boolean.class);
        m.setAccessible(true);
        Object out =
                m.invoke(
                        null, g, 400, 280, 100f, 170f, 150f, 150f, 18f, true, Float.NaN, Float.NaN,
                        3, false);
        assertArrayEquals(before, g);
        return out;
    }

    @Test
    public void fullOriginalFourCannotBeReopenedByThresholdConsensus() throws Exception {
        for (int size : new int[] {16, 20, 24})
            for (float phase : new float[] {0, .5f})
                for (float shear : new float[] {0, .15f})
                    for (float narrow : new float[] {.8f, 1f})
                        assertNull(
                                "four size="
                                        + size
                                        + " phase="
                                        + phase
                                        + " shear="
                                        + shear
                                        + " narrow="
                                        + narrow,
                                acquire(TupletConsensusFont.glyph(4, size, phase, shear, narrow)));
    }

    @Test
    public void weakCrossbarStillBelongsToCompleteOriginalFour() throws Exception {
        for (int size : new int[] {16, 20, 24})
            for (float phase : new float[] {0, .5f})
                for (float shear : new float[] {0, .15f})
                    for (float narrow : new float[] {.8f, 1f}) {
                        var r = TupletConsensusFont.glyph(4, size, phase, shear, narrow);
                        for (int y = 0; y < r.height(); y++)
                            for (int x = 0; x < r.width(); x++) {
                                int i = (r.top() + y) * 100 + r.left() + x;
                                if (y >= r.height() * .35f
                                        && y <= r.height() * .70f
                                        && x <= r.width() * .65f
                                        && (r.gray()[i] & 255) <= 155) r.gray()[i] = (byte) 155;
                            }
                        assertNull(
                                "weak complete4 size="
                                        + size
                                        + " phase="
                                        + phase
                                        + " shear="
                                        + shear
                                        + " narrow="
                                        + narrow,
                                acquire(r));
                    }
    }

    @Test
    public void genuineThreeRetainsContrastedCaller() throws Exception {
        assertNotNull(acquire(TupletConsensusFont.glyph(3, 20, 0, 0, 1)));
    }

    @Test
    public void otherCompleteNumeralsStayRejected() throws Exception {
        for (int digit : new int[] {2, 5, 8})
            assertNull(acquire(TupletConsensusFont.glyph(digit, 20, 0, 0, 1)));
    }
}
