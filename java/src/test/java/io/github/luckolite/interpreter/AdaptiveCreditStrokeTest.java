// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdaptiveCreditStrokeTest {
    static final int W = 400, H = 100;

    static int gray(int g) {
        return 0xff000000 | (g << 16) | (g << 8) | g;
    }

    static int[] blank() {
        int[] p = new int[W * H];
        Arrays.fill(p, gray(215));
        return p;
    }

    static void box(int[] p, int x, int y, int w, int h, int g) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) p[j * W + i] = gray(g);
    }

    static int[] letters() {
        int[] p = blank();
        for (int x = 40; x <= 240; x += 50) box(p, x, 25, 20, 36, 20);
        return p;
    }

    static boolean stem(int[] p) {
        var q = TerminalCreditStroke.prepareAdaptive(W, H, p);
        return TerminalCreditStroke.hasTerminalShortStem(q.pixels(), q.width(), q.height());
    }

    @Test
    public void faintStemSurvives() {
        var p = letters();
        box(p, 280, 33, 7, 28, 165);
        assertTrue(stem(p));
    }

    @Test
    public void blankRemainsBlank() {
        var q = TerminalCreditStroke.prepareAdaptive(W, H, blank());
        assertTrue(Arrays.stream(q.pixels()).allMatch(x -> x == -1));
    }

    @Test
    public void noStemIsNotInvented() {
        assertFalse(stem(letters()));
    }

    @Test
    public void dustDoesNotBecomeStem() {
        var p = letters();
        box(p, 281, 40, 2, 2, 50);
        assertFalse(stem(p));
    }

    @Test
    public void dottedLetterRejected() {
        var p = letters();
        box(p, 280, 33, 7, 28, 165);
        box(p, 281, 20, 5, 5, 80);
        assertFalse(stem(p));
    }

    @Test
    public void topRuleRemoved() {
        var p = letters();
        box(p, 10, 5, 380, 3, 10);
        box(p, 280, 33, 7, 28, 165);
        assertTrue(stem(p));
    }

    @Test
    public void disconnectedInkInsideRuleBoundsRetained() {
        var p = blank();
        box(p, 10, 5, 380, 2, 10);
        box(p, 10, 5, 2, 15, 10);
        box(p, 388, 5, 2, 15, 10);
        box(p, 200, 14, 3, 3, 10);
        var q = TerminalCreditStroke.prepareAdaptive(W, H, p);
        int pad = H / 8;
        assertEquals(0xff000000, q.pixels()[(15 + pad) * q.width() + 201 + pad]);
        assertEquals(0xffffffff, q.pixels()[(5 + pad) * q.width() + 100 + pad]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tallInputRejectedBeforeAllocation() {
        TerminalCreditStroke.prepareAdaptive(20, 20000, new int[400000]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidDimensionsRejected() {
        TerminalCreditStroke.prepareAdaptive(100, 20, new int[1999]);
    }
}
