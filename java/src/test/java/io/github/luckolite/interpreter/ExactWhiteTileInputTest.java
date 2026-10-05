// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original tensors protect exact equality across all channels and padded positions. */
public final class ExactWhiteTileInputTest {
    @Test
    public void checksEveryChannelAndPaddingPosition() {
        int plane = 320 * 320;
        float[] input = new float[3 * plane];
        Arrays.fill(input, 255f);
        assertTrue(ExactWhiteTileInput.matches(input));
        int[] positions = {0, 319, plane - 1, plane, 2 * plane - 1, 2 * plane, input.length - 1};
        float[] different = {
            0f,
            -0f,
            254f,
            Math.nextDown(255f),
            Math.nextUp(255f),
            Float.NaN,
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY
        };
        for (int position : positions) {
            for (float value : different) {
                input[position] = value;
                assertFalse(
                        "position=" + position + " value=" + value,
                        ExactWhiteTileInput.matches(input));
                input[position] = 255f;
            }
        }
        assertTrue(ExactWhiteTileInput.matches(input));
    }

    @Test
    public void inputValuesAndFloatBitsAreUnchanged() {
        float[] input = new float[3 * 320 * 320];
        Arrays.fill(input, 255f);
        float[] before = input.clone();
        assertTrue(ExactWhiteTileInput.matches(input));
        assertArrayEquals(before, input, 0f);
        input[input.length - 1] = Float.intBitsToFloat(0x7fc00425);
        int[] bits = new int[input.length];
        for (int i = 0; i < bits.length; i++) bits[i] = Float.floatToRawIntBits(input[i]);
        assertFalse(ExactWhiteTileInput.matches(input));
        for (int i = 0; i < bits.length; i++)
            assertEquals(bits[i], Float.floatToRawIntBits(input[i]));
    }

