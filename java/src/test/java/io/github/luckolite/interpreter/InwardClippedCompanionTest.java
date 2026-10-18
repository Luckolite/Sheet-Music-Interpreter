// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Raw-identical original raster with a distinct partial OCR hypothesis. */
public final class InwardClippedCompanionTest {
    private static byte[] plane(boolean boxed) throws Exception {
        String n =
                "/inward_clipped_companion/smooth-inward-clipped"
                        + (boxed ? ".gray" : ".base.gray");
        try (var in = InwardClippedCompanionTest.class.getResourceAsStream(n)) {
            assertNotNull(n, in);
            byte[] p = in.readAllBytes();
            assertEquals(320 * 256, p.length);
            return p;
        }
    }

    private static List<ScoreDynamicChange> actual(boolean boxed, String text) throws Exception {
        byte[] p = plane(boxed), before = p.clone();
        var w =
                new PlayingTechniqueDetector.Word(
                        text, 145.25f / 320, 78.5f / 256, 154.25f / 320, 106.5f / 256);
        var s = new PlayingTechniqueDetector.Staff(150.5f, 198.5f, 12, 0, 1);
        var result =
                ScoreDynamicsDetector.detectDirectionCore(
                                List.of(w),
                                List.of(s),
                                List.of(new MeasureRegion(.05f, .95f, .4f, .85f)),
                                List.of(),
                                p,
                                320,
                                256,
                                p,
                                true)
                        .changes();
        assertArrayEquals(before, p);
        return result.stream().filter(x -> x.direction() == 0).toList();
    }

    @Test
    public void inwardFrameClippedWordRemainsRehearsal() throws Exception {
        for (String text : List.of("F", "f"))
            assertTrue(text + " complete scene rehearsal", actual(true, text).isEmpty());
    }

    @Test
    public void rawIdenticalUnboxedGlyphProvesAdmissionSensitivity() throws Exception {
        for (String text : List.of("F", "f")) {
            var found = actual(false, text);
            assertEquals(1, found.size());
            assertEquals(3f, found.get(0).decibels(), 0f);
            assertEquals(0, found.get(0).measureIndex());
        }
    }
}
