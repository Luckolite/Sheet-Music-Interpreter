// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original blurred rule with two left reference rails obscured by neighboring notation. */
public class OccludedFrameTremoloTest {
    static final int W = 480, H = 240, G = 16, X = 220, TOP = 80;

    byte[] image(boolean complete, float slope, int paper) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        for (int cy = TOP; cy <= TOP + 4 * G; cy += G) {
            if (!complete && cy != 112) continue;
            for (int x = 20; x < W - 20; x++)
                for (int dy = -1; dy <= 1; dy++) p[(cy + dy) * W + x] = 60;
        }
        for (int cy : new int[] {80, 144})
            for (int x = 166; x <= 195; x++)
                for (int dy = -3; dy <= 3; dy++) p[(cy + dy) * W + x] = (byte) paper;
        int[] blur = {100, 80, 65, 80, 100};
        for (int x = X - 14; x <= X + 14; x++)
            for (int dy = -2; dy <= 2; dy++) p[(112 + dy) * W + x] = (byte) blur[dy + 2];
        for (int y = 82; y <= 142; y++) p[y * W + X] = 20;
        for (int y = 82; y <= 94; y++) for (int x = X; x <= X + 21; x++) p[y * W + x] = 20;
        if (slope != 0)
            for (int x = X - 14; x <= X + 14; x++)
                for (int dy = -2; dy <= 2; dy++)
                    p[(112 + Math.round((x - X) * slope) + dy) * W + x] = 20;
        return p;
    }

    int count(byte[] p) throws Exception {
        var c = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
        var ctor =
                c.getDeclaredConstructor(
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        ctor.setAccessible(true);
        Object head = ctor.newInstance(286, X, X + 21, 82, 94, 230f, 88f);
        var st = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var sc = st.getDeclaredConstructor(float.class, float.class, float.class);
        sc.setAccessible(true);
        Object staff = sc.newInstance((float) TOP, (float) (TOP + 4 * G), (float) G);
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
        return ((int[]) m.invoke(null, p, W, H, head, staff, List.of(head)))[0];
    }

    @Test
    public void oneProvedFrameAndLevelWingsIdentifyBlurredRule() throws Exception {
        assertEquals(0, count(image(true, 0, 140)));
    }

    @Test
    public void actualRisingStrokeSurvivesOccludedReferences() throws Exception {
        assertEquals(1, count(image(true, .2f, 140)));
    }

    @Test
    public void actualFallingStrokeSurvivesOccludedReferences() throws Exception {
        assertEquals(1, count(image(true, -.2f, 140)));
    }

    @Test
    public void shallowActualStrokeStillHasIndependentInclination() throws Exception {
        assertEquals(1, count(image(true, .1f, 140)));
    }

    @Test
    public void incompleteFrameCannotEraseHorizontalInk() throws Exception {
        assertEquals(1, count(image(false, 0, 140)));
    }

    @Test
    public void brightPaperRetainsExistingHorizontalInkDecision() throws Exception {
        assertEquals(1, count(image(true, 0, 250)));
    }

    @Test
    public void rawImageRemainsUnchanged() throws Exception {
        var p = image(true, 0, 140);
        var old = p.clone();
        count(p);
        assertArrayEquals(old, p);
    }
}
