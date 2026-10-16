// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original note body, isolated dot and attached descending stroke, with ruled paper. */
public class AttachedGraceDotInkTest {
    static final int W = 160, H = 160;

    byte[] page(boolean connected, int rule) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 250);
        for (int y = 76; y <= 84; y++)
            for (int x = 54; x <= 66; x++)
                if (Math.pow((x - 60) / 6., 2) + Math.pow((y - 80) / 4., 2) <= 1) p[y * W + x] = 20;
        for (int y = 82; y <= 86; y++)
            for (int x = 76; x <= 80; x++)
                if ((x - 78) * (x - 78) + (y - 84) * (y - 84) <= 5) p[y * W + x] = 20;
        if (connected)
            for (int x = 65; x <= 78; x++) {
                int y = (int) Math.round(80 + (x - 65) * 4 / 13.);
                for (int dy = -1; dy <= 1; dy++) p[(y + dy) * W + x] = 20;
            }
        for (int y = 82; y < 82 + rule; y++) for (int x = 10; x < W - 10; x++) p[y * W + x] = 20;
        return p;
    }

    boolean match(byte[] p) {
        return AttachedGraceDotInk.matches(
                p, W, H, 54, 76, 66, 84, 73, 76, 82, 80, 86, 21, 14, 100);
    }

    @Test
    public void attachedStrokeIsNotAnIndependentDot() {
        assertTrue(match(page(true, 0)));
    }

    @Test
    public void printedRuleDoesNotHideAttachment() {
        assertTrue(match(page(true, 2)));
    }

    @Test
    public void threePixelRuleDoesNotHideAttachment() {
        assertTrue(match(page(true, 3)));
    }

    @Test
    public void isolatedDotRemains() {
        assertFalse(match(page(false, 0)));
    }

    @Test
    public void sharedStaffRuleDoesNotJoinSeparateDot() {
        assertFalse(match(page(false, 2)));
    }

    @Test
    public void widerSharedRuleDoesNotJoinSeparateDot() {
        assertFalse(match(page(false, 3)));
    }

    @Test
    public void blankPaperDoesNotProveAttachment() {
        byte[] p = page(false, 0);
        Arrays.fill(p, (byte) 250);
        assertFalse(match(p));
    }

    @Test
    public void missingRasterDoesNotProveAttachment() {
        assertFalse(match(null));
    }

    @Test
    public void pixelsRemainUnchanged() {
        byte[] p = page(true, 2), copy = p.clone();
        match(p);
        assertArrayEquals(copy, p);
    }

    @Test
    public void unboundedScaleIsRejected() {
        assertFalse(
                AttachedGraceDotInk.matches(
                        page(true, 0),
                        W,
                        H,
                        54,
                        76,
                        66,
                        84,
                        73,
                        76,
                        82,
                        80,
                        86,
                        21,
                        Float.MAX_VALUE,
                        100));
    }
}
