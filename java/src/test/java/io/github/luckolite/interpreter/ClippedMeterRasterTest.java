// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original ARGB crops with staff rules wholly outside their retained pixel rectangle. */
public class ClippedMeterRasterTest {
    static int[] pixels(int h) {
        int[] p = new int[20 * h];
        Arrays.fill(p, 0xffffffff);
        p[7 * 20 + 10] = 0xff000000;
        return p;
    }

    @Test
    public void absentUpperRuleDoesNotReadNegativeRows() {
        int[] p = pixels(60);
        p[10] = 0xff000000;
        int[] before = p.clone();
        MeterCropRaster.prepare(p, 20, 60, 0, -3, 14, false);
        assertArrayEquals(before, p);
    }

    @Test
    public void absentLowerRuleDoesNotReadPastCrop() {
        int[] p = pixels(64), before = p.clone();
        MeterCropRaster.prepare(p, 20, 64, 0, 11, 14, false);
        assertArrayEquals(before, p);
    }

    @Test
    public void sourceTopOffsetUsesSameClipping() {
        int[] p = pixels(60), q = p.clone();
        MeterCropRaster.prepare(p, 20, 60, 0, -3, 14, false);
        MeterCropRaster.prepare(q, 20, 60, 103, 100, 14, false);
        assertArrayEquals(p, q);
    }
}
