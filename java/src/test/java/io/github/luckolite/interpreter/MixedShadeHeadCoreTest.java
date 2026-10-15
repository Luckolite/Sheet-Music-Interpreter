// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original small oval fixtures, not rasterized published notation. */
public class MixedShadeHeadCoreTest {
    private byte[] page(int paper, int fill) {
        byte[] p = new byte[120 * 100];
        Arrays.fill(p, (byte) paper);
        for (int y = 40; y <= 58; y++)
            for (int x = 40; x <= 65; x++) {
                double r = Math.pow((x - 52.5) / 12.5, 2) + Math.pow((y - 49) / 9.0, 2);
                if (r <= 1) p[y * 120 + x] = (byte) (r > .7 ? 25 : fill);
            }
        return p;
    }

    private boolean filled(byte[] p) {
        return PaleFilledHeadCore.isFilled(p, 120, 100, 40, 40, 65, 58, 52.5f, 49, 20);
    }

    @Test
    public void darkerEngravingWithinGrayCoreStillCountsAsFilled() {
        byte[] p = page(250, 190);
        for (int y = 44; y <= 54; y++) for (int x = 48; x <= 51; x++) p[y * 120 + x] = 70;
        assertTrue(filled(p));
    }

    @Test
    public void mixedDarkInkDoesNotFillAWhiteCounter() {
        byte[] p = page(250, 250);
        for (int y = 44; y <= 54; y++) for (int x = 48; x <= 51; x++) p[y * 120 + x] = 70;
        assertFalse(filled(p));
    }
}
