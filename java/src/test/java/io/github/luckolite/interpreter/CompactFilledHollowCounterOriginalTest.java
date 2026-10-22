// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent literal hollow bodies; semantic class2 may paint their entire raw counter. */
public class CompactFilledHollowCounterOriginalTest {
    static final int W = 200, H = 180, L = 85, R = 97, T = 75, B = 85;
    static final float G = 13.25f, CX = 91, CY = 80;

    record F(byte[] labels, byte[] gray) {}

    static F body(
            boolean stem, int paper, boolean gradient, boolean shadow, boolean staff, float tilt) {
        byte[] g = new byte[W * H], l = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int p = gradient ? Math.max(150, Math.min(245, paper + (x - 91) * 2)) : paper;
                // A localized photograph shadow changes PAPER, not the head's hollow role.
                if (shadow && x >= L - 3 && x <= R + 3 && y >= T - 3 && y <= B + 3) p = 180;
                g[y * W + x] = (byte) p;
            }
        for (int y = T; y <= B; y++)
            for (int x = L; x <= R; x++) {
                double dx = (x - CX) / 6.,
                        dy = (y - CY + tilt * (x - CX)) / 4.5,
                        q = dx * dx + dy * dy;
                if (q <= 1) {
                    l[y * W + x] = 2;
                    if (q >= .42) g[y * W + x] = 35;
                }
            }
        if (stem)
            for (int y = 40; y <= 80; y++)
                for (int x = 96; x <= 97; x++) {
                    g[y * W + x] = 35;
                    l[y * W + x] = 1;
                }
        if (staff)
            for (int y : new int[] {54, 67, 80, 93, 106})
                for (int x = 35; x <= 165; x++) {
                    g[y * W + x] = 55;
                    l[y * W + x] = 4;
                }
        return new F(l, g);
    }

    static boolean filled(F f) {
        return CompactFilledHeadCore.isFilled(f.labels, f.gray, W, H, L, T, R, B, CX, CY, G);
    }

    @Test
    public void genuineCompactHollowHalfOnWhitePaper() {
        assertFalse(filled(body(true, 255, false, false, false, .3f)));
    }

    @Test
    public void genuineCompactHollowWholeOnWhitePaper() {
        assertFalse(filled(body(false, 255, false, false, false, -.15f)));
    }

    @Test
    public void shadedPaperBelowAbsolute185StillLeavesHollowHalf() {
        assertFalse(filled(body(true, 180, false, false, false, .3f)));
    }

    @Test
    public void gradientPaperCounterIsNotPaleFilledInk() {
        assertFalse(filled(body(true, 190, true, false, false, .3f)));
    }

    @Test
    public void localPaperShadowDoesNotFillWholeCounter() {
        assertFalse(filled(body(false, 235, false, true, false, 0f)));
    }

    @Test
    public void genuineHalfCounterSurvivesCenterStaffRule() {
        assertFalse(filled(body(true, 255, false, false, true, .3f)));
    }

    @Test
    public void shadedCounterPlusCrossingRuleIsNotFilled() {
        assertFalse(filled(body(true, 235, false, true, true, .3f)));
    }

    @Test
    public void tiltedGradientHollowIsRejectedReadOnly() {
        var f = body(false, 215, true, false, false, -.3f);
        byte[] l = f.labels.clone(), g = f.gray.clone();
        assertFalse(filled(f));
        assertArrayEquals(l, f.labels);
        assertArrayEquals(g, f.gray);
    }
}
