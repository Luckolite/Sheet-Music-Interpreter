// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.File;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original music-font meters with deliberately shifted staff metadata. */
public class LocalMeterFallbackTest {
    static String read(int shift) throws Exception {
        var inference =
                new PortableOcr.Inference() {
                    public float[][] detect(float[] chw, int w, int h) {
                        return new float[h][w];
                    }

                    public float[][] recognize(float[] chw, int w, int h) {
                        return new float[][] {{1, 0}};
                    }

                    public List<String> dictionary() {
                        return List.of("4");
                    }
                };
        var ocr =
                new MusicalOcr(
                        inference, new MeterFontMatcher(new File("java/assets/Bravura.otf")));
        var m =
                MusicalOcr.class.getDeclaredMethod(
                        "meter",
                        byte[].class,
                        int.class,
                        int.class,
                        MeterChangeDetector.Crop.class);
        m.setAccessible(true);
        var reading =
                m.invoke(
                        ocr,
                        LocalMeterFontTest.page(52),
                        640,
                        300,
                        new MeterChangeDetector.Crop(300, 94, 340, 165, 100 + shift, 14));
        var text = reading.getClass().getDeclaredMethod("text");
        text.setAccessible(true);
        return (String) text.invoke(reading);
    }

    @Test
    public void fallbackRecoversLowerHint() throws Exception {
        assertEquals("4/4", read(3));
    }

    @Test
    public void fallbackRecoversUpperHint() throws Exception {
        assertEquals("4/4", read(-3));
    }

    @Test
    public void successfulReadingRemainsSuccessful() throws Exception {
        assertEquals("4/4", read(0));
    }
}