    @Test
    public void copiedPlanesMatchTheAllChannelCheckAndPreserveCallerBits() {
        int plane = 4 * 4;
        // A 3-by-2 crop leaves the fourth column and last two rows as padding.
        int[] positions = {0, 1, 6, 7, 11, plane - 1};
        float[] values = {
            255f,
            0f,
            -0f,
            254f,
            Math.nextDown(255f),
            Math.nextUp(255f),
            Float.intBitsToFloat(0x7fc00425),
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.MIN_VALUE
        };
        for (int position : positions)
            for (float value : values) {
                float[] input = new float[3 * plane];
                Arrays.fill(input, 255f);
                input[position] = value;
                System.arraycopy(input, 0, input, plane, plane);
                System.arraycopy(input, 0, input, 2 * plane, plane);
                int[] bits = new int[input.length];
                for (int i = 0; i < bits.length; i++) bits[i] = Float.floatToRawIntBits(input[i]);
                assertEquals(
                        ExactWhiteTileInput.matches(input),
                        ExactWhiteTileInput.matchesReplicatedFirstPlane(input, plane));
                for (int i = 0; i < bits.length; i++)
                    assertEquals(bits[i], Float.floatToRawIntBits(input[i]));
            }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExactWhiteTileInput.matchesReplicatedFirstPlane(
                                new float[3 * plane + 1], plane));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExactWhiteTileInput.matchesReplicatedFirstPlane(
                                new float[3], Integer.MAX_VALUE));
    }

    @Test
    public void whitePredictionRequiresCompleteOutputAndRetainsAllValidClasses() {
        int plane = 320 * 320;
        assertFalse(ExactWhiteTileInput.cacheablePrediction(null, plane));
        assertFalse(ExactWhiteTileInput.cacheablePrediction(new long[plane - 1], plane));
        assertFalse(ExactWhiteTileInput.cacheablePrediction(new long[plane + 1], plane));
        long[] labels = new long[plane];
        for (int i = 0; i < plane; i++) labels[i] = i % 6;
        long[] before = labels.clone();
        assertTrue(ExactWhiteTileInput.cacheablePrediction(labels, plane));
        assertArrayEquals(before, labels);
    }

    @Test
    public void whitePredictionChecksPaddedPositionsAndFullInt64WithoutMutation() {
        int plane = 320 * 320;
        int[] positions = {0, 319, 320, plane / 2, plane - 1};
        // 256/261 truncate to valid byte classes; reuse must reject their complete INT64 values.
        long[] values = {-1, 6, 256, 261, Long.MIN_VALUE, Long.MAX_VALUE};
        for (int position : positions)
            for (long value : values) {
                long[] labels = new long[plane];
                Arrays.fill(labels, 5);
                labels[position] = value;
                long[] before = labels.clone();
                assertFalse(
                        "position=" + position + " value=" + value,
                        ExactWhiteTileInput.cacheablePrediction(labels, plane));
                assertArrayEquals(before, labels);
            }
    }

    @Test
    public void onlyCompleteInBoundsTilesCanSkipTheWhiteFill() {
        byte[] full = new byte[1024 * 768];
        assertTrue(ExactWhiteTileInput.fullTileOverwritesPlane(full, 1024, 768, 192, 192, 320));
        assertTrue(ExactWhiteTileInput.fullTileOverwritesPlane(full, 1024, 768, 704, 448, 320));
        assertFalse(ExactWhiteTileInput.fullTileOverwritesPlane(full, 1024, 768, 705, 448, 320));
        assertFalse(ExactWhiteTileInput.fullTileOverwritesPlane(full, 1024, 768, 704, 449, 320));
        assertFalse(ExactWhiteTileInput.fullTileOverwritesPlane(full, 1024, 768, -1, 0, 320));
        assertFalse(ExactWhiteTileInput.fullTileOverwritesPlane(full, 1024, 768, 0, -1, 320));
        assertFalse(ExactWhiteTileInput.fullTileOverwritesPlane(null, 320, 320, 0, 0, 320));
        assertFalse(ExactWhiteTileInput.fullTileOverwritesPlane(new byte[17], 320, 320, 0, 0, 320));
        assertFalse(
                ExactWhiteTileInput.fullTileOverwritesPlane(
                        new byte[0], Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, 320));
        assertFalse(ExactWhiteTileInput.fullTileOverwritesPlane(full, 1024, 768, 0, 0, 0));
    }

    @Test
    public void paddingGuardRetainsCompleteTensorBitsAndFailureSideEffects() {
        int[][] shapes = {
            {320, 320, 0, 0}, {1024, 768, 192, 192}, {513, 384, 193, 64},
            {32, 19, 0, 0}, {319, 320, 0, 0}, {320, 319, 0, 0},
            {513, 384, 384, 192}, {320, 320, 320, 320}, {0, 0, 0, 0},
            {-1, -1, 0, 0}, {320, 320, -1, 0}, {320, 320, 0, -1}
        };
        for (int[] shape : shapes) {
            byte[] gray = new byte[Math.max(0, shape[0] * shape[1])];
            for (int i = 0; i < gray.length; i++) gray[i] = (byte) (i * 37 + (i >>> 9));
            assertPackingBits(gray, shape[0], shape[1], shape[2], shape[3]);
        }
        assertPackingBits(null, 320, 320, 0, 0);
        assertPackingBits(new byte[17], 320, 320, 0, 0);
    }

    private static void assertPackingBits(byte[] gray, int width, int height, int left, int top) {
        float[] original = new float[3 * 320 * 320 + 13];
        int[] dirty = {0, 0x80000000, 0x7fc00425, 0x7f800000, 0xff800000, 0x3f123456};
        for (int i = 0; i < original.length; i++)
            original[i] = Float.intBitsToFloat(dirty[i % dirty.length]);
        float[] optimized = original.clone();
        float[] before = original.clone();
        byte[] saved = gray == null ? null : gray.clone();
        Class<?> first = packedFailure(original, gray, width, height, left, top, false);
        Class<?> second = packedFailure(optimized, gray, width, height, left, top, true);
        assertEquals(first, second);
        assertArrayEquals(saved, gray);
        for (int i = 0; i < original.length; i++)
            assertEquals(
                    Float.floatToRawIntBits(original[i]), Float.floatToRawIntBits(optimized[i]));
        for (int i = 3 * 320 * 320; i < original.length; i++)
            assertEquals(Float.floatToRawIntBits(before[i]), Float.floatToRawIntBits(optimized[i]));
    }

    private static Class<?> packedFailure(
            float[] input,
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            boolean optimized) {
        try {
            int plane = 320 * 320;
            if (input.length < 3 * plane)
                throw new IllegalArgumentException("Input tensor is too small");
            int rows = Math.max(0, Math.min(320, height - top));
            int columns = Math.max(0, Math.min(320, width - left));
            if (!optimized
                    || !ExactWhiteTileInput.fullTileOverwritesPlane(
                            gray, width, height, left, top, 320))
                Arrays.fill(input, 0, plane, 255f);
            // Original packing loop and copies; only the production fill predicate differs.
            for (int y = 0; y < rows; y++) {
                int source = (top + y) * width + left, destination = y * 320;
                for (int x = 0; x < columns; x++) input[destination + x] = gray[source + x] & 255;
            }
            System.arraycopy(input, 0, input, plane, plane);
            System.arraycopy(input, 0, input, 2 * plane, plane);
            return null;
        } catch (RuntimeException | Error failure) {
            return failure.getClass();
        }
    }
}
