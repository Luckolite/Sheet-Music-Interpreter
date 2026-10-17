// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;

/** Original adapter-boundary controls; records real portable OCR inputs without inventing tokens. */
public class IsolatedMeterOcrAdapterTest {
    static final class Capture implements PortableOcr.Inference {
        final List<Boolean> blankCorners = new ArrayList<>();
        final List<Float> centerInk = new ArrayList<>();

        public float[][] detect(float[] chw, int w, int h) {
            blankCorners.add(
                    chw[0] > 1 && chw[w - 1] > 1 && chw[(h - 1) * w] > 1 && chw[h * w - 1] > 1);
            centerInk.add(chw[(h / 2) * w + w / 2]);
            return new float[1][1];
        }

        public float[][] recognize(float[] chw, int w, int h) {
            throw new AssertionError("No fake recognition");
        }

        public List<String> dictionary() {
            return List.of("");
        }
    }

    static void digits(Capture capture, int[] p, int w, int h, int top, int bottom, int scale)
            throws Exception {
        var ocr =
                new MusicalOcr(capture, new MeterFontMatcher(new File("java/assets/Bravura.otf")));
        Method m =
                MusicalOcr.class.getDeclaredMethod(
                        "digits",
                        int[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        assertEquals("", m.invoke(ocr, p, w, h, top, bottom, scale));
    }

    @Test
    public void actualIsolatedDigitAdapterAddsContextAtEveryOcrScale() throws Exception {
        int w = 7, h = 19;
        int[] p = new int[w * h];
        Arrays.fill(p, 0xff000000);
        int[] before = p.clone();
        var capture = new Capture();
        for (int scale : new int[] {3, 4, 5}) digits(capture, p, w, h, 0, h, scale);
        assertEquals(3, capture.blankCorners.size());
        for (boolean blank : capture.blankCorners) assertTrue("Missing blank OCR context", blank);
        for (float center : capture.centerInk) assertTrue("Owned ink must survive", center < -1);
        assertArrayEquals(before, p);
    }

    @Test
    public void actualDenominatorAdapterExcludesTheUpperOwnedBand() throws Exception {
        int w = 9, h = 24;
        int[] p = new int[w * h];
        Arrays.fill(p, 0xff000000);
        Arrays.fill(p, w * 12, p.length, 0xffe0e0e0);
        int[] before = p.clone();
        var capture = new Capture();
        digits(capture, p, w, h, 12, 24, 4);
        assertEquals(1, capture.centerInk.size());
        assertTrue("Denominator must retain its own gray", capture.centerInk.get(0) > 1);
        assertTrue(capture.blankCorners.get(0));
        assertArrayEquals(before, p);
    }
}
