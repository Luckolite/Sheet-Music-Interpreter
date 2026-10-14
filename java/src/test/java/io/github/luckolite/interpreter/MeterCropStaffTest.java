// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original staff drawings test geometry evidence, never numeral acceptance thresholds. */
public class MeterCropStaffTest {
    static final int W = 640, H = 300;

    static byte[] rails(int count, int first, float slope) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 235);
        for (int n = 0; n < count; n++)
            for (int x = 60; x < 580; x++) {
                int y = first + n * 14 + Math.round((x - 320) * slope);
                g[y * W + x] = 30;
            }
        return g;
    }

    static MeterChangeDetector.Crop crop(int first) {
        return new MeterChangeDetector.Crop(300, 94, 340, 165, first, 14);
    }

    @Test
    public void completeRailsCorrectShiftedHint() {
        var c = crop(102);
        var r = MeterCropStaff.refine(rails(5, 100, 0), W, H, c);
        assertEquals(100, r.firstLine());
        assertEquals(14, r.gap(), .2);
        assertEquals(c.left(), r.left());
        assertEquals(c.right(), r.right());
    }

    @Test
    public void alreadyAlignedStaffStaysAligned() {
        var r = MeterCropStaff.refine(rails(5, 100, 0), W, H, crop(100));
        assertEquals(100, r.firstLine());
        assertEquals(14, r.gap(), .2);
    }

    @Test
    public void fourRailsCannotRephaseMeter() {
        var c = crop(102);
        assertEquals(c, MeterCropStaff.refine(rails(4, 100, 0), W, H, c));
    }

    @Test
    public void blankPaperRetainsOriginal() {
        var c = crop(102);
        assertEquals(c, MeterCropStaff.refine(rails(0, 100, 0), W, H, c));
    }

    @Test
    public void disagreeingLocalPhasesRetainOriginal() {
        var c = crop(102);
        assertEquals(c, MeterCropStaff.refine(rails(5, 100, .08f), W, H, c));
    }

    @Test
    public void distantStaffCannotMoveCrop() {
        var c = crop(106);
        assertEquals(c, MeterCropStaff.refine(rails(5, 100, 0), W, H, c));
    }

    @Test
    public void malformedRasterRetainsOriginal() {
        var c = crop(102);
        assertEquals(c, MeterCropStaff.refine(new byte[3], W, H, c));
        assertEquals(c, MeterCropStaff.refine(null, W, H, c));
    }

    @Test
    public void invalidGapRetainsOriginal() {
        var c = new MeterChangeDetector.Crop(300, 94, 340, 165, 102, Float.NaN);
        assertSame(c, MeterCropStaff.refine(rails(5, 100, 0), W, H, c));
    }

    @Test
    public void sourcePixelsArePreserved() {
        var g = rails(5, 100, 0);
        var before = g.clone();
        MeterCropStaff.refine(g, W, H, crop(102));
        assertArrayEquals(before, g);
    }
}
