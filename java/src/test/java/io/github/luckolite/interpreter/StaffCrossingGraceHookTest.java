// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original curved and straight connectors crossing a continuous printed rule. */
public class StaffCrossingGraceHookTest {
    static final int W = 160, H = 160;

    byte[] page(int thickness, boolean curve) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 255);
        for (int x = 65; x <= 83; x++) {
            double t = (x - 65) / 18.;
            int y = (int) Math.round(82 - 9 * t + (curve ? 3 * Math.sin(Math.PI * t) : 0));
            for (int dy = -1; dy <= 1; dy++) p[(y + dy) * W + x] = 0;
        }
        for (int y = 77; y < 77 + thickness; y++) for (int x = 5; x < W - 5; x++) p[y * W + x] = 0;
        return p;
    }

    boolean matches(byte[] p) {
        return GraceConnectorInk.isHook(p, W, H, 66, 74, 81, 84, 74, 80, 48, 14, 100);
    }

    @Test
    public void narrowRuleDoesNotSplitOwnedHook() {
        assertTrue(matches(page(2, true)));
    }

    @Test
    public void threePixelRuleDoesNotSplitOwnedHook() {
        assertTrue(matches(page(3, true)));
    }

    @Test
    public void straightStrokeStillHasNoReturningBend() {
        assertFalse(matches(page(2, false)));
    }

    @Test
    public void broadBandCannotBeSkipped() {
        assertFalse(matches(page(5, true)));
    }

    @Test
    public void rawPixelsAreUnchanged() {
        byte[] p = page(2, true), copy = p.clone();
        matches(p);
        assertArrayEquals(copy, p);
    }
}
