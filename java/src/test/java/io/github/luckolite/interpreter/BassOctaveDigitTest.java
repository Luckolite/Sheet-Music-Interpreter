// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class BassOctaveDigitTest {
    static final int W = 120, H = 100;

    static void box(byte[] g, int l, int t, int r, int b, int color) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = (byte) color;
    }

    static byte[] page(String kind) {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        box(g, 15, 48, 100, 48, 0);
        if (kind.equals("plain")) return g;
        int top = kind.equals("distant") ? 5 : 30;
        box(g, 40, top, 50, top + 17, 0);
        box(g, 42, top + 2, 48, top + 6, 255);
        box(g, 42, top + 10, 48, top + 15, 255);
        if (kind.equals("zero")) box(g, 42, top + 2, 48, top + 15, 255);
        if (kind.equals("open")) box(g, 40, top + 11, 42, top + 13, 255);
        if (kind.equals("adjacent")) {
            box(g, 56, top, 66, top + 17, 0);
            box(g, 58, top + 2, 64, top + 6, 255);
            box(g, 58, top + 10, 64, top + 15, 255);
        }
        return g;
    }

    @org.junit.Test
    public void originalProceduralDigits() {
        int pass = 0;
        for (String mode : List.of("eight", "zero", "plain", "open", "adjacent", "distant")) {
            boolean found = BassOctaveDigit.above(page(mode), W, H, 30, 75, 48, 16);
            if (found != mode.equals("eight")) throw new AssertionError(mode + " " + found);
            pass++;
        }
        System.out.println("PASS " + pass + " original bass-octave digit controls");
    }
}
