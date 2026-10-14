// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConvergingStaffStrokesTest {
    static final int W = 600, H = 180;
    static final int[] ROWS = {40, 60, 80, 100, 120};

    byte[] page(int kind) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int x = 40; x <= 520; x++) {
            double t = (x - 40) / 480d;
            int upper = (int) Math.round(60 + (kind == 1 ? 0 : 12 * t));
            int lower = (int) Math.round(kind == 1 ? 84 : 84 - 12 * t);
            if (kind == 2) {
                upper += (int) Math.round(8 * Math.sin(t * Math.PI));
                lower += (int) Math.round(8 * Math.sin(t * Math.PI));
            }
            if (kind == 3 && x % 30 < 10) continue;
            for (int d = 0; d < 2; d++) {
                g[(upper + d) * W + x] = 80;
                g[(lower + d) * W + x] = 80;
            }
        }
        return g;
    }

    @Test
    public void convergingArmsAreDetected() {
        assertTrue(ConvergingStaffStrokes.matches(page(0), W, H, ROWS, 20));
    }

    @Test
    public void parallelPartialStaffIsPreserved() {
        assertFalse(ConvergingStaffStrokes.matches(page(1), W, H, ROWS, 20));
    }

    @Test
    public void curvedDecorationIsNotStraightWedge() {
        assertFalse(ConvergingStaffStrokes.matches(page(2), W, H, ROWS, 20));
    }

    @Test
    public void interruptedTextureIsRejected() {
        assertFalse(ConvergingStaffStrokes.matches(page(3), W, H, ROWS, 20));
    }

    @Test
    public void sourcePixelsAreUnchanged() {
        var g = page(0);
        var before = g.clone();
        ConvergingStaffStrokes.matches(g, W, H, ROWS, 20);
        assertArrayEquals(before, g);
    }

    @Test
    public void outerHairpinCannotSupplyExtraStaff() {
        var p = UnsupportedStaffAliasTest.page(2);
        for (int x = 40; x < 760; x++) {
            double t = (x - 40) / 720d;
            int upper = 66 + (int) Math.round(8 * t), lower = 82 - (int) Math.round(8 * t);
            for (int d = 0; d < 2; d++) {
                p.gray()[(upper + d) * 800 + x] = 80;
                p.gray()[(lower + d) * 800 + x] = 80;
            }
        }
        assertFalse(UnsupportedStaffAliasTest.contains(p, false));
    }

    @Test
    public void twoParallelPrintedRulesStayAvailable() {
        var p = UnsupportedStaffAliasTest.page(2);
        UnsupportedStaffAliasTest.rules(p, 66, 80, 2, 40, 760, 0);
        assertTrue(UnsupportedStaffAliasTest.contains(p, false));
    }
}
