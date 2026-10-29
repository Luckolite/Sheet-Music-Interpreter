// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Reuses original binary numeral geometry, never score-derived pixels. */
public class MeterTailHeadOwnershipOriginalTest {
    @Test
    public void sparseModelHeadPixelsOnCompleteEightAreNumeralInk() {
        var p = MeterOnlyCourtesyTailTest.page();
        MeterOnlyCourtesyTailTest.rect(p, 218, 96, 2, 3, 2, 0);
        MeterOnlyCourtesyTailTest.rect(p, 231, 111, 2, 3, 2, 0);
        assertTrue(MeterOnlyCourtesyTailTest.detect(p));
    }

    @Test
    public void independentHeadBesideEightRetainsMeasure() {
        var p = MeterOnlyCourtesyTailTest.page();
        MeterOnlyCourtesyTailTest.rect(p, 240, 102, 15, 8, 2, 0);
        assertFalse(MeterOnlyCourtesyTailTest.detect(p));
    }

    @Test
    public void sparseHeadWithoutCompleteEightRetainsMeasure() {
        var p = MeterOnlyCourtesyTailTest.page();
        MeterOnlyCourtesyTailTest.rect(p, 218, 96, 2, 3, 2, 0);
        MeterOnlyCourtesyTailTest.rect(p, 218, 95, 5, 7, 0, 255);
        assertFalse(MeterOnlyCourtesyTailTest.detect(p));
    }

    @Test
    public void separateRestCannotBorrowNumeralOwnership() {
        var p = MeterOnlyCourtesyTailTest.page();
        MeterOnlyCourtesyTailTest.rect(p, 218, 96, 2, 3, 2, 0);
        MeterOnlyCourtesyTailTest.rect(p, 249, 99, 5, 9, 1, 0);
        assertFalse(MeterOnlyCourtesyTailTest.detect(p));
    }

    @Test
    public void onePixelAntialiasHaloStillBelongsToProvedRim() {
        var p = MeterOnlyCourtesyTailTest.page();
        MeterOnlyCourtesyTailTest.rect(p, 234, 110, 1, 1, 2, 242);
        assertTrue(MeterOnlyCourtesyTailTest.detect(p));
    }

    @Test
    public void translatedMeterRetainsFiniteOwnership() {
        var p = MeterOnlyCourtesyTailTest.page();
        MeterOnlyCourtesyTailTest.rect(p, 218, 96, 2, 3, 2, 0);
        var q = new MeterOnlyCourtesyTailTest.Page(new byte[300 * 180], new byte[300 * 180]);
        java.util.Arrays.fill(q.gray(), (byte) 255);
        for (int y = 0; y < 175; y++)
            for (int x = 0; x < 297; x++) {
                q.labels()[(y + 5) * 300 + x + 3] = p.labels()[y * 300 + x];
                q.gray()[(y + 5) * 300 + x + 3] = p.gray()[y * 300 + x];
            }
        assertTrue(
                MeterOnlyCourtesyTail.matches(
                        q.labels(), q.gray(), 300, 180, 183, 273, 65, 129, 16, 40, 155));
    }

    @Test
    public void twoFlatHalfCountersCannotBorrowMeterOwnership() {
        var p = MeterOnlyCourtesyTailTest.page();
        MeterOnlyCourtesyTailTest.rect(p, 213, 89, 26, 34, 0, 255);
        for (int cy : new int[] {98, 112})
            for (int y = cy - 4; y <= cy + 4; y++)
                for (int x = 215; x <= 237; x++) {
                    double outer = Math.pow((x - 226) / 11., 2) + Math.pow((y - cy) / 4., 2);
                    if (outer <= 1) MeterOnlyCourtesyTailTest.rect(p, x, y, 1, 1, 5, 0);
                    double inner = Math.pow((x - 226) / 7., 2) + Math.pow((y - cy) / 1.5, 2);
                    if (inner < 1) MeterOnlyCourtesyTailTest.rect(p, x, y, 1, 1, 0, 255);
                }
        MeterOnlyCourtesyTailTest.rect(p, 215, 98, 2, 1, 2, 0);
        assertFalse(MeterOnlyCourtesyTailTest.detect(p));
    }

    @Test
    public void callerRasterAndLabelsRemainUnchanged() {
        var p = MeterOnlyCourtesyTailTest.page();
        MeterOnlyCourtesyTailTest.rect(p, 218, 96, 2, 3, 2, 0);
        var l = p.labels().clone();
        var g = p.gray().clone();
        MeterOnlyCourtesyTailTest.detect(p);
        assertArrayEquals(l, p.labels());
        assertArrayEquals(g, p.gray());
    }

    @Test
    public void completeActualHeadOverNumeralCannotDisappear() {
        var p = MeterOnlyCourtesyTailTest.page();
        MeterOnlyCourtesyTailTest.rect(p, 218, 100, 16, 9, 2, 0);
        MeterOnlyCourtesyTailTest.rect(p, 232, 74, 2, 36, 1, 0);
        assertFalse(MeterOnlyCourtesyTailTest.detect(p));
    }
}
