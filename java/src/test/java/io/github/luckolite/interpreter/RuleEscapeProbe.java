// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class RuleEscapeProbe {
    static final int W = 260, H = 250;

    static void segment(byte[] a, int x, int y) {
        ExtentChallenge.pixel(a, x, y, 25);
        ExtentChallenge.pixel(a, x + 1, y, 25);
    }

    static byte[] scene() {
        var a = ExtentChallenge.paper();
        for (int y = 25; y <= 99; y++) {
            double p = Math.floorMod(y - 27, 16) / 16.;
            int x = 60 + (int) Math.round(p < .5 ? p * 20 : (1 - p) * 20);
            segment(a, x, y);
        }
        for (int y = 100; y <= 102; y++)
            for (int x = 10; x <= 220; x++) ExtentChallenge.pixel(a, x, y, 100);
        segment(a, 70, 100);
        segment(a, 71, 101);
        segment(a, 73, 102);
        for (int y = 103; y <= 170; y++) {
            double p = (y - 103) % 16 / 16.;
            int x = 75 + (int) Math.round(p < .5 ? p * 20 : (1 - p) * 20);
            segment(a, x, y);
        }
        return a;
    }

    static ScorePageInterpretation page() {
        var n = new ArrayList<ScoreNoteEvent>();
        for (int step : new int[] {1, 4, 8})
            n.add(
                    new ScoreNoteEvent(
                            0, 80f / W, step, 0, 2, (100 - step * 8f) / H, false, 0, 0, 0, 2));
        return new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), n, 1);
    }
}
