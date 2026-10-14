// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic courtesy-key/meter tail and playable negative controls. */
public class SingleBarMeterTailTest {
    private RasterMeterReadingsTest.Page page() {
        var p = new RasterMeterReadingsTest.Page(16);
        p.four(0);
        p.four(1);
        p.staff();
        for (int y = p.first; y <= p.first + 4 * p.gap; y++) ink(p, 64, y, 1);
        for (int center : new int[] {80, 104}) {
            int cy = p.first + 2 * p.gap;
            for (int y = cy - 24; y <= cy + 12; y++)
                for (int x = center - 6; x <= center - 4; x++) ink(p, x, y, 3);
            for (int y = cy - 12; y <= cy + 24; y++)
                for (int x = center + 4; x <= center + 6; x++) ink(p, x, y, 3);
            for (int yy : new int[] {cy - 10, cy + 10})
                for (int y = yy - 2; y <= yy + 2; y++)
                    for (int x = center - 4; x <= center + 4; x++) ink(p, x, y, 3);
        }
        return p;
    }

    private void ink(RasterMeterReadingsTest.Page p, int x, int y, int label) {
        p.gray[y * p.w + x] = 0;
        p.labels[y * p.w + x] = (byte) label;
    }

    private boolean tail(RasterMeterReadingsTest.Page p) {
        return OmrMeasurePostProcessor.courtesySignatureTail(
                p.labels,
                p.gray,
                p.w,
                p.h,
                64,
                144,
                p.first,
                p.first + 4 * p.gap,
                p.gap,
                p.first - 2 * p.gap,
                p.first + 6 * p.gap);
    }

    @Test
    public void completeMeterAndKeyAfterSingleBarHaveNoDuration() {
        assertTrue(tail(page()));
    }

    @Test
    public void noteBeyondFalseMeterBoundaryPreservesMeasure() {
        var p = page();
        ink(p, 170, p.first + 20, 2);
        assertFalse(tail(p));
    }

    @Test
    public void unlabelledRestPreservesMeasure() {
        var p = page();
        for (int y = p.first + 20; y < p.first + 28; y++)
            for (int x = 170; x < 187; x++) ink(p, x, y, 0);
        assertFalse(tail(p));
    }

    @Test
    public void realClosingBarPreservesMeasure() {
        var p = page();
        for (int y = p.first; y <= p.first + 4 * p.gap; y++) ink(p, 190, y, 1);
        assertFalse(tail(p));
    }

    @Test
    public void absentPrintedMeterDoesNotSuppressMeasure() {
        var p = page();
        for (int y = 0; y < p.h; y++)
            for (int x = 128; x < 154; x++) {
                p.gray[y * p.w + x] = (byte) 255;
                p.labels[y * p.w + x] = 0;
            }
        assertFalse(tail(p));
    }
}
