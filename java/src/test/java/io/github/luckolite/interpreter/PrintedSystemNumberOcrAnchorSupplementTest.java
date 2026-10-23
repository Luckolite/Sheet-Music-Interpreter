// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

/** Synthetic OCR-box replay only; contains no score image or model output. */
public final class PrintedSystemNumberOcrAnchorSupplementTest {
    private static final PrintedSystemNumberOcr.Crop CROP =
            new PrintedSystemNumberOcr.Crop(100, 200, 180, 250, 0);

    private static OcrText read(int scale, String... values) {
        var elements = new java.util.ArrayList<OcrText.Element>();
        for (int i = 0; i < values.length; i++) {
            int left = 20 + (15 + i * 14) * scale;
            int top = 20 + 10 * scale;
            elements.add(
                    new OcrText.Element(
                            values[i],
                            new OcrText.Box(left, top, left + 8 * scale, top + 12 * scale),
                            List.of()));
        }
        var line = new OcrText.Line(String.join(" ", values), null, elements);
        var block = new OcrText.Block(line.text(), null, List.of(line));
        return new OcrText(block.text(), List.of(block));
    }

    private static List<OcrText> aligned(String a, String b, String c) {
        return List.of(read(3, a), read(5, b), read(7, c));
    }

    @Test
    public void requiresTwoScaleValueAndPhysicalBoxConsensus() {
        var token = PrintedSystemNumberOcr.resolve(CROP, 400, 500, aligned("7", "7", "9"));
        assertNotNull(token);
        assertEquals(7, token.value());
        assertEquals((100 + 15) / 400f, token.left(), .0001f);
        assertEquals((200 + 10) / 500f, token.top(), .0001f);
    }

    @Test
    public void rejectsUnmatchedOrNonoverlappingBoxes() {
        assertNull(PrintedSystemNumberOcr.resolve(CROP, 400, 500, aligned("7", "8", "9")));
        var far = List.of(readAt(3, "7", 0), readAt(5, "7", 40), readAt(7, "9", 80));
        assertNull(PrintedSystemNumberOcr.resolve(CROP, 400, 500, far));
    }

    private static OcrText readAt(int scale, String value, int shift) {
        int left = 20 + (15 + shift) * scale, top = 20 + 10 * scale;
        var element =
                new OcrText.Element(
                        value,
                        new OcrText.Box(left, top, left + 8 * scale, top + 12 * scale),
                        List.of());
        var line = new OcrText.Line(value, null, List.of(element));
        var block = new OcrText.Block(value, null, List.of(line));
        return new OcrText(value, List.of(block));
    }

    @Test
    public void rejectsMultipleNumericElementsWithinOneScale() {
        var reads = List.of(read(3, "7", "8"), read(5, "7", "8"), read(7, "7"));
        assertNull(PrintedSystemNumberOcr.resolve(CROP, 400, 500, reads));
    }

    @Test
    public void requiresFullOcrTextToMatchTheSingleNumericElement() {
        var inconsistent =
                List.of(withFullText("f 7", 3, "7"), withFullText("f 7", 5, "7"), read(7, "7"));
        assertNull(PrintedSystemNumberOcr.resolve(CROP, 400, 500, inconsistent));
        var hiddenForeignElement =
                List.of(withExtraNonNumericElement(3), withExtraNonNumericElement(5), read(7, "7"));
        assertNull(PrintedSystemNumberOcr.resolve(CROP, 400, 500, hiddenForeignElement));
    }

    private static OcrText withExtraNonNumericElement(int scale) {
        int left = 20 + 15 * scale, top = 20 + 10 * scale;
        var seven =
                new OcrText.Element(
                        "7",
                        new OcrText.Box(left, top, left + 8 * scale, top + 12 * scale),
                        List.of());
        var foreign = new OcrText.Element("f", null, List.of());
        var line = new OcrText.Line("7", null, List.of(seven, foreign));
        var block = new OcrText.Block("7", null, List.of(line));
        return new OcrText("7", List.of(block));
    }

    private static OcrText withFullText(String full, int scale, String elementText) {
        int left = 20 + 15 * scale, top = 20 + 10 * scale;
        var element =
                new OcrText.Element(
                        elementText,
                        new OcrText.Box(left, top, left + 8 * scale, top + 12 * scale),
                        List.of());
        var line = new OcrText.Line(elementText, null, List.of(element));
        var block = new OcrText.Block(elementText, null, List.of(line));
        return new OcrText(full, List.of(block));
    }

    @Test
    public void rejectsOutOfCropBoxesInvalidCropsAndOversizedElementLists() {
        var outside = List.of(readAt(3, "7", -20), readAt(5, "7", -20), read(7, "7"));
        assertNull(PrintedSystemNumberOcr.resolve(CROP, 400, 500, outside));
        var outsideCropBody = List.of(outsideBodyRead(3), outsideBodyRead(5), read(7, "7"));
        assertNull(PrintedSystemNumberOcr.resolve(CROP, 400, 500, outsideCropBody));
        assertNull(
                PrintedSystemNumberOcr.resolve(
                        new PrintedSystemNumberOcr.Crop(-1, 200, 180, 250, 0),
                        400,
                        500,
                        aligned("7", "7", "7")));
        var excessive = largeElementRead();
        assertNull(
                PrintedSystemNumberOcr.resolve(
                        CROP, 400, 500, List.of(excessive, excessive, excessive)));
        var longNestedText = longNestedTextRead();
        assertNull(
                PrintedSystemNumberOcr.resolve(
                        CROP, 400, 500, List.of(longNestedText, longNestedText, read(7, "7"))));
    }

