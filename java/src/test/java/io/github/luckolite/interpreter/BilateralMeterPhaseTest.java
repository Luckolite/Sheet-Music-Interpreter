// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original clipped meter-frame geometry with two independent printed-rule witnesses. */
public class BilateralMeterPhaseTest {
    static final int W = 640, H = 300;

    static byte[] page(boolean both) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int n = 0; n < 5; n++)
            for (int x = 50; x < (both ? 590 : 290); x++) g[(100 + n * 14) * W + x] = 30;
        return g;
    }

    static MeterChangeDetector.Crop crop(int top, int bottom, int first) {
        return new MeterChangeDetector.Crop(300, top, 340, bottom, first, 14);
    }

    @Test
    public void twoWitnessesCorrectClippedUpperRule() {
        var c = crop(102, 165, 105);
        var r = MeterCropStaff.refine(page(true), W, H, c);
        assertEquals(100, r.firstLine());
        assertEquals(14, r.gap(), .2);
    }

    @Test
    public void twoWitnessesCorrectClippedLowerRule() {
        var c = crop(90, 154, 95);
        var r = MeterCropStaff.refine(page(true), W, H, c);
        assertEquals(100, r.firstLine());
    }

    @Test
    public void oneWitnessCannotMoveClippedPhase() {
        var c = crop(102, 165, 105);
        assertEquals(c, MeterCropStaff.refine(page(false), W, H, c));
    }

    @Test
    public void deepClippingRemainsUnresolved() {
        var c = crop(105, 165, 108);
        assertEquals(c, MeterCropStaff.refine(page(true), W, H, c));
    }

    @Test
    public void distantPhaseRemainsUnresolved() {
        var c = crop(102, 170, 109);
        assertEquals(c, MeterCropStaff.refine(page(true), W, H, c));
    }

    @Test
    public void unclippedDistantHintKeepsOldBound() {
        var c = crop(94, 165, 106);
        assertEquals(c, MeterCropStaff.refine(page(true), W, H, c));
    }

    @Test
    public void cropPixelsNeverExpand() {
        var c = crop(102, 165, 105);
        var r = MeterCropStaff.refine(page(true), W, H, c);
        assertEquals(c.left(), r.left());
        assertEquals(c.top(), r.top());
        assertEquals(c.right(), r.right());
        assertTrue(r.top() >= c.top());
        assertTrue(r.bottom() <= c.bottom());
        assertEquals(161, r.bottom());
        var pixels = page(true);
        for (int y = r.bottom(); y < c.bottom(); y++)
            for (int x = c.left(); x < c.right(); x++)
                assertEquals(255, pixels[y * W + x] & 255);
    }

    @Test
    public void inputPixelsRemainExact() {
        var g = page(true);
        var before = g.clone();
        MeterCropStaff.refine(g, W, H, crop(102, 165, 105));
        assertArrayEquals(before, g);
    }
}
