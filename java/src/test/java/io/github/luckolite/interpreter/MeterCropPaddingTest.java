// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original staff and blank-margin geometry, independent of source-score pixels. */
public class MeterCropPaddingTest {
    static final int W = 640, H = 300;

    static byte[] rails(int count, float slope, int paper) {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) paper);
        for (int n = 0; n < count; n++)
            for (int x = 60; x < 580; x++)
                g[(100 + n * 14 + Math.round((x - 320) * slope)) * W + x] = 30;
        return g;
    }

    static int passed;

    static void check(String name, byte[] g, int first, int top, int bottom) {
        var before = g.clone();
        var c = new MeterChangeDetector.Crop(300, 90, 340, 164, first, 14);
        var r = MeterCropStaff.refine(g, W, H, c);
        if (r.top() != top || r.bottom() != bottom || !Arrays.equals(before, g))
            throw new AssertionError(name + " " + r);
        System.out.println("PASS " + name);
        passed++;
    }

    @org.junit.Test
    public void originalSyntheticControls() throws Exception {
        passed = 0;
        main(new String[0]);
    }

    public static void main(String[] args) {
        check("two witnesses trim empty outer padding", rails(5, 0, 255), 96, 96, 161);
        check("aligned crop has no new trim", rails(5, 0, 255), 100, 90, 164);
        check("four rails preserve crop", rails(4, 0, 255), 96, 90, 164);
        check("blank page preserves crop", rails(0, 0, 255), 96, 90, 164);
        check("disagreeing phases preserve crop", rails(5, .08f, 255), 96, 90, 164);
        check("gray margin is not assumed blank", rails(5, 0, 235), 96, 90, 164);
        var g = rails(5, 0, 255);
        g[94 * W + 320] = 0;
        check("top ink preserved", g, 96, 90, 161);
        g = rails(5, 0, 255);
        g[162 * W + 320] = 0;
        check("bottom ink preserved", g, 96, 96, 164);
        g = rails(5, 0, 255);
        g[94 * W + 320] = (byte) 244;
        check("faint top ink preserved", g, 96, 90, 161);
        g = rails(5, 0, 255);
        g[162 * W + 320] = (byte) 244;
        check("faint bottom ink preserved", g, 96, 96, 164);
        g = rails(5, 0, 255);
        g[95 * W + 339] = 0;
        g[161 * W + 300] = 0;
        check("edge ink preserved", g, 96, 90, 164);
        check("distant staff preserves crop", rails(5, 0, 255), 107, 90, 164);
        System.out.println("Passed " + passed + " original padding controls");
    }
}
