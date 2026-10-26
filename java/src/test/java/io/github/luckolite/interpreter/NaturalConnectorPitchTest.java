// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original thick/thin centered connector bands, independent of prior key context. */
public final class NaturalConnectorPitchTest {
    static void rect(byte[] p, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) p[yy * 100 + xx] = 3;
    }

    static float center(int thickness, int shift) throws Exception {
        byte[] p = new byte[10000];
        rect(p, 25, 18 + shift, 2, 30);
        rect(p, 35, 30 + shift, 2, 30);
        for (int cy : new int[] {32 + shift, 46 + shift})
            rect(p, 25, cy - thickness / 2, 12, thickness);
        var f =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "findComponents", byte[].class, int.class, int.class, byte.class);
        f.setAccessible(true);
        List<?> cs = (List<?>) f.invoke(null, p, 100, 100, (byte) 3);
        assertEquals(1, cs.size());
        Object c = cs.get(0);
        var ac =
                Class.forName(OmrScoreInterpreter.class.getName() + "$AccidentalCandidate")
                        .getDeclaredConstructors()[0];
        ac.setAccessible(true);
        Object a = ac.newInstance(c, (byte) 3);
        var bool =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "isNaturalGlyph",
                        byte[].class,
                        int.class,
                        int.class,
                        a.getClass(),
                        float.class);
        bool.setAccessible(true);
        assertEquals(true, bool.invoke(null, p, 100, 100, a, 14f));
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "naturalGlyphPitch",
                        byte[].class,
                        int.class,
                        int.class,
                        a.getClass(),
                        float.class);
        m.setAccessible(true);
        return (float) m.invoke(null, p, 100, 100, a, 14f);
    }

    @Test
    public void onePixelBandsKeepDrawnMusicalCenter() throws Exception {
        assertEquals(39f, center(1, 0), .5f);
    }

    @Test
    public void thickBandsKeepDrawnMusicalCenter() throws Exception {
        assertEquals(39f, center(7, 0), .5f);
    }

    @Test
    public void veryThickBandsDoNotUseFirstAcceptedRow() throws Exception {
        assertEquals(39f, center(11, 0), .5f);
    }

    @Test
    public void coordinateIsNativeAndTranslationCovariant() throws Exception {
        assertEquals(46f, center(7, 7), .5f);
    }
}
