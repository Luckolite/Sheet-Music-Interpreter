// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class RestWaveBoundsTest {
    @Test
    public void fractionalRightWitnessCannotReadPastLastRow() {
        int w = 143, h = 143;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        g[142 * w + 11] = 0;
        assertFalse(RestVerticalWave.crosses(g, w, h, 20, 134, 130, 140, 8.75f));
    }

    @Test
    public void missingRightWitnessMatchesWhitePadding() {
        int w = 31, h = 60;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        for (int y = 0; y < h; y++) {
            int x = 12 + Math.abs((y % 12) - 6);
            g[y * w + x] = 0;
            g[y * w + 1] = 0;
        }
        byte[] padded = new byte[(w + 1) * h];
        Arrays.fill(padded, (byte) 255);
        for (int y = 0; y < h; y++) System.arraycopy(g, y * w, padded, y * (w + 1), w);
        assertEquals(
                RestVerticalWave.crosses(padded, w + 1, h, 10, 22, 16, 40, 8.75f),
                RestVerticalWave.crosses(g, w, h, 10, 22, 16, 40, 8.75f));
    }
}
