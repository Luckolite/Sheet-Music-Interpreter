// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original opaque gray fixtures compare the retained ARGB path with the gray path. */
public class PortableOcrGrayTest {
    static int[] referenceArgb(byte[] gray) {
        int[] pixels = new int[gray.length];
        for (int i = 0; i < pixels.length; i++)
            pixels[i] = 0xff000000 + (gray[i] & 255) * 0x00010101;
        return pixels;
    }

    static byte[] gray(int width, int height, int seed) {
        byte[] pixels = new byte[width * height];
        int value = seed;
        for (int i = 0; i < pixels.length; i++) {
            value = value * 1664525 + 1013904223;
            pixels[i] = (byte) value;
        }
        return pixels;
    }

    static void bits(float[] expected, float[] actual) {
        assertEquals("complete tensor length", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++)
            assertEquals(
                    "tensor sample " + i,
                    Float.floatToRawIntBits(expected[i]),
                    Float.floatToRawIntBits(actual[i]));
    }

    @Test
    public void materializesEveryUnsignedGrayAsOpaqueArgb() {
        byte[] gray = new byte[256];
        for (int i = 0; i < gray.length; i++) gray[i] = (byte) i;
        byte[] before = gray.clone();
        assertArrayEquals(referenceArgb(gray), PortableOcr.opaqueGrayPixels(gray));
        assertArrayEquals(before, gray);
        for (boolean detector : new boolean[] {false, true}) {
            float[] expected =
                    PortableOcr.normalize(
                            referenceArgb(gray), 256, 1, 0, 0, 256, 1, 256, 1, 260, detector);
            float[] actual =
                    PortableOcr.normalizeGray(gray, 256, 1, 0, 0, 256, 1, 256, 1, 260, detector);
            bits(expected, actual);
            for (int c = 0; c < 3; c++)
                for (int x = 256; x < 260; x++)
                    assertEquals(0, Float.floatToRawIntBits(actual[c * 260 + x]));
            assertArrayEquals(before, gray);
        }
    }

