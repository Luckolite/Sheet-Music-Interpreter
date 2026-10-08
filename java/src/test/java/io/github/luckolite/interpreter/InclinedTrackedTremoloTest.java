// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original blurred rails transformed by a bounded page shear; no score pixels. */
public final class InclinedTrackedTremoloTest {
    private static final int W = 480, H = 240, G = 16, X = 220;

    private int count(boolean complete, float pageSlope, float strokeSlope, int paper)
            throws Exception {
        byte[] flat = new OccludedFrameTremoloTest().image(complete, strokeSlope, paper);
        byte[] p = new byte[flat.length];
        Arrays.fill(p, (byte) paper);
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) {
                int yy = y + Math.round(pageSlope * (x - X));
                if (yy >= 0 && yy < H) p[yy * W + x] = flat[y * W + x];
            }
        Class<?> c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var hc =
                c.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        hc.setAccessible(true);
        int shift = Math.round(pageSlope * 21);
        Object head =
                hc.newInstance(
                        286,
                        X,
                        X + 21,
                        82 + Math.min(0, shift),
                        94 + Math.max(0, shift),
                        230f,
                        88f + pageSlope * 10);
        Class<?> st = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var sc = st.getDeclaredConstructor(float.class, float.class, float.class);
        sc.setAccessible(true);
        Object staff = sc.newInstance(30f, 94f, (float) G);
        Field tf = st.getDeclaredField("pitchTrack");
        tf.setAccessible(true);
        tf.set(staff, StaffPitchTrack.linear(W, 144 + pageSlope * (W * .5f - X), G, pageSlope));
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "tremoloStrokeCounts",
                        byte[].class,
                        int.class,
                        int.class,
                        c,
                        st,
                        List.class);
        m.setAccessible(true);
        byte[] before = p.clone();
        int result = ((int[]) m.invoke(null, p, W, H, head, staff, List.of(head)))[0];
        assertArrayEquals(before, p);
        return result;
    }

    @Test
    public void risingPrintedRuleUsesTrackedPhase() throws Exception {
        assertEquals(0, count(true, .12f, 0, 140));
    }

    @Test
    public void fallingPrintedRuleUsesTrackedPhase() throws Exception {
        assertEquals(0, count(true, -.12f, 0, 140));
    }

    @Test
    public void genuineCrossStrokeOnRisingPageSurvives() throws Exception {
        assertEquals(1, count(true, .12f, .2f, 140));
    }

    @Test
    public void genuineCrossStrokeOnFallingPageSurvives() throws Exception {
        assertEquals(1, count(true, -.12f, -.2f, 140));
    }

    @Test
    public void shallowIndependentCrossStrokeSurvives() throws Exception {
        assertEquals(1, count(true, .12f, .1f, 140));
    }

    @Test
    public void incompleteFrameCannotEraseInk() throws Exception {
        assertEquals(1, count(false, .12f, 0, 140));
    }

    @Test
    public void brightPaperKeepsLegacyStrokeDecision() throws Exception {
        assertEquals(1, count(true, .12f, 0, 250));
    }
}
