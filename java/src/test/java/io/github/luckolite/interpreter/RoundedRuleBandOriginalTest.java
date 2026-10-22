// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pixel-centre samples of thin complete five-rule frames with fractional gap. */
public class RoundedRuleBandOriginalTest {
    static final int W = 240, H = 180;
    byte[] g = new byte[W * H], l = new byte[W * H];
    int[] rows = {40, 54, 67, 81, 93};
    float gap = 13.25f;

    public RoundedRuleBandOriginalTest() {
        Arrays.fill(g, (byte) 255);
        for (int[] band : new int[][] {{36, 39}, {50, 52}, {64, 66}, {78, 79}, {91, 94}})
            for (int y = band[0]; y <= band[1]; y++)
                for (int x = 20; x < 220; x++) {
                    g[y * W + x] = 30;
                    l[y * W + x] = 4;
                }
    }

    float[] refined() throws Exception {
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "connectionStaffRows",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int[].class,
                        float.class,
                        float.class);
        m.setAccessible(true);
        return (float[]) m.invoke(null, g, W, H, 160, rows, gap, 0f);
    }

    boolean touches() throws Exception {
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "headTouchesColumn",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int[].class,
                        float.class,
                        float.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, l, g, W, H, 160, 20, 113, rows, gap, 0f);
    }

    void head(int cx) {
        for (int y = 57; y <= 73; y++)
            for (int x = cx - 11; x <= cx + 11; x++) {
                double dx = x - cx, dy = y - 65;
                double q = dx * dx / 81 + (dy + .4 * dx) * (dy + .4 * dx) / 25;
                if (q <= 1) {
                    l[y * W + x] = 2;
                    g[y * W + x] = 30;
                }
            }
        for (int y = 68; y <= 94; y++)
            for (int x = cx - 9; x <= cx - 8; x++) {
                l[y * W + x] = 1;
                g[y * W + x] = 30;
            }
    }

    @Test
    public void fourPixelSampleOfFractionalThinRuleHasCompleteFrame() throws Exception {
        assertNotNull(refined());
    }

    @Test
    public void staffOnlyBridgeCannotMakeSeparateHeadOwnBar() throws Exception {
        head(145);
        for (int y = 36; y <= 95; y++)
            for (int x = 159; x <= 161; x++) {
                g[y * W + x] = 30;
                l[y * W + x] = 1;
            }
        assertFalse(touches());
    }

    @Test
    public void genuineHeadOnAxisStillVetoes() throws Exception {
        head(159);
        for (int y = 36; y <= 95; y++)
            for (int x = 159; x <= 161; x++) {
                g[y * W + x] = 30;
                l[y * W + x] = 1;
            }
        assertTrue(touches());
    }

    @Test
    public void missingIndependentRuleCannotRefineFrame() throws Exception {
        for (int y = 91; y <= 94; y++) for (int x = 20; x < 220; x++) g[y * W + x] = (byte) 255;
        assertNull(refined());
    }

    @Test
    public void fivePixelCoreDoesNotFitThinRuleBudget() throws Exception {
        for (int x = 20; x < 220; x++) g[35 * W + x] = 30;
        assertNull(refined());
    }
}
