// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PortableOcrTest {
    @Test
    public void toleratesSigmoidEndpointRoundingButNotInvalidProbabilities() {
        float[][] map = new float[4][4];
        for (var row : map) Arrays.fill(row, Math.nextUp(1f));
        assertEquals(1, PortableOcr.regions(map, 40, 40).size());
        map[0][0] = 1.01f;
        assertThrows(IllegalArgumentException.class, () -> PortableOcr.regions(map, 40, 40));
    }

    @Test
    public void paddedTempoDigitsRetainConservativePrintedEqualsDetection() {
        int width = 300, height = 240;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0xffffffff);
        byte[] gray = new byte[pixels.length];
        Arrays.fill(gray, (byte) 255);
        for (int x = 104; x <= 116; x++) {
            pixels[48 * width + x] = pixels[54 * width + x] = 0xff000000;
            gray[48 * width + x] = gray[54 * width + x] = 0;
        }
        for (int y = 42; y < 60; y++)
            for (int x = 120; x < 150; x++) {
                pixels[y * width + x] = 0xff000000;
                gray[y * width + x] = 0;
            }
        var raw = new OcrText.Box(118, 15, 153, 88);
        var bounds = PortableOcr.inkBounds(pixels, width, raw);
        assertEquals(
                List.of(new ScoreTempoChange(0, 0, 136)),
                TempoChangeDetector.detect(
                        List.of(
                                new MeasureNumberReconciler.NumberToken(
                                        136,
                                        bounds.left / (float) width,
                                        bounds.top / (float) height,
                                        bounds.right / (float) width,
                                        bounds.bottom / (float) height)),
                        gray,
                        width,
                        height,
                        List.of(new MeasureRegion(.2f, .9f, .27f, .48f))));
    }

    @Test
    public void trimsDetectorPaddingToInkAndPreservesBlankEvidenceBounds() {
        int[] pixels = new int[20 * 70];
        Arrays.fill(pixels, 0xffffffff);
        var box = new OcrText.Box(0, 0, 20, 70);
        assertEquals(box, PortableOcr.inkBounds(pixels, 20, box));
        for (int y = 20; y < 40; y++) for (int x = 4; x < 16; x++) pixels[y * 20 + x] = 0xff000000;
        assertEquals(new OcrText.Box(4, 20, 16, 40), PortableOcr.inkBounds(pixels, 20, box));
        pixels[0] = 0;
        assertEquals(new OcrText.Box(4, 20, 16, 40), PortableOcr.inkBounds(pixels, 20, box));
    }

    @Test
    public void splitsStackedMeterButNotDottedLetter() {
        int[] pixels = new int[20 * 70];
        Arrays.fill(pixels, 0xffffffff);
        for (int y = 5; y < 25; y++) for (int x = 5; x < 15; x++) pixels[y * 20 + x] = 0xff000000;
        for (int y = 40; y < 60; y++) for (int x = 5; x < 15; x++) pixels[y * 20 + x] = 0xff000000;
        var box = new OcrText.Box(0, 0, 20, 70);
        var split = PortableOcr.splitStackedRows(pixels, 20, box);
        assertEquals(2, split.size());
        assertEquals(split.get(0).bottom, split.get(1).top);
        Arrays.fill(pixels, 0xffffffff);
        for (int y = 5; y < 8; y++) for (int x = 5; x < 10; x++) pixels[y * 20 + x] = 0xff000000;
        for (int y = 20; y < 60; y++) for (int x = 5; x < 10; x++) pixels[y * 20 + x] = 0xff000000;
        assertEquals(List.of(box), PortableOcr.splitStackedRows(pixels, 20, box));
    }

    @Test
    public void recognizerPaddingIsZeroAndAlphaCompositesWhite() {
        var tensor =
                PortableOcr.normalize(new int[] {0, 0xff000000}, 2, 1, 0, 0, 2, 1, 2, 1, 4, false);
        assertArrayEquals(new float[] {1, -1, 0, 0, 1, -1, 0, 0, 1, -1, 0, 0}, tensor, 0);
    }

    @Test
    public void connectedRegionMapsBackToInputAndClampsExpansion() {
        float[][] map = new float[8][8];
        for (int y = 0; y < 3; y++) for (int x = 0; x < 4; x++) map[y][x] = .9f;
        var boxes = PortableOcr.regions(map, 80, 80);
        assertEquals(1, boxes.size());
        assertEquals(0, boxes.get(0).left);
        assertEquals(0, boxes.get(0).top);
        assertTrue(boxes.get(0).right >= 40);
        assertTrue(boxes.get(0).bottom >= 30);
    }

    @Test
    public void emptyDetectorProducesNoInventedText() throws Exception {
        var engine =
                new PortableOcr(
                        new PortableOcr.Inference() {
                            public float[][] detect(float[] x, int w, int h) {
                                return new float[32][32];
                            }

                            public float[][] recognize(float[] x, int w, int h) {
                                throw new AssertionError("No crop expected");
                            }

                            public List<String> dictionary() {
                                return List.of("", "x");
                            }
                        });
        var result = engine.read(new int[64], 8, 8);
        assertEquals("", result.text());
        assertTrue(result.blocks().isEmpty());
    }

    @Test
    public void rejectsMalformedInputsBeforeInference() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PortableOcr.regions(new float[][] {{Float.NaN}}, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> PortableOcr.regions(new float[][] {{2}}, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> PortableOcr.regions(new float[][] {{0}, null}, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> PortableOcr.regions(new float[][] {{0}}, 0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> PortableOcr.normalize(new int[4], 2, 2, -1, 0, 2, 2, 2, 2, 2, false));
    }

    @Test
    public void cancellationStopsPreprocessing() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(
                    java.util.concurrent.CancellationException.class,
                    () -> PortableOcr.normalize(new int[4], 2, 2, 0, 0, 2, 2, 2, 2, 2, false));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void halfPixelCropPreservesMixedAlphaBgrAndRawPadding() {
        int[] pixels = {
            0xffaabbcc, 0xffaabbcc, 0xffaabbcc,
            0xffaabbcc, 0x00112233, 0xff102030,
            0xffaabbcc, 0x80112233, 0xffffffff
        };
        int[] original = pixels.clone();
        // Nonzero crop origins, clamped edges and the midpoint cover both coordinate axes.
        // Each plane has three content columns followed by positive zero padding.
        int[][] expectedBits = {
            {
                0x3f800000, 0x3e44c4c8, 0xbf1f9fa0, 0x00000000,
                0x3f19999a, 0x3ecacacc, 0x3e44c4c8, 0x00000000,
                0x3e4cccd0, 0x3f19999a, 0x3f800000, 0x00000000,
                0x3f800000, 0x3e048488, 0xbf3fbfc0, 0x00000000,
                0x3f119192, 0x3eb2b2b4, 0x3e048488, 0x00000000,
                0x3e048488, 0x3f119192, 0x3f800000, 0x00000000,
                0x3f800000, 0x3d888890, 0xbf5fdfe0, 0x00000000,
                0x3f078788, 0x3e969698, 0x3d888890, 0x00000000,
                0x3d888890, 0x3f078788, 0x3f800000, 0x00000000
            },
            {
                0x400fee1d, 0x3ef8599d, 0xbfa5e092, 0x00000000,
                0x3fb011e3, 0x3f6e284a, 0x3ef8599d, 0x00000000,
                0x3f008f18, 0x3fb011e3, 0x400fee1d, 0x00000000,
                0x401b6db7, 0x3ef8787c, 0xbfbcdcdc, 0x00000000,
                0x3fbb9b9c, 0x3f79b9bb, 0x3ef8787c, 0x00000000,
                0x3ef8787c, 0x3fbb9b9c, 0x401b6db7, 0x00000000,
                0x4028f5c3, 0x3f10e089, 0xbfc3461e, 0x00000000,
                0x3fcc1056, 0x3f8922be, 0x3f10e089, 0x00000000,
                0x3f10e089, 0x3fcc1056, 0x4028f5c3, 0x00000000
            }
        };
        for (int mode = 0; mode < expectedBits.length; mode++) {
            float[] actual = PortableOcr.normalize(pixels, 3, 3, 1, 1, 3, 3, 3, 3, 4, mode == 1);
            assertEquals(expectedBits[mode].length, actual.length);
            for (int i = 0; i < actual.length; i++)
                assertEquals(
                        "mode " + mode + " sample " + i,
                        expectedBits[mode][i],
                        Float.floatToRawIntBits(actual[i]));
            assertArrayEquals(original, pixels);
        }
    }
}
