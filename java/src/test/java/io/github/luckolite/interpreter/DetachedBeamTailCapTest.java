// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class DetachedBeamTailCapTest {
    static byte[] image(int side, int rows, int tone) {
        byte[] g = new byte[100 * 160];
        Arrays.fill(g, (byte) 255);
        for (int d = 0; d <= 70; d++)
            for (int x = 49; x <= 51; x++)
                g[(80 + side * d) * 100 + x] = (byte) (d >= 58 && d < 58 + rows ? tone : 20);
        return g;
    }

    static int run(byte[] g, int side) {
        return DetachedBeamTailCap.endpoint(g, 100, 160, 50, 80, 80 + side * 70, side, 13, 165);
    }

    @Test
    public void detachedTailStopsAtPaperCapBothDirections() {
        for (int side : new int[] {-1, 1})
            assertEquals(80 + side * 57, run(image(side, 2, 235), side));
    }

    @Test
    public void continuousShaftRemains() {
        assertEquals(150, run(image(1, 0, 235), 1));
    }

    @Test
    public void oneBlankRowIsInsufficient() {
        assertEquals(150, run(image(1, 1, 235), 1));
    }

    @Test
    public void faintConnectionRemains() {
        assertEquals(150, run(image(1, 2, 185), 1));
    }

    @Test
    public void nearBoundaryAndInvalidGeometryAbstain() {
        assertEquals(
                150,
                DetachedBeamTailCap.endpoint(image(1, 2, 235), 100, 160, 0, 80, 150, 1, 13, 165));
        assertEquals(
                150,
                DetachedBeamTailCap.endpoint(
                        image(1, 2, 235), 100, 160, 50, 80, 150, 1, Float.NaN, 165));
    }
}
