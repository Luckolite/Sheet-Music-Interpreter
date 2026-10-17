// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural zigzags and optical staff overlaps, without score pixels or outlines.
 * The unchanged existing quarterRest entry point supplies the baseline behavior. */
public class RuleCrossingQuarterRestTest {
    static final int W = 260, H = 220;
    // Reuse the original public PairedQuarterRestTopologyTest procedural contour policy.
    static final double[] T = {0, .04, .18, .24, .42, .48, .57, .62, .72, .78, .86, .96, 1};
    static final double[] L = {.4, .4, .46, .46, .2, .2, .45, .45, 0, 0, .08, .3, .3};

    static final class Page {
        final byte[] gray = new byte[W * H];
        final boolean[] lines = new boolean[H];
        final int gap, first, last, right, thickness, staffTone;
        SixteenthRestDetector.Staff staff;

        Page(int g, double phase, int thick, double shoulder, int ruleInk, int bodyInk) {
            gap = g;
            thickness = thick;
            staffTone = ruleInk;
            first = 60 + (int) Math.round(g * phase);
            int rows = Math.round(g * 2.9f);
            last = first + rows - 1;
            right = 130 + (int) Math.ceil(g * .96);
            staff = new SixteenthRestDetector.Staff(60, 60 + 4 * g, g, 0, 1);
            Arrays.fill(gray, (byte) 255);
            for (int r = 0; r < 5; r++) rule(60 + r * g, ruleInk);
            double[] rr = {
                .48, .48, .96, .96, .68, .68, shoulder, shoulder, .95, .95, .32, .42, .42
            };
            for (int y = 0; y < rows; y++) {
                double t = y / (double) (rows - 1);
                int k = 0;
                while (k + 1 < T.length - 1 && T[k + 1] < t) k++;
                double f = (t - T[k]) / (T[k + 1] - T[k]);
                int lo = 130 + (int) Math.round(g * (L[k] + (L[k + 1] - L[k]) * f));
                int hi = 130 + (int) Math.round(g * (rr[k] + (rr[k + 1] - rr[k]) * f));
                for (int x = lo; x <= hi; x++)
                    gray[(first + y) * W + x] =
                            (byte) Math.min(gray[(first + y) * W + x] & 255, bodyInk);
            }
        }

        void rule(int cy, int value) {
            for (int y = cy - thickness / 2; y < cy - thickness / 2 + thickness; y++) {
                lines[y] = true;
                for (int x = 10; x < W - 10; x++)
                    gray[y * W + x] = (byte) Math.min(gray[y * W + x] & 255, value);
            }
        }

        void blankBody() {
            for (int y = 0; y < H; y++)
                for (int x = 130; x <= right; x++)
                    gray[y * W + x] = (byte) (lines[y] ? staffTone : 255);
        }

        boolean quarter() throws Exception {
            Method m =
                    SixteenthRestDetector.class.getDeclaredMethod(
                            "quarterRest",
                            byte[].class,
                            int.class,
                            SixteenthRestDetector.Staff.class,
                            int.class,
                            boolean[].class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            boolean.class);
            m.setAccessible(true);
            return (Boolean)
                    m.invoke(null, gray, W, staff, 0, lines, 130, right, first, last, false);
        }
    }

    static Page normal() {
        return new Page(12, .4, 2, .6, 70, 5);
    }

    @Test
    public void completeBodyCrossingTwoPixelRules() throws Exception {
        assertTrue(normal().quarter());
    }

    @Test
    public void completeBodyCrossingThreePixelRules() throws Exception {
        assertTrue(new Page(14, .5, 3, .8, 70, 5).quarter());
    }

    @Test
    public void independentlyScaledWholeBody() throws Exception {
        assertTrue(new Page(16, .6, 2, .7, 70, 5).quarter());
    }

    @Test
    public void saturatedBlackOverlapCannotInventBody() throws Exception {
        assertFalse(new Page(12, .4, 2, .6, 0, 0).quarter());
    }

    @Test
    public void insufficientBilateralContrastCannotInventBody() throws Exception {
        assertFalse(new Page(12, .4, 2, .6, 70, 50).quarter());
    }

    @Test
    public void missingMiddleRuleCannotProveFrame() throws Exception {
        var p = normal();
        for (int y = 83; y <= 84; y++)
            for (int x = 10; x < W - 10; x++)
                if (x < 130 || x > p.right) p.gray[y * W + x] = (byte) 255;
        assertFalse(p.quarter());
    }

    @Test
    public void oneFlankCannotOwnStaffCrossing() throws Exception {
        var p = normal();
        for (int y = 0; y < H; y++)
            for (int x = p.right + 1; x < W; x++) p.gray[y * W + x] = (byte) 255;
        assertFalse(p.quarter());
    }

    @Test
    public void competingFiveRulePhaseCannotProveFrame() throws Exception {
        var p = normal();
        for (int r = 0; r < 5; r++) p.rule(60 + r * p.gap + p.gap / 2, 70);
        assertFalse(p.quarter());
    }

    @Test
    public void distantStaffCannotBorrowTheGlyph() throws Exception {
        var p = normal();
        p.staff = new SixteenthRestDetector.Staff(84, 132, 12, 0, 1);
        assertFalse(p.quarter());
    }

    @Test
    public void disconnectedUpperBodyCannotBecomeAWholeRestBody() throws Exception {
        var p = normal();
        for (int x = 130; x <= p.right; x++) p.gray[(p.first + 4) * W + x] = (byte) 255;
        assertFalse(p.quarter());
    }

    @Test
    public void staffAloneCannotBecomeQuarter() throws Exception {
        var p = normal();
        p.blankBody();
        assertFalse(p.quarter());
    }

    @Test
    public void parallelSpinesCannotBecomeQuarter() throws Exception {
        var p = normal();
        p.blankBody();
        for (int y = p.first; y <= p.last; y++)
            for (int x : new int[] {132, 133, 139, 140}) p.gray[y * W + x] = 5;
        assertFalse(p.quarter());
    }

    @Test
    public void literalRectangleCannotBecomeQuarter() throws Exception {
        var p = normal();
        p.blankBody();
        for (int y = 80; y <= 84; y++) for (int x = 130; x <= p.right; x++) p.gray[y * W + x] = 5;
        assertFalse(p.quarter());
    }

    @Test
    public void originalRasterAndMaskAreReadOnly() throws Exception {
        var p = normal();
        var g = p.gray.clone();
        var l = p.lines.clone();
        p.quarter();
        assertArrayEquals(g, p.gray);
        assertArrayEquals(l, p.lines);
    }
}
