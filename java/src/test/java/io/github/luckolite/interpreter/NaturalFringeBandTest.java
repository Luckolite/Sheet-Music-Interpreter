// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original offset stems with localized side bulges and sharp controls. */
public class NaturalFringeBandTest {
    private final int w = 100, h = 100;
    private final byte[] labels = new byte[w * h];

    private void rect(int x, int y, int ww, int hh) {
        for (int yy = y; yy < y + hh; yy++)
            for (int xx = x; xx < x + ww; xx++) labels[yy * w + xx] = 3;
    }

    private boolean read() throws Exception {
        var component =
                Class.forName(OmrScoreInterpreter.class.getName() + "$Component")
                        .getDeclaredConstructors()[0];
        component.setAccessible(true);
        int area = 0, x0 = w, x1 = 0, y0 = h, y1 = 0;
        long sx = 0, sy = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (labels[y * w + x] != 0) {
                    area++;
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                    sx += x;
                    sy += y;
                }
        Object c =
                component.newInstance(area, x0, x1, y0, y1, sx / (float) area, sy / (float) area);
        var candidate =
                Class.forName(OmrScoreInterpreter.class.getName() + "$AccidentalCandidate")
                        .getDeclaredConstructors()[0];
        candidate.setAccessible(true);
        Object a = candidate.newInstance(c, (byte) 3);
        for (var m : OmrScoreInterpreter.class.getDeclaredMethods())
            if (m.getName().equals("isNaturalGlyph")) {
                m.setAccessible(true);
                return (boolean) m.invoke(null, labels, w, h, a, 16f);
            }
        throw new AssertionError();
    }

    private void natural() {
        rect(30, 20, 3, 30);
        rect(39, 30, 3, 28);
        rect(30, 31, 12, 4);
        rect(30, 46, 12, 4);
    }

    @Test
    public void localizedOppositeBulgesPreserveNatural() throws Exception {
        natural();
        rect(28, 37, 4, 3);
        rect(41, 46, 3, 3);
        assertTrue(read());
    }

    @Test
    public void localizedSameHeightBulgesPreserveNatural() throws Exception {
        natural();
        rect(28, 46, 4, 3);
        rect(41, 46, 3, 3);
        assertTrue(read());
    }

    @Test
    public void twoOuterCrossbarsRemainRejected() throws Exception {
        natural();
        rect(28, 31, 16, 4);
        rect(28, 46, 16, 4);
        assertFalse(read());
    }

    @Test
    public void longOuterSideIsNotScanFringe() throws Exception {
        natural();
        rect(28, 37, 4, 3);
        rect(41, 34, 3, 15);
        assertFalse(read());
    }

    @Test
    public void alignedSpinesStillRejectNatural() throws Exception {
        natural();
        rect(39, 20, 3, 38);
        rect(30, 20, 3, 38);
        rect(28, 37, 4, 3);
        rect(41, 46, 3, 3);
        assertFalse(read());
    }

    @Test
    public void missingLowerConnectorStillRejectsNatural() throws Exception {
        rect(30, 20, 3, 30);
        rect(39, 30, 3, 28);
        rect(30, 31, 12, 4);
        rect(28, 37, 4, 3);
        rect(41, 46, 3, 3);
        assertFalse(read());
    }
}
