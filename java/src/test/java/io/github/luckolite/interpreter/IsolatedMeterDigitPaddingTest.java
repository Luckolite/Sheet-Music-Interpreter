// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Original procedural raster/ownership controls; no score pixels, fonts, or model dependency. */
public class IsolatedMeterDigitPaddingTest {
    static int[] pattern(int w, int h) {
        int[] p = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) p[y * w + x] = 0xff000000 | (y + 1) * 0x100 + x + 1;
        return p;
    }

    static void assertBand(int[] source, int w, int top, int bottom, MeterDigitPadding.Raster r) {
        assertEquals(w + 2 * r.border(), r.width());
        assertEquals(bottom - top + 2 * r.border(), r.height());
        for (int y = 0; y < r.height(); y++)
            for (int x = 0; x < r.width(); x++) {
                boolean interior =
                        x >= r.border()
                                && x < r.border() + w
                                && y >= r.border()
                                && y < r.border() + bottom - top;
                int expected =
                        interior ? source[(y - r.border() + top) * w + x - r.border()] : 0xffffffff;
                assertEquals("pixel " + x + "," + y, expected, r.pixels()[y * r.width() + x]);
            }
    }

    @Test
    public void completeDigitKeepsAllFourInkEdges() {
        int[] p = pattern(7, 11);
        p[0] = 0xff000000;
        p[6] = 0xff333333;
        p[70] = 0xff888888;
        p[76] = 0xffdddddd;
        assertBand(p, 7, 0, 11, MeterDigitPadding.pad(p, 7, 11, 0, 11));
    }

    @Test
    public void numeratorDoesNotBorrowDenominatorRows() {
        int[] p = pattern(5, 12);
        Arrays.fill(p, 30, 60, 0xffff0000);
        var r = MeterDigitPadding.pad(p, 5, 12, 0, 6);
        assertBand(p, 5, 0, 6, r);
        for (int ink : r.pixels()) assertNotEquals(0xffff0000, ink);
    }

    @Test
    public void denominatorUsesItsOwnTranslatedCoordinates() {
        int[] p = pattern(5, 12);
        Arrays.fill(p, 0, 30, 0xff00ff00);
        var r = MeterDigitPadding.pad(p, 5, 12, 6, 12);
        assertBand(p, 5, 6, 12, r);
        for (int ink : r.pixels()) assertNotEquals(0xff00ff00, ink);
    }

    @Test
    public void sourceAndPaddedRasterDoNotAlias() {
        int[] p = pattern(4, 9), before = p.clone();
        var r = MeterDigitPadding.pad(p, 4, 9, 2, 7);
        assertArrayEquals(before, p);
        r.pixels()[r.border() * r.width() + r.border()] = 0xffabcdef;
        assertArrayEquals(before, p);
        int retained = r.pixels()[(r.border() + 1) * r.width() + r.border()];
        p[12] = 0xff123456;
        assertEquals(retained, r.pixels()[(r.border() + 1) * r.width() + r.border()]);
    }

    @Test
    public void faintInkAndGrayPaperAreNotWhitened() {
        int[] p = {0xffeeeeee, 0xfff4f4f4, 0xffdfdfdf, 0xffc8c8c8, 0xff606060, 0xffffffff};
        assertBand(p, 3, 0, 2, MeterDigitPadding.pad(p, 3, 2, 0, 2));
    }

    @Test
    public void contextFollowsTheConsumerInputScale() {
        assertEquals(2, MeterDigitPadding.border(1));
        assertEquals(2, MeterDigitPadding.border(40));
        assertEquals(3, MeterDigitPadding.border(60));
        assertEquals(6, MeterDigitPadding.border(120));
        var r = MeterDigitPadding.pad(pattern(3, 60), 3, 60, 0, 60);
        assertEquals(9, r.width());
        assertEquals(66, r.height());
    }

    @Test
    public void movingTheSameOwnedBandDoesNotChangeOcrPixels() {
        int w = 6, band = 8;
        int[] a = pattern(w, band + 4), b = pattern(w, band + 11);
        for (int y = 0; y < band; y++) System.arraycopy(a, (y + 2) * w, b, (y + 7) * w, w);
        assertArrayEquals(
                MeterDigitPadding.pad(a, w, band + 4, 2, 10).pixels(),
                MeterDigitPadding.pad(b, w, band + 11, 7, 15).pixels());
    }

    @Test
    public void reflectionPreservesEveryDigitPixelAndSymmetricContext() {
        int w = 8, h = 13;
        int[] a = pattern(w, h), b = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b[y * w + x] = a[y * w + w - 1 - x];
        var left = MeterDigitPadding.pad(a, w, h, 0, h);
        var right = MeterDigitPadding.pad(b, w, h, 0, h);
        for (int y = 0; y < left.height(); y++)
            for (int x = 0; x < left.width(); x++)
                assertEquals(
                        left.pixels()[y * left.width() + x],
                        right.pixels()[y * right.width() + right.width() - 1 - x]);
    }

    @Test
    public void invalidRasterAndBandBoundsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> MeterDigitPadding.pad(null, 2, 2, 0, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> MeterDigitPadding.pad(new int[4], 0, 2, 0, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> MeterDigitPadding.pad(new int[3], 2, 2, 0, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> MeterDigitPadding.pad(new int[4], 2, 2, -1, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> MeterDigitPadding.pad(new int[4], 2, 2, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> MeterDigitPadding.pad(new int[4], 2, 2, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> MeterDigitPadding.border(0));
    }

    @Test
    public void contradictoryScaleReadingsStillAbstain() {
        assertEquals("", MeterOcrEvidence.consensus(List.of("3/4", "3/4", "2/4")));
        assertEquals("3/4", MeterOcrEvidence.consensus(List.of("3/4", "3/4", "3/4")));
    }
}
