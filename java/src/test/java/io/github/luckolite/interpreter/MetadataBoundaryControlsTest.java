// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import io.github.luckolite.interpreter.MetadataStaffBoundary;

public class MetadataBoundaryControlsTest {
    static int checks = 0;

    static void ck(boolean b, String n) {
        checks++;
        if (!b) throw new AssertionError(n);
    }

    static boolean[] lines(int count) {
        boolean[] p = new boolean[1000 * 800];
        for (int n = 0; n < count; n++)
            for (int x = 100; x < 900; x++) p[(200 + n * 12) * 1000 + x] = true;
        return p;
    }

    static float run(boolean[] p, float old, float first, float gap, float slope, int support) {
        return MetadataStaffBoundary.advance(1000, 800, p, old, first, gap, slope, support);
    }

    @org.junit.Test
    public void originalControls() {
        var p = lines(5);
        ck(run(p, 500, 200, 12, 0, 2) == 200, "continuous staff advances");
        ck(run(p, -1, 200, 12, 0, 2) == -1, "no discovery from absent baseline");
        ck(run(p, 220, 200, 12, 0, 2) == 220, "small shift unchanged");
        ck(run(p, 500, 200, 12, 0, 1) == 500, "one tile not enough");
        ck(run(lines(3), 500, 200, 12, 0, 2) == 500, "three lines insufficient");
        ck(run(new boolean[800000], 500, 200, 12, 0, 2) == 500, "blank");
        ck(run(p, 500, Float.NaN, 12, 0, 2) == 500, "nan geometry");
        ck(run(p, 500, 200, 0, 0, 2) == 500, "zero spacing");
        ck(run(new boolean[1], 500, 200, 12, 0, 2) == 500, "invalid raster");
        ck(run(p, 150, 200, 12, 0, 2) == 150, "later candidate preserves first system");
        System.out.println(checks + " pure boundary controls passed");
    }
}
