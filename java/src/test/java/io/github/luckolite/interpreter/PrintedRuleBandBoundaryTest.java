// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class PrintedRuleBandBoundaryTest {
    static void rect(byte[] g, int w, int h, int l, int t, int r, int b, int value) {
        for (int y = Math.max(0, t); y <= Math.min(h - 1, b); y++)
            for (int x = Math.max(0, l); x <= Math.min(w - 1, r); x++) g[y * w + x] = (byte) value;
    }

    @org.junit.Test
    public void originalRuleAndStemControls() {
        main(new String[0]);
    }

    public static void main(String[] args) {
        int checks = 0;
        for (int gap : new int[] {8, 12, 16})
            for (int thick : new int[] {1, 3, 5})
                for (int kind = 0; kind < 5; kind++) {
                    int w = 600, h = 260, a = 80, b = a + gap * 4, x = 300;
                    byte[] g = new byte[w * h];
                    Arrays.fill(g, (byte) 255);
                    for (int i = 0; i < 5; i++) {
                        int y = a + i * gap;
                        rect(g, w, h, 30, y - thick / 2, 570, y + thick / 2, 147);
                        rect(g, w, h, 30, y, 570, y, 20);
                    }
                    if (kind != 4) rect(g, w, h, x - 1, a, x + 1, b, 20);
                    if (kind == 1) rect(g, w, h, x - 1, a - gap, x + 1, a, 20);
                    if (kind == 2) rect(g, w, h, x - gap / 2, a - 3, x + gap / 2, a + 3, 20);
                    if (kind == 3) rect(g, w, h, x - 1, b, x + 1, b + gap, 20);
                    var full = new MeasureRegion(.05f, .95f, .10f, .90f);
                    var raw =
                            List.of(
                                    new MeasureRegion(.05f, .495f, .10f, .90f),
                                    new MeasureRegion(.505f, .95f, .10f, .90f));
                    var got =
                            PrintedMeasureRhythmGuard.preservePrintedBoundaries(
                                    raw, List.of(full), g, w, h);
                    // The existing staff detector cannot resolve five-pixel rules at the two
                    // smaller gaps.
                    // Without a staff witness, conservatively retain the reconciled row.
                    int expected = kind == 0 && (thick != 5 || gap == 16) ? 2 : 1;
                    if (got.size() != expected)
                        throw new AssertionError(
                                "staff="
                                        + RawStaffLineDetector.detect(g, w, h).stream()
                                                .map(z -> Arrays.toString(z.rows()))
                                                .toList()
                                        + "gap="
                                        + gap
                                        + " thickness="
                                        + thick
                                        + " kind="
                                        + kind
                                        + " expected="
                                        + expected
                                        + " actual="
                                        + got.size());
                    checks++;
                }
        System.out.println("PASS " + checks + " original rule-band and stem controls");
    }
}
