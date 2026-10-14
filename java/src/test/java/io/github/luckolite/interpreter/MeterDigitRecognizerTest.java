// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural glyph slots with explicit model disagreements and blank evidence. */
public class MeterDigitRecognizerTest {
    static final int W = 30, H = 44, M = 22;
    static final float G = 10;

    static int[] pixels() {
        int[] p = new int[W * H];
        Arrays.fill(p, 0xffffffff);
        for (int t : new int[] {3, 25})
            for (int y = t; y < t + 16; y++)
                for (int x = 9; x < 17; x++) if (y < t + 3 || x >= 13) p[y * W + x] = 0xff000000;
        return p;
    }

    static class Model implements PortableOcr.Inference {
        int calls;
        float confidence = 1;
        String[] text = {"7", "7", "7", "4", "4", "4"};

        public List<String> dictionary() {
            return List.of("", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "x");
        }

        public float[][] detect(float[] x, int w, int h) {
            throw new AssertionError("Known slot must not use detector");
        }

        public float[][] recognize(float[] x, int w, int h) {
            assertEquals(48, h);
            assertEquals(w * h * 3, x.length);
            String s = text[Math.min(calls++, text.length - 1)];
            float[][] r = new float[s.length() * 2 + 1][12];
            for (int i = 0; i < r.length; i++) r[i][0] = 1;
            for (int i = 0; i < s.length(); i++) {
                Arrays.fill(r[i * 2 + 1], 0);
                r[i * 2 + 1][dictionary().indexOf(String.valueOf(s.charAt(i)))] = confidence;
            }
            return r;
        }
    }

    static String read(int[] p, String lower, Model m) throws Exception {
        return MeterDigitRecognizer.read(p, W, H, M, G, lower, m);
    }

    @Test
    public void missingDetectionUsesThreeAgreeingMarginsAndBothSlots() throws Exception {
        var m = new Model();
        assertEquals("7/4", read(pixels(), "4", m));
        assertEquals(6, m.calls);
    }

    @Test
    public void numeratorMarginConflictAbstains() throws Exception {
        var m = new Model();
        m.text[1] = "1";
        assertEquals("", read(pixels(), "4", m));
    }

    @Test
    public void denominatorDisagreementAbstains() throws Exception {
        assertEquals("", read(pixels(), "8", new Model()));
    }

    @Test
    public void lowConfidenceAbstains() throws Exception {
        var m = new Model();
        m.confidence = .97f;
        assertEquals("", read(pixels(), "4", m));
    }

    @Test
    public void unsupportedNumeratorAbstains() throws Exception {
        var m = new Model();
        m.text = new String[] {"33", "33", "33", "4", "4", "4"};
        assertEquals("", read(pixels(), "4", m));
    }

    @Test
    public void twoDigitNumeratorIsNotRestrictedToSeven() throws Exception {
        var m = new Model();
        m.text = new String[] {"12", "12", "12", "8", "8", "8"};
        assertEquals("12/8", read(pixels(), "8", m));
    }

    @Test
    public void lettersAreNotCoercedToDigits() throws Exception {
        var m = new Model();
        m.text[0] = "x";
        assertEquals("", read(pixels(), "4", m));
    }

    @Test
    public void blankPixelsDoNotInvokeInference() throws Exception {
        var m = new Model();
        int[] p = pixels();
        Arrays.fill(p, 0xffffffff);
        assertEquals("", read(p, "4", m));
        assertEquals(0, m.calls);
    }

    @Test
    public void smallSpeckCannotBecomeAMeter() throws Exception {
        var m = new Model();
        int[] p = pixels();
        Arrays.fill(p, 0, M * W, 0xffffffff);
        p[W * 10 + 10] = 0xff000000;
        assertEquals("", read(p, "4", m));
        assertEquals(0, m.calls);
    }

    @Test
    public void missingCorroboratedLowerDoesNotInvokeInference() throws Exception {
        var m = new Model();
        assertEquals("", read(pixels(), "", m));
        assertEquals(0, m.calls);
    }

    @Test
    public void unsupportedDenominatorDoesNotInvokeInference() throws Exception {
        var m = new Model();
        assertEquals("", read(pixels(), "3", m));
        assertEquals(0, m.calls);
    }

    @Test
    public void pixelsRemainUnchanged() throws Exception {
        int[] p = pixels(), copy = p.clone();
        read(p, "4", new Model());
        assertArrayEquals(copy, p);
    }
}
