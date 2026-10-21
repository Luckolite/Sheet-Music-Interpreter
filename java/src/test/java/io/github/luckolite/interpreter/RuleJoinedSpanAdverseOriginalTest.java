// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Unique integer ink, not score data. Direct masks do not certify independent staff rules. */
public class RuleJoinedSpanAdverseOriginalTest {
    private static final int WIDTH = 96, LEFT = 20, TOP = 20, RIGHT = 40, BOTTOM = 44;

    // One inclusive interval per column; each literal site is emitted exactly once.
    private static int[] original(int role) {
        int[] ink = new int[525];
        int n = 0;
        for (int t = 0; t <= 20; t++) {
            int low = 0, high;
            if (role == 0) high = 24; // Fully filled vertical rectangle.
            else if (role == 1)
                high = t >= 9 && t <= 11 ? 24 : 2; // Stem with a connected horizontal foot.
            else if (role == 2) high = 2 * Math.min(t, 20 - t) + 4; // Solid triangular wedge.
            else {
                low = 2 * Math.min(t, 20 - t);
                high = low + 4;
                if (t == 7 || t == 13) {
                    low = 16;
                    high = 20;
                }
                if (t == 8 || t == 9 || t == 11 || t == 12) {
                    low = 20;
                    high = 24;
                }
                if (t == 10) {
                    low = 0;
                    high = 24;
                } // Complete vertical bridge between upper lobes.
            }
            for (int u = low; u <= high; u++) ink[n++] = (BOTTOM - u) * WIDTH + LEFT + t;
        }
        return Arrays.copyOf(ink, n);
    }

    private static boolean joined(int[] ink) {
        // All-false complete mask deliberately isolates the dedicated structural span rule.
        return RoundedAngularArticulation.matchesRuleJoinedOpen(
                ink, WIDTH, LEFT, TOP, RIGHT, BOTTOM, new boolean[33], 16);
    }

    @Test
    public void fullyFilledVerticalRectangleRetainsDensityRejection() {
        int[] ink = original(0);
        assertEquals(525, ink.length);
        assertFalse(joined(ink));
    }

    @Test
    public void completeStemAndFootCannotSupplyAngularRise() {
        int[] ink = original(1);
        assertEquals(129, ink.length);
        // Peak column mean12 minus endpoint mean1 is11, below required12.72.
        assertFalse(joined(ink));
    }

    @Test
    public void solidWedgeCannotSupplyAngularRise() {
        int[] ink = original(2);
        assertEquals(305, ink.length);
        // Peak mean12 minus two-column endpoint average2.5 is9.5, below12.72.
        assertFalse(joined(ink));
    }

    @Test
    public void completeVerticalBridgeDoesNotMakeTwoLobesStraightArms() {
        int[] ink = original(3);
        assertEquals(125, ink.length);
        // Right-fit samples at9,10,11 are22,12,22. Their second difference20
        // forces SSE>=400/6, hence RMS>=sqrt(400/(6*13))>1.92 for that arm.
        assertFalse(joined(ink));
    }
}
