// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic pixels; no commercial score material. */
public class PrintedDashChainsTest {
    final int w = 400, h = 200;

    byte[] page() {
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        return g;
    }

    void box(byte[] g, int x, int y, int width, int height) {
        for (int yy = y; yy < y + height; yy++)
            for (int xx = x; xx < x + width; xx++) g[yy * w + xx] = 30;
    }

    @Test
    public void repeatedThinDashesKeepExtent() {
        var g = page();
        for (int x = 30; x < 330; x += 15) box(g, x, 80, 6, 2);
        var r = PrintedDashChains.detect(g, w, h, 16);
        assertEquals(1, r.size());
        assertEquals(20, r.get(0).dashes());
        assertEquals(30f / w, r.get(0).left(), .00001);
        assertEquals(321f / w, r.get(0).right(), .00001);
    }

    @Test
    public void continuousRuleIsNotDashes() {
        var g = page();
        box(g, 30, 80, 300, 2);
        assertTrue(PrintedDashChains.detect(g, w, h, 16).isEmpty());
    }

    @Test
    public void roundDotsAreNotDashes() {
        var g = page();
        for (int x = 30; x < 330; x += 15) box(g, x, 80, 3, 3);
        assertTrue(PrintedDashChains.detect(g, w, h, 16).isEmpty());
    }

    @Test
    public void stemAttachedMarksAreNotDetachedDashes() {
        var g = page();
        for (int x = 30; x < 330; x += 15) {
            box(g, x, 80, 6, 2);
            box(g, x + 5, 60, 1, 20);
        }
        assertTrue(PrintedDashChains.detect(g, w, h, 16).isEmpty());
    }

    @Test
    public void separateRowsRemainSeparate() {
        var g = page();
        for (int y : new int[] {60, 100}) for (int x = 30; x < 330; x += 15) box(g, x, y, 6, 2);
        assertEquals(2, PrintedDashChains.detect(g, w, h, 16).size());
    }

    @Test
    public void shortPunctuationRunIsNotExtension() {
        var g = page();
        for (int x = 30; x < 90; x += 15) box(g, x, 80, 6, 2);
        assertTrue(PrintedDashChains.detect(g, w, h, 16).isEmpty());
    }

    @Test
    public void inputIsUnchanged() {
        var g = page();
        for (int x = 30; x < 330; x += 15) box(g, x, 80, 6, 2);
        var before = g.clone();
        PrintedDashChains.detect(g, w, h, 16);
        assertArrayEquals(before, g);
    }
}