    private static OcrText outsideBodyRead(int scale) {
        int left = 20 + (CROP.right() - CROP.left()) * scale + 1;
        int top = 20 + 10 * scale;
        var element =
                new OcrText.Element("7", new OcrText.Box(left, top, left + 4, top + 4), List.of());
        var line = new OcrText.Line("7", null, List.of(element));
        var block = new OcrText.Block("7", null, List.of(line));
        return new OcrText("7", List.of(block));
    }

    private static OcrText largeElementRead() {
        var elements = new java.util.ArrayList<OcrText.Element>();
        for (int i = 0; i < 65; i++) elements.add(new OcrText.Element("x", null, List.of()));
        elements.add(read(5, "7").blocks().get(0).lines().get(0).elements().get(0));
        var line = new OcrText.Line("7", null, elements);
        var block = new OcrText.Block("7", null, List.of(line));
        return new OcrText("7", List.of(block));
    }

    private static OcrText longNestedTextRead() {
        String tooLong = "7".repeat(257);
        var element = new OcrText.Element("7", new OcrText.Box(65, 50, 89, 86), List.of());
        var line = new OcrText.Line(tooLong, null, List.of(element));
        var block = new OcrText.Block(tooLong, null, List.of(line));
        return new OcrText("7", List.of(block));
    }

    @Test
    public void rejectsOutOfRangeAndMalformedReadingsWithoutChangingEvidence() {
        OcrText original = read(3, "0");
        assertNull(
                PrintedSystemNumberOcr.resolve(
                        CROP, 400, 500, List.of(original, read(5, "0"), read(7, "0"))));
        assertNull(PrintedSystemNumberOcr.resolve(CROP, 400, 500, aligned("1000", "1000", "1000")));
        assertEquals("0", original.text());
    }

    @Test
    public void alreadyAnchoredCropDoesNotInvokeOcrOrMutatePixels() throws Exception {
        int width = 680, height = 520;
        byte[] labels = new byte[width * height], gray = new byte[width * height];
        java.util.Arrays.fill(gray, (byte) 185);
        for (int line = 0; line < 5; line++)
            for (int x = 45; x < width - 35; x++) {
                int y = Math.round(160 + line * 10 + .061f * (x - width * .5f));
                labels[y * width + x] = 4;
                gray[y * width + x] = 40;
            }
        var measures =
                List.of(
                        new MeasureRegion(.3f, .54f, .25f, .42f),
                        new MeasureRegion(.55f, .88f, .25f, .42f));
        var crop = PrintedSystemNumberOcr.candidates(labels, gray, width, height, measures).get(0);
        var existing =
                List.of(
                        new SheetInterpreter.NumberToken(
                                13,
                                crop.left() / (float) width,
                                crop.top() / (float) height,
                                crop.right() / (float) width,
                                crop.bottom() / (float) height,
                                crop.left() / (float) width));
        byte[] labelsBefore = labels.clone(), grayBefore = gray.clone();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertTrue(
                PrintedSystemNumberOcr.supplement(
                                labels,
                                gray,
                                width,
                                height,
                                measures,
                                existing,
                                (pixels, w, h) -> {
                                    calls.incrementAndGet();
                                    return read(3, "7");
                                })
                        .isEmpty());
        assertEquals(0, calls.get());
        assertArrayEquals(labelsBefore, labels);
        assertArrayEquals(grayBefore, gray);
    }

    @Test
    public void acceptedSyntheticTokenLeavesPageArraysUnchanged() throws Exception {
        int width = 680, height = 520;
        byte[] labels = new byte[width * height], gray = new byte[width * height];
        java.util.Arrays.fill(gray, (byte) 185);
        for (int line = 0; line < 5; line++)
            for (int x = 45; x < width - 35; x++) {
                int y = Math.round(160 + line * 10 + .061f * (x - width * .5f));
                labels[y * width + x] = 4;
                gray[y * width + x] = 40;
            }
        var measures =
                List.of(
                        new MeasureRegion(.3f, .54f, .25f, .42f),
                        new MeasureRegion(.55f, .88f, .25f, .42f));
        byte[] labelsBefore = labels.clone(), grayBefore = gray.clone();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var tokens =
                PrintedSystemNumberOcr.supplement(
                        labels,
                        gray,
                        width,
                        height,
                        measures,
                        List.of(),
                        (pixels, w, h) -> {
                            calls.incrementAndGet();
                            int scale =
                                    (w - 40)
                                            / (PrintedSystemNumberOcr.candidates(
                                                                    labels, gray, width, height,
                                                                    measures)
                                                            .get(0)
                                                            .right()
                                                    - PrintedSystemNumberOcr.candidates(
                                                                    labels, gray, width, height,
                                                                    measures)
                                                            .get(0)
                                                            .left());
                            return readAt(scale, "7", 0);
                        });
        assertEquals(3, calls.get());
        assertEquals(1, tokens.size());
        assertEquals(7, tokens.get(0).value());
        assertArrayEquals(labelsBefore, labels);
        assertArrayEquals(grayBefore, gray);
    }

    @Test
    public void nullOrOversizedPagePayloadDoesNotCallOcr() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertTrue(
                PrintedSystemNumberOcr.supplement(
                                null,
                                new byte[0],
                                5000,
                                5000,
                                List.of(),
                                List.of(),
                                (pixels, w, h) -> {
                                    calls.incrementAndGet();
                                    return read(3, "7");
                                })
                        .isEmpty());
        assertTrue(
                PrintedSystemNumberOcr.supplement(
                                new byte[0],
                                new byte[0],
                                5000,
                                5000,
                                List.of(),
                                List.of(),
                                (pixels, w, h) -> {
                                    calls.incrementAndGet();
                                    return read(3, "7");
                                })
                        .isEmpty());
        assertEquals(0, calls.get());
    }
}
