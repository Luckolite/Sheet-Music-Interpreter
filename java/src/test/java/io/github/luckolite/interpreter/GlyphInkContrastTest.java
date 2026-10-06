// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class GlyphInkContrastTest {
    private float[] shape(float paper, float ink) {
        float[] values = new float[100];
        Arrays.fill(values, paper);
        for (int i = 0; i < 35; i++) values[i] = ink;
        values[35] = (paper + ink) * .5f;
        return values;
    }

    @Test
    public void blackPrintOnWhitePaperUnchanged() {
        float[] m = GlyphInkContrast.mask(shape(255, 0));
        assertEquals(1, m[0], 0);
        assertEquals(.5, m[35], .001);
        assertEquals(0, m[99], 0);
    }

    @Test
    public void grayPaperDoesNotFillGlyphBackground() {
        float[] m = GlyphInkContrast.mask(shape(185, 45));
        assertEquals(1, m[0], 0);
        assertEquals(.5, m[35], .001);
        assertEquals(0, m[99], 0);
    }

    @Test
    public void fadedInkRetainsSoftEdges() {
        float[] m = GlyphInkContrast.mask(shape(246, 186));
        assertEquals(1, m[0], 0);
        assertEquals(.5, m[35], .001);
    }

    @Test
    public void paperTextureDoesNotBecomeCharacter() {
        float[] m = GlyphInkContrast.mask(shape(171, 155));
        for (float value : m) assertEquals(0, value, 0);
    }

    @Test
    public void emptyPatchIsEmpty() {
        float[] values = new float[200];
        Arrays.fill(values, 190);
        for (float value : GlyphInkContrast.mask(values)) assertEquals(0, value, 0);
    }

    @Test
    public void quantileFirstCrossingKeepsFractionalCallerSamples() {
        float[] values = new float[20];
        Arrays.fill(values, 200.5f);
        values[0] = 0;
        values[1] = 0;
        values[2] = 31.75f;
        int[] before = rawBits(values);
        float[] first = GlyphInkContrast.mask(values);
        assertNotSame(values, first);
        for (int i = 0; i < values.length; i++)
            assertEquals(
                    Float.floatToRawIntBits(Math.max(0, Math.min(1, (201 - values[i]) / 201f))),
                    Float.floatToRawIntBits(first[i]));
        assertArrayEquals(before, rawBits(values));
        values[1] = 200.5f;
        before = rawBits(values);
        float[] changed = GlyphInkContrast.mask(values);
        assertNotSame(first, changed);
        for (int i = 0; i < values.length; i++)
            assertEquals(
                    Float.floatToRawIntBits(Math.max(0, Math.min(1, (201 - values[i]) / 169f))),
                    Float.floatToRawIntBits(changed[i]));
        assertArrayEquals(before, rawBits(values));
    }

    @Test
    public void nonfiniteEmptyAndRangeBoundaryKeepCallerBits() {
        float[] values = {
            Float.intBitsToFloat(0x7fc12345),
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            -0f,
            0f,
            31.5f,
            32.5f,
            255f,
            127.5f,
            200.5f
        };
        int[] before = rawBits(values);
        float[] mask = GlyphInkContrast.mask(values);
        assertTrue(Float.isNaN(mask[0]));
        for (int i = 1; i < values.length; i++)
            assertEquals(
                    Float.floatToRawIntBits(Math.max(0, Math.min(1, (255 - values[i]) / 255f))),
                    Float.floatToRawIntBits(mask[i]));
        assertArrayEquals(before, rawBits(values));
        float[] empty = new float[0];
        float[] emptyMask = GlyphInkContrast.mask(empty);
        assertEquals(0, emptyMask.length);
        assertNotSame(empty, emptyMask);
        for (int paper : new int[] {31, 32}) {
            float[] edge = new float[20];
            Arrays.fill(edge, paper);
            edge[0] = edge[1] = 0;
            float[] result = GlyphInkContrast.mask(edge);
            for (int i = 0; i < result.length; i++)
                assertEquals(
                        Float.floatToRawIntBits(paper == 32 && i < 2 ? 1f : 0f),
                        Float.floatToRawIntBits(result[i]));
        }
    }

    private static int[] rawBits(float[] values) {
        int[] bits = new int[values.length];
        for (int i = 0; i < values.length; i++) bits[i] = Float.floatToRawIntBits(values[i]);
        return bits;
    }
}
