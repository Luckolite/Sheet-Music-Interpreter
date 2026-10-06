// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class LongSlurFragmentInkTest {
    private static final int W = 700, H = 280, G = 16;
    private final byte[] gray = new byte[W * H];

    public LongSlurFragmentInkTest() {
        Arrays.fill(gray, (byte) 255);
    }

    private void curve(int sign, boolean flat, boolean broken) {
        for (int x = 200; x <= 296; x++) {
            if (broken && x > 244 && x < 252) continue;
            double f = (x - 200) / 96d;
            int cy = (int) Math.round(110 + sign * (flat ? 0 : 24 * 4 * f * (1 - f)));
            for (int y = cy - 2; y <= cy + 2; y++) gray[y * W + x] = 0;
        }
    }

    private boolean left() {
        return LongSlurFragmentInk.matches(gray, W, H, 200, 105, 207, 113, 30, G);
    }

    @Test
    public void completeUpperReturnCurveIsNotAHead() {
        curve(-1, false, false);
        assertTrue(left());
    }

    @Test
    public void completeLowerReturnCurveIsNotAHead() {
        curve(1, false, false);
        assertTrue(left());
    }

    @Test
    public void straightBeamIsNotSlur() {
        curve(-1, true, false);
        assertFalse(left());
    }

    @Test
    public void disconnectedStrokeIsInsufficient() {
        curve(-1, false, true);
        assertFalse(left());
    }

    @Test
    public void centerOfCurveCannotBeAnEndpoint() {
        curve(-1, false, false);
        assertFalse(LongSlurFragmentInk.matches(gray, W, H, 246, 84, 252, 89, 20, G));
    }

    @Test
    public void compactFilledOvalIsPreserved() {
        for (int y = 100; y <= 118; y++)
            for (int x = 195; x <= 217; x++)
                if ((x - 206) * (x - 206) / 121d + (y - 109) * (y - 109) / 81d <= 1)
                    gray[y * W + x] = 0;
        assertFalse(left());
    }

    @Test
    public void thinStaffRulesDoNotInventReturnCurve() {
        for (int y = 96; y < 145; y += 16) for (int x = 0; x < W; x++) gray[y * W + x] = 0;
        assertFalse(left());
    }

    @Test
    public void shortGraceStemAtCurveEndProtectsRealHead() {
        curve(-1, false, false);
        for (int y = 87; y <= 110; y++) for (int x = 204; x <= 205; x++) gray[y * W + x] = 0;
        assertFalse(left());
    }

    @Test
    public void nullImageIsRejected() {
        assertFalse(LongSlurFragmentInk.matches(null, W, H, 200, 105, 207, 113, 30, G));
    }

    @Test
    public void invalidScaleIsRejected() {
        assertFalse(LongSlurFragmentInk.matches(gray, W, H, 200, 105, 207, 113, 30, Float.NaN));
    }

    @Test
    public void sourcePixelsArePreserved() {
        curve(-1, false, false);
        var copy = gray.clone();
        left();
        assertArrayEquals(copy, gray);
    }

    @Test
    public void paleReturnCurveSurvivesAfterLargerEarlierRejectedInk() {
        curve(-1, false, false);
        for (int i = 0; i < gray.length; i++) if (gray[i] == 0) gray[i] = (byte) 140;
        // This disjoint filled block is seeded first and cannot prove a long return curve.
        for (int y = 90; y <= 105; y++) for (int x = 160; x <= 200; x++) gray[y * W + x] = 0;
        byte[] before = gray.clone();
        assertTrue(left());
        assertTrue(left());
        assertArrayEquals(before, gray);
        Arrays.fill(gray, (byte) 255);
        before = gray.clone();
        assertFalse(left());
        assertArrayEquals(before, gray);
    }

    @Test
    public void thresholdReturnCurvesRestoreWithoutRetainingAnEarlierRaster() {
        for (int shade : new int[] {80, 110, 140}) {
            Arrays.fill(gray, (byte) 255);
            curve(1, false, false);
            for (int i = 0; i < gray.length; i++) if (gray[i] == 0) gray[i] = (byte) shade;
            byte[] before = gray.clone();
            assertTrue(left());
            assertTrue(left());
            assertArrayEquals(before, gray);
            Arrays.fill(gray, (byte) 255);
            before = gray.clone();
            assertFalse(left());
            assertArrayEquals(before, gray);
        }
        curve(1, false, false);
        for (int i = 0; i < gray.length; i++) if (gray[i] == 0) gray[i] = (byte) 141;
        byte[] before = gray.clone();
        assertFalse(left());
        assertArrayEquals(before, gray);
    }
}
