// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.Random;

/** Original generated tensors compare full OCR and explicitly selected line-only recognition. */
public final class OnnxRecognizerOnlyParity {
    private static void same(float[][] expected, float[][] actual) {
        if (expected.length != actual.length)
            throw new AssertionError("Recognizer time steps changed");
        for (int row = 0; row < expected.length; row++) {
            if (expected[row].length != actual[row].length)
                throw new AssertionError("Vocabulary changed");
            for (int column = 0; column < expected[row].length; column++)
                if (Float.floatToRawIntBits(expected[row][column])
                        != Float.floatToRawIntBits(actual[row][column]))
                    throw new AssertionError("Probability changed at " + row + "," + column);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException(
                    "Expected detector.onnx recognizer.onnx; trusted dictionary adjacent to recognizer");
        try (var full = new OnnxOcrInference(args[0], args[1]);
                var only = OnnxOcrInference.recognizerOnly(args[1])) {
            if (!full.dictionary().equals(only.dictionary()))
                throw new AssertionError("Dictionary changed");
            int cases = 0;
            for (int width : new int[] {320, 512, 960})
                for (int pattern = 0; pattern < 3; pattern++) {
                    Random random = new Random(0x93117L + width + pattern);
                    float[] input = new float[3 * width * 48];
                    for (int index = 0; index < input.length; index++)
                        input[index] =
                                pattern == 0
                                        ? 1f
                                        : pattern == 1
                                                ? (index % 17 < 3 ? -1f : 1f)
                                                : random.nextFloat() * 2 - 1;
                    float[] original = input.clone();
                    float[][] expected = full.recognize(input, width, 48);
                    var ctc = OcrCtcDecoder.decode(expected, full.dictionary(), 0);
                    for (int repeat = 0; repeat < 2; repeat++) {
                        float[][] actual = only.recognize(input, width, 48);
                        same(expected, actual);
                        if (!ctc.equals(OcrCtcDecoder.decode(actual, only.dictionary(), 0)))
                            throw new AssertionError("Text, tokens or confidence changed");
                        if (!Arrays.equals(original, input))
                            throw new AssertionError("Caller tensor changed");
                    }
                    cases++;
                }
            try {
                only.detect(new float[0], 1, 1);
                throw new AssertionError("Line-only detector accepted");
            } catch (IllegalStateException expected) {
            }
            System.out.println(
                    "PASS "
                            + cases
                            + " original recognizer-only tensors: probability bits, repeated calls, dictionary, CTC, confidence and caller input exact");
        }
    }
}
