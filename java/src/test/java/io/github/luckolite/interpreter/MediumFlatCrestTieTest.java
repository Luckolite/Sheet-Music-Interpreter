// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original cubic curves with steep shoulders, broad crests and independent staff rules. */
public class MediumFlatCrestTieTest {
    private boolean arc(int mode) throws Exception {
        int width = 450, height = 210, left = 100, right = 340;
        float center = 110, gap = 16;
        byte[] gray = new byte[width * height], labels = new byte[gray.length];
        Arrays.fill(gray, (byte) 255);
        for (int y : new int[] {64, 80, 96})
            for (int x = 0; x < width; x++) {
                gray[y * width + x] = 0;
                labels[y * width + x] = 4;
            }
        if (mode != 1)
            for (int i = 0; i <= 4000; i++) {
                double u = i / 4000.0, v = 1 - u;
                if (mode == 2 && u > .55 || mode == 3 && u > .4 && u < .6) continue;
                int x =
                        (int)
                                Math.round(
                                        left
                                                + (right - left)
                                                        * (3 * .06 * v * v * u
                                                                + 3 * .94 * v * u * u
                                                                + u * u * u));
                int y = (int) Math.round(center - (17 + (mode == 4 ? 16 * u : 16 * 4 * u * v)));
                gray[y * width + x] = (byte) (mode == 5 ? 210 : 0);
                if (labels[y * width + x] != 4) labels[y * width + x] = 5;
            }
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "hasFlattenedTieArc",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, labels, gray, width, height, left, right, center, gap);
    }

    @Test
    public void broadCrestRetainsBothReturningShoulders() throws Exception {
        assertTrue(arc(0));
    }

    @Test
    public void staffRulesCannotBecomeBroadTie() throws Exception {
        assertFalse(arc(1));
    }

    @Test
    public void oneShoulderCannotContinueNote() throws Exception {
        assertFalse(arc(2));
    }

    @Test
    public void missingMiddleCannotBeInvented() throws Exception {
        assertFalse(arc(3));
    }

    @Test
    public void diagonalStrokeDoesNotReturn() throws Exception {
        assertFalse(arc(4));
    }

    @Test
    public void uniformlyPaleStrokeLacksDarkCore() throws Exception {
        assertFalse(arc(5));
    }
}
