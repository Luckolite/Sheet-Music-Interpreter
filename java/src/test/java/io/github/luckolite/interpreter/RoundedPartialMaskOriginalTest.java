// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoundedPartialMaskOriginalTest {
    @Test
    public void faintHeadWhoseModelIslandLiesOnRuleStillOwnsItsShaft() throws Exception {
        var f = new RoundedRuleBandOriginalTest();
        for (int y = 60; y <= 76; y++)
            for (int x = 142; x <= 164; x++) {
                double dx = x - 153, dy = y - 68;
                double q = dx * dx / 81 + (dy + .4 * dx) * (dy + .4 * dx) / 25;
                if (q <= 1) f.g[y * f.W + x] = (byte) 254;
            }
        for (int[] b : new int[][] {{36, 39}, {50, 52}, {64, 66}, {78, 79}, {91, 94}})
            for (int y = b[0]; y <= b[1]; y++) for (int x = 20; x < 220; x++) f.g[y * f.W + x] = 30;
        for (int y = 36; y <= 95; y++)
            for (int x = 159; x <= 161; x++) {
                f.g[y * f.W + x] = 30;
                f.l[y * f.W + x] = 1;
            }
        for (int y = 64; y <= 66; y++) for (int x = 159; x <= 161; x++) f.l[y * f.W + x] = 2;
        assertTrue(f.touches());
    }
}