    @Test
    public void fullResizeBitsCoverCropsAspectsClampsAndPadding() {
        int[][] cases = {
            {1, 1, 0, 0, 1, 1, 1, 1, 1},
            {1, 1, 0, 0, 1, 1, 9, 7, 12},
            {7, 5, 0, 0, 7, 5, 3, 2, 6},
            {7, 5, 1, 1, 6, 4, 13, 9, 16},
            {9, 11, 8, 0, 9, 11, 3, 17, 4},
            {9, 11, 0, 10, 9, 11, 13, 2, 16},
            {53, 37, 3, 5, 47, 29, 17, 13, 320},
            {31, 23, 0, 0, 31, 23, 31, 23, 31},
            {80, 40, 3, 2, 77, 39, 89, 48, 320}
        };
        for (int i = 0; i < cases.length; i++)
            for (boolean detector : new boolean[] {false, true}) {
                int[] c = cases[i];
                byte[] input = gray(c[0], c[1], i + 17), snapshot = input.clone();
                int[] argb = referenceArgb(input), argbBefore = argb.clone();
                float[] expected =
                        PortableOcr.normalize(
                                argb, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8],
                                detector);
                float[] actual =
                        PortableOcr.normalizeGray(
                                input, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8],
                                detector);
                bits(expected, actual);
                assertArrayEquals(snapshot, input);
                assertArrayEquals(argbBefore, argb);
                for (int plane = 0; plane < 3; plane++)
                    for (int y = 0; y < c[7]; y++)
                        for (int x = c[6]; x < c[8]; x++)
                            assertEquals(
                                    0,
                                    Float.floatToRawIntBits(
                                            actual[plane * c[8] * c[7] + y * c[8] + x]));
            }
    }

    @Test
    public void detectorPlaneMeansAndRecognizerCopiesStayExact() {
        float[] mean = {.485f, .456f, .406f}, std = {.229f, .224f, .225f};
        for (int intensity : new int[] {0, 127, 128, 255}) {
            byte[] input = {(byte) intensity};
            float[] detector = PortableOcr.normalizeGray(input, 1, 1, 0, 0, 1, 1, 1, 1, 1, true);
            float[] recognizer = PortableOcr.normalizeGray(input, 1, 1, 0, 0, 1, 1, 1, 1, 1, false);
            for (int c = 0; c < 3; c++) {
                assertEquals(
                        Float.floatToRawIntBits((intensity / 255f - mean[c]) / std[c]),
                        Float.floatToRawIntBits(detector[c]));
                assertEquals(
                        Float.floatToRawIntBits((intensity / 255f - .5f) / .5f),
                        Float.floatToRawIntBits(recognizer[c]));
            }
        }
    }

    @Test
    public void inkBoundsPreserveBlankAndThresholdBoundary() {
        byte[] input = new byte[20 * 70];
        Arrays.fill(input, (byte) 255);
        var box = new OcrText.Box(0, 0, 20, 70);
        assertEquals(box, PortableOcr.inkBoundsGray(input, 20, box));
        for (int value : new int[] {191, 190, 189, 0, 255}) {
            Arrays.fill(input, (byte) 255);
            input[21 * 20 + 4] = input[39 * 20 + 15] = (byte) value;
            byte[] before = input.clone();
            var expected = PortableOcr.inkBounds(referenceArgb(input), 20, box);
            assertEquals(expected, PortableOcr.inkBoundsGray(input, 20, box));
            assertEquals(value < 190 ? new OcrText.Box(4, 21, 16, 40) : box, expected);
            assertArrayEquals(before, input);
        }
    }

    static void fill(byte[] gray, int width, int left, int top, int right, int bottom, int value) {
        for (int y = top; y < bottom; y++)
            for (int x = left; x < right; x++) gray[y * width + x] = (byte) value;
    }

    @Test
    public void stackedRowsPreserveSixPixelBandsGutterAndAspectBoundaries() {
        int[][] bands = {
            {20, 70, 5, 25, 40, 60},
            {20, 70, 5, 10, 40, 60},
            {20, 70, 5, 11, 18, 24},
            {20, 70, 5, 11, 17, 23},
            {20, 30, 2, 8, 12, 18},
            {20, 29, 2, 8, 12, 18},
            {20, 23, 2, 8, 12, 18},
            {20, 70, 0, 0, 0, 0}
        };
        for (int[] b : bands) {
            byte[] input = new byte[b[0] * b[1]];
            Arrays.fill(input, (byte) 255);
            fill(input, b[0], 5, b[2], 15, b[3], 189);
            fill(input, b[0], 5, b[4], 15, b[5], 0);
            byte[] before = input.clone();
            var box = new OcrText.Box(0, 0, b[0], b[1]);
            assertEquals(
                    PortableOcr.splitStackedRows(referenceArgb(input), b[0], box),
                    PortableOcr.splitStackedGrayRows(input, b[0], box));
            assertArrayEquals(before, input);
        }
    }

    private record Call(String kind, int width, int height, float[] input) {}

    private record Returned(float[][] value, float[][] before) {}

    private static float[][] copy(float[][] input) {
        float[][] result = new float[input.length][];
        for (int i = 0; i < input.length; i++) result[i] = input[i].clone();
        return result;
    }

    private static final class Mock implements PortableOcr.Inference {
        final float[][] map;
        final int mode;
        final List<Call> calls = new ArrayList<>();
        final List<Returned> returned = new ArrayList<>();
        int recognitions;

        Mock(float[][] map, int mode) {
            this.map = map;
            this.mode = mode;
        }

        private float[][] output(float[][] input) {
            float[][] result = copy(input);
            returned.add(new Returned(result, copy(result)));
            return result;
        }

        public float[][] detect(float[] input, int width, int height) {
            calls.add(new Call("detect", width, height, input.clone()));
            return output(map);
        }

        public float[][] recognize(float[] input, int width, int height) {
            calls.add(new Call("recognize", width, height, input.clone()));
            int[] sequence =
                    mode == 2
                            ? new int[] {2, 2, 0, 2, 0, 2}
                            : (recognitions++ % 2 == 0
                                    ? new int[] {1, 1, 0, 2, 0, 1, 0, 3, 0}
                                    : new int[] {3, 0, 2, 0, 1, 1, 0});
            float[][] result = new float[sequence.length][4];
            for (int t = 0; t < result.length; t++)
                result[t][sequence[t]] = mode == 1 ? .49f : .99f;
            return output(result);
        }

        public List<String> dictionary() {
            calls.add(new Call("dictionary", 0, 0, new float[0]));
            return List.of("", "A", " ", "?");
        }

        void assertOutputsPreserved() {
            for (Returned output : returned) {
                assertEquals(output.before.length, output.value.length);
                for (int row = 0; row < output.before.length; row++)
                    bits(output.before[row], output.value[row]);
            }
        }
    }

    @Test
    public void completeMockOcrAndEveryOrderedTensorMatchTheOriginal() throws Exception {
        for (int fixture = 0; fixture < 6; fixture++) {
            int width = fixture == 2 ? 20 : fixture == 3 ? 100 : 64;
            int height = fixture == 2 ? 70 : fixture == 3 ? 60 : 32;
            byte[] input = new byte[width * height];
            Arrays.fill(input, (byte) 255);
            float[][] map = new float[fixture == 3 ? 12 : 8][fixture == 3 ? 20 : 8];
            if (fixture == 3) {
                for (int y = 1; y < 4; y++) for (int x = 1; x < 6; x++) map[y][x] = .9f;
                for (int y = 7; y < 11; y++) for (int x = 12; x < 18; x++) map[y][x] = .9f;
                fill(input, width, 10, 5, 40, 22, 189);
                fill(input, width, 65, 35, 90, 55, 0);
            } else if (fixture != 0) {
                for (float[] row : map) Arrays.fill(row, .9f);
                if (fixture == 2) {
                    fill(input, width, 5, 5, 15, 25, 0);
                    fill(input, width, 5, 40, 15, 60, 189);
                } else fill(input, width, 8, 7, 56, 25, fixture == 5 ? 190 : 189);
            }
            int mode = fixture == 4 ? 1 : fixture == 5 ? 2 : 0;
            byte[] before = input.clone();
            int[] argb = referenceArgb(input), argbBefore = argb.clone();
            Mock original = new Mock(map, mode),
                    candidate = new Mock(map, mode),
                    automatic = new Mock(map, mode);
            OcrText expected = readArgbReference(original, argb, width, height);
            OcrText actual = new PortableOcr(candidate).readGray(input, width, height);
            assertEquals(expected, new PortableOcr(automatic).read(argb, width, height));
            assertCallsEqual(original, automatic);
            automatic.assertOutputsPreserved();
            assertEquals("all nested text, boxes and order", expected, actual);
            assertEquals(original.calls.size(), candidate.calls.size());
            for (int call = 0; call < original.calls.size(); call++) {
                Call a = original.calls.get(call), b = candidate.calls.get(call);
                assertEquals(a.kind, b.kind);
                assertEquals(a.width, b.width);
                assertEquals(a.height, b.height);
                bits(a.input, b.input);
            }
            if (fixture == 0 || fixture >= 4) assertTrue(actual.blocks().isEmpty());
            else assertFalse(actual.blocks().isEmpty());
            original.assertOutputsPreserved();
            candidate.assertOutputsPreserved();
            assertArrayEquals(before, input);
            assertArrayEquals(argbBefore, argb);
        }
    }

    @Test
    public void invalidGrayGuardsFailBeforeInference() {
        Mock mock = new Mock(new float[8][8], 0);
        for (int[] size : new int[][] {{0, 1}, {1, 0}, {2, 2}, {20_000_001, 1}})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PortableOcr(mock).readGray(new byte[1], size[0], size[1]));
        assertThrows(NullPointerException.class, () -> new PortableOcr(mock).readGray(null, 1, 1));
        assertTrue(mock.calls.isEmpty());
        int[][] invalid = {
            {0, 1, 0, 0, 1, 1, 1, 1, 1},
            {1, 1, -1, 0, 1, 1, 1, 1, 1},
            {1, 1, 0, 0, 2, 1, 1, 1, 1},
            {1, 1, 0, 0, 0, 1, 1, 1, 1},
            {1, 1, 0, 0, 1, 1, 0, 1, 1},
            {1, 1, 0, 0, 1, 1, 2, 1, 1},
            {1, 1, 0, 0, 1, 1, 3000, 2000, 3000},
            {2, 1, 0, 0, 1, 1, 1, 1, 1}
        };
        for (int[] c : invalid) {
            var expected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    PortableOcr.normalize(
                                            new int[] {-1},
                                            c[0],
                                            c[1],
                                            c[2],
                                            c[3],
                                            c[4],
                                            c[5],
                                            c[6],
                                            c[7],
                                            c[8],
                                            true));
            var actual =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    PortableOcr.normalizeGray(
                                            new byte[] {-1},
                                            c[0],
                                            c[1],
                                            c[2],
                                            c[3],
                                            c[4],
                                            c[5],
                                            c[6],
                                            c[7],
                                            c[8],
                                            true));
            assertEquals(expected.getMessage(), actual.getMessage());
        }
    }

    @Test
    public void incomingInterruptStopsGrayResizeAndReadWithoutClearingIt() {
        for (boolean read : new boolean[] {false, true}) {
            Mock mock = new Mock(new float[8][8], 0);
            Thread.currentThread().interrupt();
            try {
                assertThrows(
                        CancellationException.class,
                        () -> {
                            if (read) new PortableOcr(mock).readGray(new byte[4], 2, 2);
                            else
                                PortableOcr.normalizeGray(
                                        new byte[4], 2, 2, 0, 0, 2, 2, 3, 3, 4, false);
                        });
                assertTrue(Thread.currentThread().isInterrupted());
                assertTrue(mock.calls.isEmpty());
            } finally {
                Thread.interrupted();
            }
        }
    }

    private static OcrText readArgbReference(Mock inference, int[] input, int width, int height)
            throws Exception {
        var method =
                PortableOcr.class.getDeclaredMethod(
                        "readPixels", int[].class, byte[].class, int.class, int.class);
        method.setAccessible(true);
        return (OcrText) method.invoke(new PortableOcr(inference), input, null, width, height);
    }

    private static void assertCallsEqual(Mock expected, Mock actual) {
        assertEquals(expected.calls.size(), actual.calls.size());
        for (int call = 0; call < expected.calls.size(); call++) {
            Call a = expected.calls.get(call), b = actual.calls.get(call);
            assertEquals(a.kind, b.kind);
            assertEquals(a.width, b.width);
            assertEquals(a.height, b.height);
            bits(a.input, b.input);
        }
    }

    @Test
    public void anyColoredOrTranslucentPixelKeepsTheArgbPipeline() throws Exception {
        float[][] map = new float[8][8];
        for (float[] row : map) Arrays.fill(row, .9f);
        for (int index = 0; index < 9; index++) {
            for (int pixel : new int[] {0xff7f807f, 0x7f808080}) {
                int[] input = referenceArgb(gray(3, 3, index + 17));
                input[index] = pixel;
                int[] before = input.clone();
                Mock original = new Mock(map, 0), automatic = new Mock(map, 0);
                OcrText expected = readArgbReference(original, input, 3, 3);
                assertEquals(expected, new PortableOcr(automatic).read(input, 3, 3));
                assertCallsEqual(original, automatic);
                original.assertOutputsPreserved();
                automatic.assertOutputsPreserved();
                assertArrayEquals(before, input);
            }
        }
    }

    @Test
    public void incomingInterruptStopsAutomaticGrayReadWithoutClearingIt() {
        Mock mock = new Mock(new float[8][8], 0);
        Thread.currentThread().interrupt();
        try {
            assertThrows(
                    CancellationException.class,
                    () -> new PortableOcr(mock).read(new int[] {-1, -1, -1, -1}, 2, 2));
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(mock.calls.isEmpty());
        } finally {
            Thread.interrupted();
        }
    }
}
