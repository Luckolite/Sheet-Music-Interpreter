// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public class DecoderRasterBoundaryTest {
    @Test
    public void correctedDecoderRetainsOriginalAnnotationHash() throws Exception {
        byte[] labels = new byte[128 * 96];
        byte[] original = new byte[labels.length], decoder = new byte[labels.length];
        Arrays.fill(original, (byte) 180);
        Arrays.fill(decoder, (byte) 255);
        byte[] before = original.clone();
        var score =
                SheetInterpreter.analyze(
                        labels,
                        original,
                        128,
                        96,
                        SheetInterpreter.Annotations.EMPTY,
                        null,
                        0,
                        decoder);
        assertEquals(
                ScoreClockContextCapture.digest(original),
                score.clockContextSource().origin().annotationRasterSha256());
        assertEquals(
                ScoreClockContextCapture.digest(decoder),
                score.clockContextSource().origin().rasterSha256());
        assertArrayEquals(before, original);
    }

    @Test
    public void mismatchedDecoderRejectsBeforeDecoding() throws Exception {
        try {
            SheetInterpreter.analyze(
                    new byte[64],
                    new byte[64],
                    8,
                    8,
                    SheetInterpreter.Annotations.EMPTY,
                    null,
                    0,
                    new byte[63]);
            fail("Accepted mismatched decoder raster");
        } catch (IllegalArgumentException expected) {
        }
    }
}
