// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original font renderings, horizontally compressed without private-score pixels. */
public class NarrowMeterFontTest {
    static int[] narrow(String text) throws Exception {
        int[] original = DarkMeterCoreTest.page(48, 0f, text), out = new int[80 * 72];
        Arrays.fill(out, 0xffffffff);
        for (int y = 0; y < 72; y++)
            for (int x = 0; x < 80; x++) {
                int sx = (int) Math.floor(40 + (x - 40) / .8);
                if (sx >= 0 && sx < 80) out[y * 80 + x] = original[y * 80 + sx];
            }
        MeterCropRaster.prepare(out, 80, 72, 0, 8, 14, false);
        return out;
    }

    @Test
    public void narrowThreeQuarters() throws Exception {
        assertEquals("3/4", DarkMeterCoreTest.font().read(narrow("3/4"), 80, 72, 36, 8, 14).text());
    }

    @Test
    public void narrowFourQuarters() throws Exception {
        assertEquals("4/4", DarkMeterCoreTest.font().read(narrow("4/4"), 80, 72, 36, 8, 14).text());
    }

    @Test
    public void narrowSixEighths() throws Exception {
        assertEquals("6/8", DarkMeterCoreTest.font().read(narrow("6/8"), 80, 72, 36, 8, 14).text());
    }

    @Test
    public void missingLowerRejected() throws Exception {
        int[] p = narrow("3/4");
        Arrays.fill(p, 36 * 80, p.length, 0xffffffff);
        assertEquals("", DarkMeterCoreTest.font().read(p, 80, 72, 36, 8, 14).text());
    }

    @Test
    public void pixelsPreserved() throws Exception {
        int[] p = narrow("3/4"), old = p.clone();
        DarkMeterCoreTest.font().read(p, 80, 72, 36, 8, 14);
        assertArrayEquals(old, p);
    }
}
