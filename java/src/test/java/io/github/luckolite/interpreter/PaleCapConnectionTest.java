// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original very pale source shafts remain printed ink across an estimated cap. */
public class PaleCapConnectionTest {
    static final int W = 100, H = 120;

    byte[] shaft(int start, int end) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 250);
        for (int y = Math.min(start, end); y <= Math.max(start, end); y++)
            p[y * W + 50] = (byte) 235;
        return p;
    }

    @Test
    public void lowerPaleContinuationIsNotWhiteSpace() {
        byte[] p = shaft(60, 70), before = p.clone();
        assertFalse(DetachedStemInkBand.beyondWhiteGap(p, W, H, 50, 60, 70, false, 16, 205));
        assertArrayEquals(before, p);
    }

    @Test
    public void upperPaleContinuationIsNotWhiteSpace() {
        byte[] p = shaft(50, 60), before = p.clone();
        assertFalse(DetachedStemInkBand.beyondWhiteGap(p, W, H, 50, 60, 50, true, 16, 205));
        assertArrayEquals(before, p);
    }
}
