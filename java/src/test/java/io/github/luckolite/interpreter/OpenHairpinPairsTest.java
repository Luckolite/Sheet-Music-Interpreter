// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpenHairpinPairsTest {
    final int W = 300, H = 200;
    final byte[] g = new byte[W * H];
    final List<PlayingTechniqueDetector.Staff> staffs =
            List.of(new PlayingTechniqueDetector.Staff(20, 84, 16, 0, 1));

    public OpenHairpinPairsTest() {
        Arrays.fill(g, (byte) 255);
    }

    void line(int y0, int y1) {
        for (int x = 40; x <= 240; x++) {
            int y = Math.round(y0 + (y1 - y0) * (x - 40) / 200f);
            g[y * W + x] = 0;
            g[(y + 1) * W + x] = 0;
        }
    }

    List<OpenHairpinPairs.Shape> read() {
        return OpenHairpinPairs.detect(g, W, H, staffs);
    }

    @Test
    public void openTipDiminuendo() {
        line(110, 113);
        line(126, 123);
        byte[] before = g.clone();
        assertEquals(-1, read().get(0).direction());
        assertEquals(1, read().size());
        assertArrayEquals(before, g);
    }

    @Test
    public void crossedStemDoesNotEraseWedge() {
        line(110, 117);
        line(126, 119);
        for (int y = 85; y < 140; y++) for (int x = 139; x <= 141; x++) g[y * W + x] = 0;
        assertEquals(-1, read().get(0).direction());
    }

    @Test
    public void crescendoAlsoRecognized() {
        line(113, 110);
        line(123, 126);
        assertEquals(1, read().get(0).direction());
    }

    @Test
    public void parallelRulesRejected() {
        line(110, 110);
        line(126, 126);
        assertTrue(read().isEmpty());
    }

    @Test
    public void singleRailRejected() {
        line(110, 113);
        assertTrue(read().isEmpty());
    }

    @Test
    public void filledInteriorRejected() {
        line(110, 113);
        line(126, 123);
        for (int x = 60; x < 220; x++) for (int y = 114; y < 123; y++) g[y * W + x] = 0;
        assertTrue(read().isEmpty());
    }
}
