// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PaddedEndingSelectorTest {
    static byte[] page(boolean ink) {
        byte[] g = new byte[600 * 240];
        Arrays.fill(g, (byte) 255);
        for (int y = 40; y < 42; y++) for (int x = 306; x < 552; x++) g[y * 600 + x] = 0;
        for (int y = 40; y < 76; y++) for (int x = 306; x < 308; x++) g[y * 600 + x] = 0;
        if (ink) {
            for (int y = 49; y < 73; y++) for (int x = 316; x < 320; x++) g[y * 600 + x] = 0;
            for (int y = 70; y < 74; y++) for (int x = 324; x < 328; x++) g[y * 600 + x] = 0;
        }
        return g;
    }

    static List<PrintedEndingBrackets.Mark> read(byte[] g, String text, float left) {
        return PrintedEndingBrackets.detect(
                g,
                600,
                240,
                List.of(new PlayingTechniqueDetector.Staff(120, 168, 12, 0, 1)),
                List.of(
                        new MeasureRegion(.1f, .5f, .45f, .8f),
                        new MeasureRegion(.51f, .92f, .45f, .8f)),
                List.of(
                        new PlayingTechniqueDetector.Word(
                                text, left / 600f, 38 / 240f, 335 / 600f, 82 / 240f)));
    }

    @Test
    public void paddedSelectorUsesInk() {
        var m = read(page(true), "1.", 299);
        assertEquals(1, m.size());
        assertEquals(1, m.get(0).start());
        assertEquals(2, m.get(0).end());
        assertEquals(List.of(1), m.get(0).passes());
    }

    @Test
    public void bracketAloneIsNotSelector() {
        assertTrue(read(page(false), "1.", 299).isEmpty());
    }

    @Test
    public void unrelatedWordRejected() {
        assertTrue(read(page(true), "mf", 299).isEmpty());
    }

    @Test
    public void distantBoxRejected() {
        assertTrue(read(page(true), "1.", 280).isEmpty());
    }

    @Test
    public void pixelsRemainUnchanged() {
        byte[] g = page(true), old = g.clone();
        read(g, "1.", 299);
        assertArrayEquals(old, g);
    }
}
