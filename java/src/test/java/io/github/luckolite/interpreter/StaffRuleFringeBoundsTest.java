// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original deterministic image-edge profiles; no retained score pixels. */
public class StaffRuleFringeBoundsTest {
    Object sample(byte[] g, int w, int h, int x, float seed, float gap) throws Exception {
        var m =
                CompleteStaffRuleBeamBand.class.getDeclaredMethod(
                        "sample",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        int.class);
        m.setAccessible(true);
        return m.invoke(null, g, w, h, x, seed, gap, 165);
    }

    byte[] edge(boolean bottom) {
        int w = 30, h = 80;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 200);
        for (int y = 4; y <= 17; y++)
            for (int x = 0; x < w; x++) g[(bottom ? h - 1 - y : y) * w + x] = 100;
        return g;
    }

    @Test
    public void topFringeOutsideImageAbstains() throws Exception {
        assertNull(sample(edge(false), 30, 80, 15, 20, 40));
    }

    @Test
    public void bottomFringeOutsideImageAbstains() throws Exception {
        assertNull(sample(edge(true), 30, 80, 15, 59, 40));
    }

    @Test
    public void exactFringeImageEdgeIsInBounds() throws Exception {
        byte[] g = new byte[30 * 80];
        Arrays.fill(g, (byte) 200);
        for (int y = 5; y <= 18; y++) for (int x = 0; x < 30; x++) g[y * 30 + x] = 100;
        assertNotNull(sample(g, 30, 80, 15, 21.5f, 40));
    }

    @Test
    public void finiteRailScanningPastHorizontalImageEdgesAbstains() {
        var p = new CompleteStaffRuleBeamBandTest();
        p.setup();
        for (int x : new int[] {-1, 0, 1, 2, 1197, 1198, 1199, 1200})
            assertFalse(
                    CompleteStaffRuleBeamBand.matches(p.g, 1200, 240, x, 80, 16, 165, 110, 114));
    }

    @Test
    public void deterministicTopBottomFringeFuzzNeverReadsOutsideImage() throws Exception {
        Random r = new Random(169);
        for (int n = 0; n < 1200; n++) {
            int w = 12, h = 30 + r.nextInt(100), gap = 8 + r.nextInt(57);
            byte[] g = new byte[w * h];
            Arrays.fill(g, (byte) 200);
            int first = r.nextInt(h),
                    last = Math.min(h - 1, first + 1 + r.nextInt(Math.max(2, gap / 2)));
            for (int y = first; y <= last; y++) for (int x = 0; x < w; x++) g[y * w + x] = 100;
            float seed =
                    n % 2 == 0
                            ? r.nextInt(Math.max(1, h / 2))
                            : h - 1 - r.nextInt(Math.max(1, h / 2));
            sample(g, w, h, 1 + r.nextInt(w - 2), seed, gap);
        }
    }
}
