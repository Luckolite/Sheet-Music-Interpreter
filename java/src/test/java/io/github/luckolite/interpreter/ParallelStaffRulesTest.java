// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParallelStaffRulesTest {
    static final int W = 240, H = 140;
    byte[] g = new byte[W * H];

    public ParallelStaffRulesTest() {
        Arrays.fill(g, (byte) 255);
        for (int y : new int[] {40, 54, 68, 82, 96}) rect(10, 230, y, y, 0);
    }

    void rect(int a, int b, int t, int z, int ink) {
        for (int y = t; y <= z; y++) for (int x = a; x <= b; x++) g[y * W + x] = (byte) ink;
    }

    boolean proved() {
        return ParallelStaffRules.proved(g, W, H, 100, 38, 98, 14);
    }

    void pair() {
        rect(90, 92, 40, 96, 0);
        rect(100, 102, 40, 96, 0);
    }

    @Test
    public void independentRulesProvePairAndPreservePixels() {
        pair();
        byte[] before = g.clone();
        assertTrue(proved());
        assertArrayEquals(before, g);
    }

    @Test
    public void singleRuleDoesNotProvePair() {
        rect(100, 102, 40, 96, 0);
        assertFalse(proved());
    }

    @Test
    public void missingLowerSpaceRejects() {
        pair();
        rect(90, 92, 86, 94, 255);
        assertFalse(proved());
    }

    @Test
    public void sharedSolidStrokeRejects() {
        rect(90, 102, 40, 96, 0);
        assertFalse(proved());
    }

    @Test
    public void staggeredStrokesReject() {
        rect(90, 92, 40, 69, 0);
        rect(100, 102, 65, 96, 0);
        assertFalse(proved());
    }

    @Test
    public void nearParallelCounterRequiresFourSpaces() throws Exception {
        pair();
        rect(91, 102, 81, 89, 0);
        for (int row = 0; row < 5; row++)
            rect(98 - Math.min(6, row + 2) + 1, 98, 83 + row, 83 + row, 255);
        assertTrue(OmrMeasurePostProcessor.threeOverFourCounters(g, W, H, 100, 38, 98, 14));
        var m =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "rawBarlineColumn",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int[].class,
                        float.class,
                        float.class,
                        float.class);
        m.setAccessible(true);
        assertNotEquals(
                Integer.MIN_VALUE,
                (int) m.invoke(null, g, W, H, 100, new int[] {40, 54, 68, 82, 96}, 14f, 0f, 0f));
    }
}
