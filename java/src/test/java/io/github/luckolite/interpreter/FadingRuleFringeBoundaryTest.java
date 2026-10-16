// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class FadingRuleFringeBoundaryTest {
    static void rect(byte[] g, int w, int l, int t, int r, int b, int ink) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * w + x] = (byte) ink;
    }

    @org.junit.Test
    public void originalGeometryControls() {
        main(new String[0]);
    }

    public static void main(String[] args) {
        int checks = 0;
        for (int kind = 0; kind < 6; kind++) {
            int w = 600, h = 260, a = 80, gap = 18, b = a + 4 * gap, x = 300;
            byte[] g = new byte[w * h];
            Arrays.fill(g, (byte) 255);
            for (int i = 0; i < 5; i++) {
                int y = a + i * gap;
                rect(g, w, 30, y - 1, 570, y + 1, 130);
                rect(g, w, 30, y, 570, y, 20);
            }
            rect(g, w, x - 1, a, x + 1, b, 20);
            // A partial fading raster row cannot itself provide bilateral rule support.
            for (int edge : new int[] {a, b}) {
                int sign = edge == a ? -1 : 1;
                rect(g, w, x - 12, edge + sign * 2, x + 12, edge + sign * 2, 175);
            }
            if (kind == 1) rect(g, w, x - 1, a - 18, x + 1, a - 2, 20); // extended stem
            if (kind == 2) rect(g, w, x - 12, a - 4, x + 12, a + 4, 20); // head touching endpoint
            if (kind == 3) {
                rect(g, w, 250, a - 1, 285, a - 1, 255);
                rect(g, w, 315, a - 1, 350, a - 1, 255);
            } // no supporting rule on flanks
            if (kind == 4) rect(g, w, x - 12, b + 2, x + 12, b + 2, 100); // darker off-rule mark
            if (kind == 5) rect(g, w, x - 12, a - 4, x + 12, a - 4, 175); // detached mark
            var raw =
                    List.of(
                            new MeasureRegion(.05f, .495f, .10f, .90f),
                            new MeasureRegion(.505f, .95f, .10f, .90f));
            var got =
                    PrintedMeasureRhythmGuard.preservePrintedBoundaries(
                            raw, List.of(new MeasureRegion(.05f, .95f, .10f, .90f)), g, w, h);
            int expected = kind == 0 ? 2 : 1;
            if (got.size() != expected)
                throw new AssertionError(
                        "kind=" + kind + " expected=" + expected + " got=" + got.size());
            checks++;
        }
        System.out.println("PASS " + checks + " original fringe and rejection cases");
    }
}
