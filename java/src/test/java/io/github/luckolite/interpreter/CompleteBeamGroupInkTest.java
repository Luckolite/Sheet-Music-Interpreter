// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class CompleteBeamGroupInkTest {
    @org.junit.Test
    public void proceduralControls() {
        main(new String[0]);
    }

    static final int W = 420, H = 300;
    static final int[] STEMS = {100, 150, 200, 250};

    static void box(byte[] g, int l, int t, int r, int b, int color) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = (byte) color;
    }

    static boolean probe(int direction, String variant, int omit) {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        int head = direction > 0 ? 190 : 90, rail = direction > 0 ? 110 : 170;
        for (int x : STEMS) {
            box(g, x - 1, Math.min(head, rail), x + 1, Math.max(head, rail), 0);
            box(g, x - direction * 8 - 7, head - 4, x - direction * 8 + 7, head + 4, 0);
        }
        int top = rail - 2, bottom = rail + 2;
        if (variant.equals("thin")) top = bottom = rail;
        box(g, STEMS[0] - 1, top, STEMS[3] + 1, bottom, 0);
        if (variant.equals("broken")) box(g, 170, top, 180, bottom, 255);
        if (variant.equals("extra"))
            box(g, 174, Math.min(head, rail), 176, Math.max(head, rail), 0);
        if (variant.equals("extended")) box(g, STEMS[0] - 25, top, STEMS[0], bottom, 0);
        float[] xs = new float[omit < 0 ? 4 : 3], ys = new float[xs.length];
        int at = 0;
        for (int i = 0; i < 4; i++)
            if (i != omit) {
                xs[at] = STEMS[i] - direction * 8;
                ys[at++] = head;
            }
        return CompleteBeamGroupInk.proves(g, W, H, xs, ys, 16, direction);
    }

    public static void main(String[] args) {
        int passed = 0;
        for (int d : new int[] {-1, 1}) {
            for (String v : new String[] {"complete", "broken", "extra", "extended", "thin"}) {
                boolean found = probe(d, v, -1);
                if (found != v.equals("complete"))
                    throw new AssertionError(d + " " + v + " " + found);
                passed++;
            }
            for (int i = 0; i < 4; i++) {
                if (probe(d, "complete", i)) throw new AssertionError("omitted " + d + " " + i);
                passed++;
            }
        }
        System.out.println("PASS " + passed + " original synthetic beam controls");
    }
}
