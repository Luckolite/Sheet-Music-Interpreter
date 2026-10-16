// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original separate curved bands sharing one erroneous semantic island. */
public class NestedCurveHeadTest {
    static final int W = 400, H = 300;
    byte[] g = new byte[W * H];

    public NestedCurveHeadTest() {
        Arrays.fill(g, (byte) 255);
    }

    void arc(boolean lower) {
        int l = lower ? 100 : 110, r = lower ? 176 : 170;
        for (int x = l; x <= r; x++) {
            double t = (x - l) / (double) (r - l);
            int y =
                    (int)
                            Math.round(
                                    lower
                                            ? 162 + 8 * 4 * t * (1 - t) - 18 * t
                                            : 140 + 12 * 4 * t * (1 - t));
            for (int dy = -1; dy <= 1; dy++) g[(y + dy) * W + x] = 0;
        }
    }

    boolean read() throws Exception {
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object head = ctor.newInstance(80, 155, 171, 144, 158, 163f, 151f);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "flatStemlessFragment", byte[].class, int.class, int.class, c, float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, g, W, H, head, 16f);
    }

    @Test
    public void separateCurvesExplainMergedIsland() throws Exception {
        arc(false);
        arc(true);
        assertTrue(read());
    }

    @Test
    public void singleCurveIsNotNestedProof() throws Exception {
        arc(false);
        assertFalse(read());
    }

    @Test
    public void noRawInkIsNotProof() throws Exception {
        assertFalse(read());
    }

    @Test
    public void hollowHeadRemainsNote() throws Exception {
        for (int y = 138; y <= 154; y++)
            for (int x = 151; x <= 175; x++) {
                double r = Math.pow((x - 163) / 12., 2) + Math.pow((y - 146) / 8., 2);
                if (r <= 1 && r >= .55) g[y * W + x] = 0;
            }
        assertFalse(read());
    }

    @Test
    public void filledHeadRemainsNote() throws Exception {
        for (int y = 138; y <= 154; y++)
            for (int x = 151; x <= 175; x++)
                if (Math.pow((x - 163) / 12., 2) + Math.pow((y - 146) / 8., 2) <= 1)
                    g[y * W + x] = 0;
        assertFalse(read());
    }

    @Test
    public void sourcePixelsUnchanged() throws Exception {
        arc(false);
        arc(true);
        byte[] old = g.clone();
        read();
        assertArrayEquals(old, g);
    }

    @Test
    public void horizontallyMirroredCurvesRemainRecognizable() throws Exception {
        arc(false);
        arc(true);
        byte[] old = g.clone();
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if (326 - x >= 0 && 326 - x < W) g[y * W + 326 - x] = old[y * W + x];
        assertTrue(read());
    }

    @Test
    public void upperNestedCurvesRemainRecognizable() throws Exception {
        arc(false);
        arc(true);
        byte[] old = g.clone();
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if (302 - y >= 0 && 302 - y < H) g[(302 - y) * W + x] = old[y * W + x];
        assertTrue(read());
    }

    @Test
    public void staffSplitHollowNoteIsNotTwoSlurs() throws Exception {
        for (int y = 139; y <= 163; y++)
            for (int x = 141; x <= 185; x++) {
                double r = Math.pow((x - 163) / 22., 2) + Math.pow((y - 151) / 12., 2);
                if (r <= 1 && r >= .65) g[y * W + x] = 0;
            }
        for (int x = 0; x < W; x++) g[151 * W + x] = 0;
        assertFalse(read());
    }

    @Test
    public void attachedStemPreservesCandidate() throws Exception {
        arc(false);
        arc(true);
        for (int y = 105; y <= 155; y++) for (int x = 169; x <= 171; x++) g[y * W + x] = 0;
        assertFalse(read());
    }
}
