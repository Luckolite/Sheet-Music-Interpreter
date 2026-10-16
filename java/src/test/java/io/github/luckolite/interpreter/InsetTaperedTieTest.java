// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic tapered arcs, without score-derived pixels. */
public class InsetTaperedTieTest {
    static final float[][] CASES = {{18f, 45f, 4f, .35f, .75f}, {18f, 45f, 4f, .55f, 1f}};

    static byte[] curve(
            float gap, int span, float bow, float offset, float stroke, boolean broken) {
        byte[] gray = new byte[180 * 120];
        Arrays.fill(gray, (byte) 255);
        float a = 60, b = a + span;
        for (int y = 0; y < 120; y++)
            for (int x = 0; x < 180; x++) {
                float t = (x - a) / (b - a);
                if (t < 0 || t > 1 || broken && t > .4 && t < .6) continue;
                float yy = 80 - gap * offset - 4 * bow * t * (1 - t);
                float thickness = stroke * (float) Math.sin(Math.PI * t);
                float coverage = Math.max(0, Math.min(1, thickness + .5f - Math.abs(y - yy)));
                gray[y * 180 + x] = (byte) Math.round(255 - 205 * coverage);
            }
        return gray;
    }

    static boolean detect(byte[] gray, float[] c) throws Exception {
        Method m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "hasPrintedTieArc",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        boolean.class);
        m.setAccessible(true);
        return (boolean)
                m.invoke(
                        null,
                        new byte[180 * 120],
                        gray,
                        180,
                        120,
                        40,
                        65 + (int) c[1],
                        80f,
                        c[0],
                        true);
    }

    static byte[] image(float[] c, boolean broken) {
        return curve(c[0], (int) c[1], c[2], c[3], c[4], broken);
    }

    @Test
    public void recoversTaperedArcsAcrossStaffSpacing() throws Exception {
        for (float[] c : CASES) assertTrue(Arrays.toString(c), detect(image(c, false), c));
    }

    @Test
    public void rejectsBrokenArcs() throws Exception {
        for (float[] c : CASES) assertFalse(Arrays.toString(c), detect(image(c, true), c));
    }

    @Test
    public void rejectsFlatStrokes() throws Exception {
        for (float[] c : CASES) {
            float[] flat = c.clone();
            flat[2] = 0;
            assertFalse(Arrays.toString(c), detect(image(flat, false), flat));
        }
    }

    @Test
    public void rejectsPaleArcsWithoutDarkCore() throws Exception {
        for (float[] c : CASES) {
            byte[] gray = image(c, false);
            for (int i = 0; i < gray.length; i++) gray[i] = (byte) Math.max(190, gray[i] & 255);
            assertFalse(Arrays.toString(c), detect(gray, c));
        }
    }
}
