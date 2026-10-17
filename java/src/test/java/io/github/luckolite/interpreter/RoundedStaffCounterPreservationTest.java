// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two connected figure-eight glyph bodies and independent horizontal staff rules. */
public class RoundedStaffCounterPreservationTest {
    static final int W = 200, H = 200;

    byte[] page(boolean staff) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int top : new int[] {76, 109})
            for (int y = top; y <= top + 31; y++)
                for (int x = 90; x <= 110; x++) {
                    int cx = Math.max(94, Math.min(106, x)),
                            cy = Math.max(top + 4, Math.min(top + 27, y));
                    boolean outer = (x - cx) * (x - cx) + (y - cy) * (y - cy) <= 16;
                    boolean inner = false;
                    for (int eye : new int[] {top + 8, top + 24})
                        inner |= Math.pow((x - 100) / 6d, 2) + Math.pow((y - eye) / 5d, 2) < 1;
                    if (outer && !inner) g[y * W + x] = 0;
                }
        if (staff)
            for (int line : new int[] {78, 94, 110, 126, 142})
                for (int y = line - 1; y <= line + 1; y++)
                    for (int x = 20; x < 180; x++) g[y * W + x] = 0;
        return g;
    }

    @Test
    public void paperGapBetweenActualDigitBodiesRetainsRoundedReading() {
        assertTrue(RoundedMeterCounters.matches(page(false), W, H, 94, 76, 144, 16));
    }

    @Test
    public void staffIntersectionsCannotSupplyMissingInterDigitRailInk() {
        assertTrue(RoundedMeterCounters.matches(page(true), W, H, 94, 76, 144, 16));
    }

    @Test
    public void genuineStackWithAlignedStaffDoesNotBecomeBoundary() throws Exception {
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "rawBarlineColumn",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int[].class,
                        float.class,
                        float.class,
                        float.class);
        m.setAccessible(true);
        assertEquals(
                Integer.MIN_VALUE,
                (int)
                        m.invoke(
                                null,
                                page(true),
                                W,
                                H,
                                94,
                                new int[] {78, 94, 110, 126, 142},
                                16f,
                                0f,
                                0f));
    }

    @Test
    public void readingLeavesGlyphStaffAndOutsidePaperExact() {
        var g = page(true);
        var before = g.clone();
        RoundedMeterCounters.matches(g, W, H, 94, 76, 144, 16);
        assertArrayEquals(before, g);
    }
}
