// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original curves, straight rails and shaft connections; no scanned pixels. */
public class DetachedSlurStemCapTest {
    static final int W = 240, H = 240, X = 100;
    byte[] gray = new byte[W * H];

    void setup(boolean up, int bend, int thickness, boolean connected, boolean broken) {
        Arrays.fill(gray, (byte) 255);
        int center = up ? 80 : 160, side = up ? -1 : 1;
        for (int dx = -36; dx <= 36; dx++) {
            if (broken && dx == 20) continue;
            int y = center - side * bend * Math.round(dx * dx / 324f);
            for (int dy = -thickness / 2; dy <= thickness / 2; dy++)
                gray[(y + dy) * W + X + dx] = 20;
        }
        int refined = up ? 88 : 152;
        for (int y = Math.min(refined, 120); y <= Math.max(refined, 120); y++) gray[y * W + X] = 20;
        if (connected)
            for (int y = Math.min(center, refined); y <= Math.max(center, refined); y++)
                gray[y * W + X] = 20;
    }

    boolean matches(boolean up) {
        return DetachedSlurStemCap.matches(
                gray, W, H, X, up ? 78 : 162, up ? 88 : 152, up ? -1 : 1, 12, 170);
    }

    @Test
    public void detachedUpperCurveSeparatesShaft() {
        setup(true, 1, 5, false, false);
        assertTrue(matches(true));
    }

    @Test
    public void detachedLowerCurveSeparatesShaft() {
        setup(false, 1, 5, false, false);
        assertTrue(matches(false));
    }

    @Test
    public void straightRailCannotBecomeSlur() {
        setup(true, 0, 5, false, false);
        assertFalse(matches(true));
    }

    @Test
    public void oppositeBendCannotCapShaft() {
        setup(true, -1, 5, false, false);
        assertFalse(matches(true));
    }

    @Test
    public void connectedInkRetainsOriginalEndpoint() {
        setup(true, 1, 5, true, false);
        assertFalse(matches(true));
    }

    @Test
    public void brokenCurveCannotSupplyProof() {
        setup(true, 1, 5, false, true);
        assertFalse(matches(true));
    }

    @Test
    public void thickStrokeCannotSupplyThinCurve() {
        setup(true, 1, 9, false, false);
        assertFalse(matches(true));
    }

    @Test
    public void imageRemainsExact() {
        setup(true, 1, 5, false, false);
        byte[] before = gray.clone();
        matches(true);
        assertArrayEquals(before, gray);
    }

    @Test
    public void invalidGeometryRejected() {
        setup(true, 1, 5, false, false);
        assertFalse(DetachedSlurStemCap.matches(gray, W, H, 0, 78, 88, -1, 12, 170));
        assertFalse(DetachedSlurStemCap.matches(gray, W, H, X, 78, 88, -1, Float.NaN, 170));
    }
}
