// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Existing SIL-OFL Bravura renderer, independently fixed phase/scales; no private score data. */
public class CompleteFourThreeContradictionTest {
    static boolean crossbar(CurledTupletThreeTest.Raster r) throws Exception {
        int area = 0;
        for (int y = 0; y < r.height(); y++)
            for (int x = 0; x < r.width(); x++)
                if ((r.gray()[(r.top() + y) * 100 + r.left() + x] & 255) <= 165) area++;
        var m =
                TripletRhythmDetector.class.getDeclaredMethod(
                        "completeFourCrossbar",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        return (boolean)
                m.invoke(null, r.gray(), 100, area, r.left(), r.top(), r.width(), r.height());
    }

    @Test
    public void completePrintedFoursCannotBecomeThreesAcrossIndependentScalesPhases()
            throws Exception {
        for (int size : new int[] {16, 20, 24})
            for (float phase : new float[] {0, .5f})
                for (float shear : new float[] {-.15f, .15f})
                    for (float narrow : new float[] {.8f, 1f}) {
                        var r = CurledTupletThreeTest.glyph(4, size, phase, shear, narrow);
                        assertFalse(
                                "printed4 size="
                                        + size
                                        + " phase="
                                        + phase
                                        + " shear="
                                        + shear
                                        + " narrow="
                                        + narrow,
                                CurledTupletThreeTest.match(r));
                    }
    }

    @Test
    public void completeFourContradictionIsNonvacuous() throws Exception {
        assertTrue(crossbar(CurledTupletThreeTest.glyph(4, 20, 0, 0, 1)));
    }

    @Test
    public void missingMiddleStrokeCannotSupplyFourContradiction() throws Exception {
        var r = CurledTupletThreeTest.glyph(4, 20, 0, 0, 1);
        for (int y = Math.round(r.height() * .36f); y <= Math.round(r.height() * .74f); y++)
            for (int x = 0; x < r.width(); x++)
                r.gray()[(r.top() + y) * 100 + r.left() + x] = (byte) 255;
        assertFalse(crossbar(r));
    }
}
