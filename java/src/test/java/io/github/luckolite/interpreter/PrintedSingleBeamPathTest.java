// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;

public class PrintedSingleBeamPathTest {
    static byte[] page(int thickness, int paper) {
        byte[] g = new byte[160 * 130];
        Arrays.fill(g, (byte) paper);
        for (int x = 40; x <= 100; x++) {
            int y = 80 + (x / 12) % 2;
            for (int d = 0; d < thickness; d++) g[(y + d) * 160 + x] = (byte) 185;
        }
        return g;
    }

    static boolean test(byte[] g) {
        return PrintedSingleBeamPath.connects(
                g, 160, 130, new int[] {40, 85, 1}, new int[] {100, 85, 1}, 14);
    }

    static void check(boolean b, String name) {
        if (!b) throw new AssertionError(name);
    }

    @org.junit.Test
    public void syntheticBeamControls() {
        check(test(page(5, 250)), "pale curved beam");
        check(!test(page(1, 250)), "thin staff line");
        check(!test(page(2, 250)), "thin slur");
        check(!test(page(16, 250)), "broad ink patch");
        byte[] g = page(5, 250);
        for (int y = 65; y <= 100; y++) g[y * 160 + 70] = (byte) 250;
        check(!test(g), "full white gap");
        check(!test(page(5, 200)), "shaded low contrast");
        check(
                !PrintedSingleBeamPath.connects(
                        page(5, 250), 160, 130, new int[] {40, 85, 1}, new int[] {100, 85, -1}, 14),
                "opposite stems");
        g = page(5, 250);
        byte[] before = g.clone();
        test(g);
        check(Arrays.equals(before, g), "source unchanged");
    }
}
