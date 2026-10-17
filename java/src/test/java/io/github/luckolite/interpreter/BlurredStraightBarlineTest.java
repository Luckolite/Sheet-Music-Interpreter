// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original thick strokes with independently quantized edges, no source score pixels. */
public class BlurredStraightBarlineTest {
    boolean bar(int kind, int scale, double phase, int paper, int ink) throws Exception {
        int w = 280 * scale, h = 220 * scale;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) paper);
        int[] rows = {70 * scale, 88 * scale, 106 * scale, 124 * scale, 142 * scale};
        float slope = kind == 5 ? .08f : 0;
        for (int y : rows) for (int x = 20 * scale; x < 260 * scale; x++) g[y * w + x] = (byte) ink;
        for (int y = rows[0] - 3 * scale; y <= rows[4] + 3 * scale; y++) {
            double angle = (y - rows[0]) * 2 * Math.PI / (18 * scale) + phase;
            double axis = 140 * scale - slope * (y - 106 * scale),
                    left = -3 * scale,
                    right = 3 * scale;
            double distance = 1000;
            for (int row : rows) distance = Math.min(distance, Math.abs(y - row));
            double flare = 1 + 7 * Math.max(0, 1 - distance / (8d * scale));
            left = -flare * scale;
            right = flare * scale;
            for (int x = (int) Math.round(axis + left); x <= Math.round(axis + right); x++)
                g[y * w + x] = (byte) ink;
        }
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "rawBarlineSpansStaff",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int[].class,
                        float.class,
                        float.class,
                        float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, g, w, h, 140 * scale, rows, 18f * scale, 0f, slope);
    }

    @Test
    public void symmetricStaffBlurRetainsStraightBar() throws Exception {
        assertTrue(bar(0, 1, 0, 255, 0));
    }

    @Test
    public void doubledStaffBlurRetainsStraightBar() throws Exception {
        assertTrue(bar(0, 2, 0, 255, 0));
    }

    @Test
    public void grayPaperRetainsStraightBar() throws Exception {
        assertTrue(bar(0, 1, 0, 210, 100));
    }
}
