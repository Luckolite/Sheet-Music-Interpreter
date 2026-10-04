// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class OcrCtcDecoderTest {
    @Test
    public void acceptsOnlyEndpointRoundingAndClampsReportedConfidence() {
        var result =
                OcrCtcDecoder.decode(
                        new float[][] {{-0.0000001f, Math.nextUp(1f)}}, List.of("", "f"), 0);
        assertEquals("f", result.text());
        assertEquals(1, result.confidence(), 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> OcrCtcDecoder.decode(new float[][] {{0, 1.01f}}, List.of("", "f"), 0));
    }

    private static final List<String> DICT = List.of("", "0", "o", "p", " ", "é");

    private static float[][] sequence(int... indexes) {
        float[][] result = new float[indexes.length][DICT.size()];
        for (int i = 0; i < indexes.length; i++) result[i][indexes[i]] = 1;
        return result;
    }

    @Test
    public void blankSeparatesRepeatedDynamics() {
        var result = OcrCtcDecoder.decode(sequence(3, 3, 0, 3, 3), DICT, 0);
        assertEquals("pp", result.text());
        assertEquals(2, result.tokens().size());
        assertEquals(0, result.tokens().get(0).startStep());
        assertEquals(2, result.tokens().get(0).endStep());
        assertEquals(3, result.tokens().get(1).startStep());
        assertEquals(5, result.tokens().get(1).endStep());
    }

    @Test
    public void noGuessedNumericCorrectionsOrTrim() {
        assertEquals(" 0oé ", OcrCtcDecoder.decode(sequence(4, 1, 2, 5, 4), DICT, 0).text());
    }

    @Test
    public void allBlankDoesNotInventTextOrConfidence() {
        var result = OcrCtcDecoder.decode(sequence(0, 0), DICT, 0);
        assertEquals("", result.text());
        assertTrue(result.tokens().isEmpty());
        assertEquals(0, result.confidence(), 0);
    }

    @Test
    public void rejectsMismatchedVocabularyAndNonFiniteProbabilities() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OcrCtcDecoder.decode(new float[][] {{1}}, DICT, 0));
        var bad = sequence(1);
        bad[0][0] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> OcrCtcDecoder.decode(bad, DICT, 0));
        assertThrows(
                IllegalArgumentException.class, () -> OcrCtcDecoder.decode(sequence(1), DICT, -1));
    }

    @Test
    public void longRunsKeepInitialConfidenceAndExactSpanBoundaries() {
        List<String> dictionary = List.of("", "p", "é", " ");
        float[][] probabilities = new float[5006][dictionary.size()];
        for (int t = 0; t < 5000; t++) probabilities[t][1] = t == 0 ? .625f : .875f;
        probabilities[5000][2] = .75f;
        probabilities[5001][2] = .95f;
        probabilities[5002][0] = 1;
        probabilities[5003][2] = .5f;
        probabilities[5004][3] = Math.nextUp(1f);
        probabilities[5005][0] = 1;
        var result = OcrCtcDecoder.decode(probabilities, dictionary, 0);
        assertEquals("péé ", result.text());
        assertEquals(
                List.of(
                        new OcrCtcDecoder.Token("p", 0, 5000, .625f),
                        new OcrCtcDecoder.Token("é", 5000, 5002, .75f),
                        new OcrCtcDecoder.Token("é", 5003, 5004, .5f),
                        new OcrCtcDecoder.Token(" ", 5004, 5005, 1f)),
                result.tokens());
        assertEquals(
                Float.floatToRawIntBits(.71875f), Float.floatToRawIntBits(result.confidence()));
        assertEquals(Math.nextUp(1f), probabilities[5004][3], 0);
        probabilities[5005][3] = Float.NaN;
        assertThrows(
                IllegalArgumentException.class,
                () -> OcrCtcDecoder.decode(probabilities, dictionary, 0));
    }
}
