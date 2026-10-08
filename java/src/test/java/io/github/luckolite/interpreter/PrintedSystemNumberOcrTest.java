// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Original synthetic rasters and OCR responses; no private score pixels. */
public final class PrintedSystemNumberOcrTest {
    private record Page(int width, int height, byte[] labels, byte[] gray) {}

    private static Page page(float slope, int lines, boolean printed) {
        int w = 680, h = 520;
        byte[] labels = new byte[w * h], gray = new byte[labels.length];
        Arrays.fill(gray, (byte) 185);
        for (int line = 0; line < 5; line++)
            for (int x = 45; x < w - 35; x++) {
                int y = Math.round(160 + line * 10 + slope * (x - w * .5f));
                labels[y * w + x] = 4;
                if (printed && line < lines) gray[y * w + x] = 40;
            }
        return new Page(w, h, labels, gray);
    }

    private static List<MeasureRegion> measures() {
        return List.of(
                new MeasureRegion(.3f, .54f, .25f, .42f),
                new MeasureRegion(.55f, .88f, .25f, .42f));
    }

    private static void checkSlope(float slope) {
        Page p = page(slope, 5, true);
        byte[] oldLabels = p.labels.clone(), oldGray = p.gray.clone();
        var crops =
                PrintedSystemNumberOcr.candidates(p.labels, p.gray, p.width, p.height, measures());
        assertEquals(1, crops.size());
        var c = crops.get(0);
        float gap = 10;
        float expectedTop = 160 + slope * ((c.left() + c.right()) * .5f - p.width * .5f);
        assertEquals(expectedTop - gap * 4.7f, c.top(), 2f);
        assertEquals(expectedTop - gap * 1.5f, c.bottom(), 2f);
        assertEquals(0, c.systemMeasure());
        assertTrue(c.right() < measures().get(0).left() * p.width);
        assertArrayEquals(oldLabels, p.labels);
        assertArrayEquals(oldGray, p.gray);
    }

    @Test
    public void positiveSlopeUsesThePhysicalLeftHeader() {
        checkSlope(.061f);
    }

    @Test
    public void negativeSlopeUsesThePhysicalLeftHeader() {
        checkSlope(-.097f);
    }

    @Test
    public void fourPrintedRulesCannotGenerateNumberCrops() {
        Page p = page(.042f, 4, true);
        assertTrue(
                PrintedSystemNumberOcr.candidates(p.labels, p.gray, p.width, p.height, measures())
                        .isEmpty());
    }

    @Test
    public void semanticRulesWithoutPrintedInkCannotGenerateCrops() {
        Page p = page(-.047f, 5, false);
        assertTrue(
                PrintedSystemNumberOcr.candidates(p.labels, p.gray, p.width, p.height, measures())
                        .isEmpty());
    }

    @Test
    public void absentMeasuresOrMalformedPixelsCannotBorrowGeometry() {
        Page p = page(.02f, 5, true);
        assertTrue(
                PrintedSystemNumberOcr.candidates(p.labels, p.gray, p.width, p.height, List.of())
                        .isEmpty());
        assertTrue(
                PrintedSystemNumberOcr.candidates(null, p.gray, p.width, p.height, measures())
                        .isEmpty());
        assertTrue(
                PrintedSystemNumberOcr.candidates(
                                p.labels, new byte[3], p.width, p.height, measures())
                        .isEmpty());
    }

    @Test
    public void connectedSystemUsesOnlyTheHighestStaff() {
        Page p = page(.02f, 5, true);
        for (int line = 0; line < 5; line++)
            for (int x = 45; x < p.width - 35; x++) {
                int y = Math.round(340 + line * 10 + .02f * (x - p.width * .5f));
                p.labels[y * p.width + x] = 4;
                p.gray[y * p.width + x] = 40;
            }
        var crops =
                PrintedSystemNumberOcr.candidates(
                        p.labels,
                        p.gray,
                        p.width,
                        p.height,
                        List.of(new MeasureRegion(.3f, .88f, .25f, .77f)));
        assertEquals(1, crops.size());
        assertTrue(crops.get(0).top() < 200);
    }

    @Test
    public void rasterHasOnlyOriginalSamplesAndWhiteBorder() {
        byte[] gray = {10, 20, 30, 40, 50, 60};
        byte[] before = gray.clone();
        var raster =
                PrintedSystemNumberOcr.raster(
                        gray, 3, 2, new PrintedSystemNumberOcr.Crop(1, 0, 3, 2, 0), 3);
        assertEquals(46, raster.width());
        assertEquals(46, raster.height());
        assertEquals(255, raster.pixels()[0] & 255);
        assertEquals(20, raster.pixels()[20 * 46 + 20] & 255);
        assertEquals(60, raster.pixels()[25 * 46 + 25] & 255);
        assertArrayEquals(before, gray);
    }

    @Test
    public void invalidCropIsRejected() {
        try {
            PrintedSystemNumberOcr.raster(
                    new byte[6], 3, 2, new PrintedSystemNumberOcr.Crop(-1, 0, 3, 2, 0), 3);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void repeatedNumericReadingsAreRequired() {
        assertEquals(27, PrintedSystemNumberOcr.agreedNumber(List.of("27", "27", "")));
        assertEquals(0, PrintedSystemNumberOcr.agreedNumber(List.of("27", "", "")));
        assertEquals(0, PrintedSystemNumberOcr.agreedNumber(List.of("27", "28", "29")));
        assertEquals(0, PrintedSystemNumberOcr.agreedNumber(List.of("27", "27", "28", "28")));
        assertEquals(0, PrintedSystemNumberOcr.agreedNumber(List.of("27va", "27va", "0")));
    }

    private static PrintedSystemNumberOcr.Reading reading(int value, int preceding) {
        return new PrintedSystemNumberOcr.Reading(value, preceding);
    }

    @Test
    public void independentSystemNumbersUseActualVariableBarCounts() {
        assertEquals(
                21,
                PrintedSystemNumberOcr.firstMeasureNumber(
                        List.of(reading(26, 5), reading(30, 9), reading(35, 14))));
    }

    @Test
    public void isolatedLeadingMisreadDoesNotOverrideLaterAgreement() {
        assertEquals(
                21,
                PrintedSystemNumberOcr.firstMeasureNumber(
                        List.of(reading(11, 0), reading(26, 5), reading(30, 9))));
    }

    @Test
    public void duplicateReadingOfOneSystemIsOnlyOneVote() {
        assertEquals(
                0,
                PrintedSystemNumberOcr.firstMeasureNumber(List.of(reading(26, 5), reading(26, 5))));
    }

    @Test
    public void tiedConflictingNumberSequencesStayUnknown() {
        assertEquals(
                0,
                PrintedSystemNumberOcr.firstMeasureNumber(
                        List.of(reading(26, 5), reading(30, 9), reading(43, 14), reading(48, 19))));
    }

    @Test
    public void invalidStartsAndSingleAnchorsStayUnknown() {
        assertEquals(
                0,
                PrintedSystemNumberOcr.firstMeasureNumber(List.of(reading(3, 5), reading(30, 9))));
        assertEquals(0, PrintedSystemNumberOcr.firstMeasureNumber(List.of(reading(26, 5))));
    }
}
