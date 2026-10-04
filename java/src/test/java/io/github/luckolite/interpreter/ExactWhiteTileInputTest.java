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
}
