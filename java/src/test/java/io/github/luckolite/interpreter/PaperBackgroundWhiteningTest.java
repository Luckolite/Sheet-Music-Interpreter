// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public class PaperBackgroundWhiteningTest {
    @Test
    public void cleanInkBypassesModelWithoutChangingSource() throws Exception {
        byte[] source = new byte[128 * 96];
        Arrays.fill(source, (byte) 255);
        for (int y = 20; y < 80; y += 12)
            Arrays.fill(source, y * 128 + 10, y * 128 + 118, (byte) 0);
        byte[] before = source.clone();
        var result =
                PaperBackgroundWhitening.apply(
                        source,
                        128,
                        96,
                        (features, w, h) -> {
                            throw new AssertionError("Clean page invoked model");
                        });
        assertSame(source, result.decoderGray());
        assertFalse(result.changed());
        assertEquals(result.rawSha256(), result.decoderSha256());
        assertArrayEquals(before, source);
    }

    @Test
    public void restoresKnownPaperAndKeepsOriginalInk() throws Exception {
        byte[] source = new byte[96 * 64];
        Arrays.fill(source, (byte) 204);
        source[3000] = 102;
        byte[] before = source.clone();
        var result =
                PaperBackgroundWhitening.apply(
                        source,
                        96,
                        64,
                        (features, w, h) -> {
                            assertEquals(2 * w * h, features.length);
                            assertEquals(.8f, features[0], .00001f);
                            return fields(w, h, .8f, .5f);
                        });
        assertTrue(result.changed());
        assertEquals(255, result.decoderGray()[0] & 255);
        assertEquals(0, result.decoderGray()[3000] & 255);
        assertArrayEquals(before, source);
        assertNotEquals(result.rawSha256(), result.decoderSha256());
    }

    @Test
    public void validatesDimensionsBeforeInference() throws Exception {
        try {
            PaperBackgroundWhitening.apply(
                    new byte[2],
                    1,
                    3,
                    (f, w, h) -> {
                        throw new AssertionError();
                    });
            fail("Accepted mismatched raster");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void rejectsNonFiniteModelOutput() throws Exception {
        byte[] source = new byte[64];
        Arrays.fill(source, (byte) 180);
        try {
            PaperBackgroundWhitening.apply(source, 8, 8, (f, w, h) -> fields(w, h, Float.NaN, .8f));
            fail("Accepted non-finite field");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void rasterHashIncludesDimensions() throws Exception {
        byte[] source = new byte[64];
        assertNotEquals(
                PaperBackgroundWhitening.rasterSha256(source, 8, 8),
                PaperBackgroundWhitening.rasterSha256(source, 4, 16));
    }

    private static PaperBackgroundWhitening.Fields fields(int w, int h, float p, float c) {
        float[][] paper = new float[h][w], contrast = new float[h][w];
        for (int y = 0; y < h; y++) {
            Arrays.fill(paper[y], p);
            Arrays.fill(contrast[y], c);
        }
        return new PaperBackgroundWhitening.Fields(paper, contrast);
    }
}
