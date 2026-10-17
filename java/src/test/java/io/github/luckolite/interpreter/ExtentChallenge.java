// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Independent original procedural scenes; no source-score pixels. */
public final class ExtentChallenge {
    static final int W = 260, H = 250;

    static byte[] paper() {
        var a = new byte[W * H];
        Arrays.fill(a, (byte) 255);
        return a;
    }

    static void pixel(byte[] a, int x, int y, int v) {
        if (x >= 0 && x < W && y >= 0 && y < H)
            a[y * W + x] = (byte) Math.min(a[y * W + x] & 255, v);
    }

    static void wave(byte[] a, int first, int last, int shift) {
        for (int y = first; y <= last; y++) {
            double p = Math.floorMod(y - 50, 12) / 12.;
            int x = 60 + shift + (int) Math.round(p < .5 ? p * 12 : (1 - p) * 12);
            pixel(a, x, y, 25);
            pixel(a, x + 1, y, 25);
        }
    }

    static ScorePageInterpretation page() {
        var n = new ArrayList<ScoreNoteEvent>();
        for (int step : new int[] {0, 3, 7})
            n.add(
                    new ScoreNoteEvent(
                            0, .35f, step, 0, 2, (100 - step * 6f) / H, false, 0, 0, 0, 2));
        n.add(new ScoreNoteEvent(0, .35f, 0, 1, 2, 190f / H, false, 0, 0, 0, 2));
        return new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), n, 1);
    }

    static byte[] finite() {
        var a = paper();
        wave(a, 50, 109, 0);
        return a;
    }
}
