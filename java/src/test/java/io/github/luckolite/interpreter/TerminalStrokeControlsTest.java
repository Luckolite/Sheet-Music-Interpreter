// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import io.github.luckolite.interpreter.TerminalCreditStroke;

public class TerminalStrokeControlsTest {
    static final int W = 240, H = 80;

    static void box(int[] p, int x, int y, int w, int h) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) p[j * W + i] = 0xff000000;
    }

    static int[] base() {
        int[] p = new int[W * H];
        Arrays.fill(p, 0xffffffff);
        for (int i = 0; i < 5; i++) box(p, 20 + i * 28, 20, 18, 28);
        return p;
    }

    static void check(String name, int[] p, boolean expected) {
        boolean actual = TerminalCreditStroke.hasTerminalShortStem(p, W, H);
        if (actual != expected) throw new AssertionError(name + ":" + actual);
    }

    @org.junit.Test
    public void originalControls() {
        int[] p = base();
        box(p, 160, 31, 8, 17);
        check("short terminal stem", p, true);
        p = base();
        check("broad final letter", p, false);
        p = base();
        box(p, 160, 31, 8, 17);
        box(p, 162, 23, 3, 3);
        check("dotted letter", p, false);
        p = base();
        box(p, 205, 31, 8, 17);
        check("distant rule", p, false);
        p = base();
        box(p, 160, 49, 5, 10);
        check("punctuation below baseline", p, false);
        p = base();
        box(p, 160, 31, 8, 17);
        box(p, 8, 8, 200, 2);
        check("overhead long rule", p, true);
        p = base();
        box(p, 160, 31, 8, 17);
        box(p, 182, 20, 18, 28);
        check("interior stroke", p, false);
        p = new int[W * H];
        Arrays.fill(p, 0xffffffff);
        check("blank", p, false);
        System.out.println("8 original terminal-stroke controls passed");
    }
}
