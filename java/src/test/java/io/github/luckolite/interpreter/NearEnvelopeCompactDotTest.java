// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Round printed dots tangent to a staff fringe, including a one-gray visible boundary. */
public class NearEnvelopeCompactDotTest {
    static void check(int darker) {
        for (int gap : new int[] {16, 20})
            for (int thickness : new int[] {2, 3})
                for (int radius : new int[] {1, 2, 3}) {
                    var p = new CompleteRuleRowWholeRestTest(gap, thickness);
                    p.halo();
                    int cx = p.right + Math.round(gap * .65f), cy = p.first + 1 - radius;
                    for (int y = cy - radius; y <= cy + radius; y++)
                        for (int x = cx - radius; x <= cx + radius; x++)
                            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius)
                                p.gray[y * p.W + x] =
                                        (byte) Math.min(p.gray[y * p.W + x] & 255, 220 - darker);
                    assertFalse(
                            "gap="
                                    + gap
                                    + " thickness="
                                    + thickness
                                    + " radius="
                                    + radius
                                    + " darker="
                                    + darker,
                            p.full());
                }
    }

    @Test
    public void oneGrayDarkerRoundDotsRemainUnaccounted() {
        check(1);
    }

    @Test
    public void twoGrayDarkerRoundDotsRemainUnaccounted() {
        check(2);
    }

    @Test
    public void threeGrayDarkerRoundDotsRemainUnaccounted() {
        check(3);
    }

    static CompleteRuleRowWholeRestTest gradient(int gap) {
        var p = new CompleteRuleRowWholeRestTest(gap, 3);
        for (int x = 500; x <= 779; x++)
            if (x < p.left || x > p.right) {
                int inner = 160 + (x - 500) * 30 / 279;
                int outer = (int) Math.round(255 - .5 * (255 - inner));
                p.gray[p.first * p.W + x] = (byte) inner;
                p.gray[(p.first + 1) * p.W + x] = (byte) outer;
            }
        return p;
    }

    @Test
    public void brokenCoreCannotDismissUnownedFringe() {
        var p = new CompleteRuleRowWholeRestTest();
        p.halo();
        p.box(p.right + 15, 100 + p.gap, p.right + 15, p.first - 1, 255);
        assertFalse(p.full());
    }

    @Test
    public void observedOpticalPhaseGradientRetainsUndottedRest() {
        assertTrue(gradient(16).full());
    }

    @Test
    public void scaledOpticalPhaseGradientRetainsUndottedRest() {
        assertTrue(gradient(20).full());
    }
}
