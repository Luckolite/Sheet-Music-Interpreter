// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original mirrored curves whose returning shoulders extend beneath their head centers. */
public class UnderHeadTieShoulderTest {
    static final int O = 10, B = 14;

    private Class<?> nested(String name) {
        return Arrays.stream(OmrScoreInterpreter.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private Object note(int x, int step, float position, float scale) throws Exception {
        var event = new ScoreNoteEvent(0, position, step, 0, 1, .5f, false, 0, 1, 2, 0);
        var hc = nested("Component").getDeclaredConstructors()[0];
        hc.setAccessible(true);
        var head =
                hc.newInstance(
                        400,
                        Math.round((x - 10) * scale),
                        Math.round((x + 10) * scale),
                        Math.round(73 * scale),
                        Math.round(87 * scale),
                        x * scale,
                        80 * scale);
        var nc = nested("DetectedNote").getDeclaredConstructors()[0];
        nc.setAccessible(true);
        return nc.newInstance(event, head, 14 * scale);
    }

    private boolean detect(int mode, int side, int step, float scale) throws Exception {
        int w = Math.round(400 * scale), h = Math.round(180 * scale);
        byte[] labels = new byte[w * h], gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        for (int x = 0; x < w; x++)
            for (int y = Math.round(80 * scale) - 1; y <= Math.round(80 * scale) + 1; y++) {
                labels[y * w + x] = 4;
                gray[y * w + x] = 0;
            }
        for (int x = Math.round(96 * scale); x <= Math.round(146 * scale); x++) {
            float t = (x / scale - 96) / 50f;
            if (mode == 0 || mode == 3 && t > .5f || mode == 5 && t > .3f && t < .7f) continue;
            float bend = mode == 4 ? B * t : B * 4 * t * (1 - t);
            int yy = Math.round((80 + side * (O + bend)) * scale);
            int ink = mode == 6 ? 185 : mode == 2 && (t < .18f || t > .82f) ? 180 : 0;
            for (int y = yy - 1; y <= yy + 1; y++) {
                gray[y * w + x] = (byte) ink;
                labels[y * w + x] = 5;
            }
        }
        var mark =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "hasTieArc",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        nested("DetectedNote"),
                        nested("DetectedNote"));
        mark.setAccessible(true);
        byte[] oldGray = gray.clone(), oldLabels = labels.clone();
        boolean result =
                (boolean)
                        mark.invoke(
                                null,
                                labels,
                                gray,
                                w,
                                h,
                                note(100, 0, .2f, scale),
                                note(142, step, .3f, scale));
        assertArrayEquals(oldGray, gray);
        assertArrayEquals(oldLabels, labels);
        return result;
    }

    @Test
    public void upperReturningShouldersExtendUnderHeads() throws Exception {
        assertTrue(detect(1, -1, 0, 1));
    }

    @Test
    public void lowerReturningShouldersExtendUnderHeads() throws Exception {
        assertTrue(detect(1, 1, 0, 1));
    }

    @Test
    public void enlargedUpperCurveRetainsTheSameGeometry() throws Exception {
        assertTrue(detect(1, -1, 0, 1.5f));
    }

    @Test
    public void enlargedLowerCurveRetainsTheSameGeometry() throws Exception {
        assertTrue(detect(1, 1, 0, 1.5f));
    }

    @Test
    public void staffWithoutCurveDoesNotJoin() throws Exception {
        assertFalse(detect(0, -1, 0, 1));
    }

    @Test
    public void halfCurveDoesNotJoin() throws Exception {
        assertFalse(detect(3, -1, 0, 1));
    }

    @Test
    public void slantDoesNotJoin() throws Exception {
        assertFalse(detect(4, -1, 0, 1));
    }

    @Test
    public void disconnectedShouldersDoNotJoin() throws Exception {
        assertFalse(detect(5, -1, 0, 1));
    }

    @Test
    public void faintStrokeWithoutDarkCoreDoesNotJoin() throws Exception {
        assertFalse(detect(6, -1, 0, 1));
    }
}
