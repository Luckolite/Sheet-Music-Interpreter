// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Test;

/** Synthetic printed-rule evidence for the primitive staff scan invariants. */
public final class StaffPitchLoopEvidenceTest {
    private static final int WIDTH = 160, HEIGHT = 200;

    private static byte[][] printedRules() {
        byte[] labels = new byte[WIDTH * HEIGHT], gray = new byte[WIDTH * HEIGHT];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++)
            for (int x = 0; x < WIDTH; x++) {
                int row = Math.round(140f - line * 12f + (x - 80f) * 0f);
                labels[row * WIDTH + x] = 4;
                gray[row * WIDTH + x] = 0;
            }
        return new byte[][] {labels, gray};
    }

    private static float[] local(byte[] labels, byte[] gray) throws Exception {
        Method method =
                StaffPitchTrack.class.getDeclaredMethod(
                        "localRulesWithSlope",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class,
                        boolean.class,
                        boolean.class,
                        int.class);
        method.setAccessible(true);
        return (float[])
                method.invoke(
                        null, labels, gray, WIDTH, HEIGHT, 80f, 75, 85, 140f, 12f, 0f, true, false,
                        170);
    }

    private static boolean bilateral(byte[] labels, byte[] gray) throws Exception {
        Method method =
                StaffPitchTrack.class.getDeclaredMethod(
                        "supportedOnBothSides",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        boolean.class,
                        int.class);
        method.setAccessible(true);
        return (Boolean)
                method.invoke(
                        null, labels, gray, WIDTH, HEIGHT, 80f, 75, 85, 140f, 12f, 0f, 0, WIDTH - 1,
                        5, 3, 3, false, 170);
    }

    @Test
    public void completePrintedRulesRecoverExactGeometryWithoutChangingPixels() throws Exception {
        byte[][] raster = printedRules();
        byte[] labels = raster[0], gray = raster[1];
        byte[] savedLabels = labels.clone(), savedGray = gray.clone();
        float[] result = local(labels, gray);
        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(Float.floatToRawIntBits(140f), Float.floatToRawIntBits(result[0]));
        assertEquals(Float.floatToRawIntBits(12f), Float.floatToRawIntBits(result[1]));
        assertArrayEquals(savedLabels, labels);
        assertArrayEquals(savedGray, gray);
        assertTrue(bilateral(labels, gray));
        assertArrayEquals(savedLabels, labels);
        assertArrayEquals(savedGray, gray);
    }

    @Test
    public void absentRightRuleEvidenceRejectsBilateralSupportWithoutChangingPixels()
            throws Exception {
        byte[][] raster = printedRules();
        byte[] labels = raster[0], gray = raster[1];
        for (int line = 0; line < 5; line++)
            for (int x = 91; x < WIDTH; x++) {
                int row = Math.round(140f - line * 12f + (x - 80f) * 0f);
                labels[row * WIDTH + x] = 0;
                gray[row * WIDTH + x] = (byte) 255;
            }
        byte[] savedLabels = labels.clone(), savedGray = gray.clone();
        assertFalse(bilateral(labels, gray));
        assertArrayEquals(savedLabels, labels);
        assertArrayEquals(savedGray, gray);
    }
}
