// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent normal-size 19x15 heads: a long own stem alone does not mean quarter. */
public class NormalPaleHollowOriginalTest {
    static final int W = 240, H = 200, L = 111, R = 129, T = 93, B = 107;
    static final float CX = 120, CY = 100, G = 13.5f;

    record F(byte[] gray, byte[] labels) {}

    static F page(
            int paper,
            boolean gradient,
            boolean localShadow,
            boolean staff,
            boolean dot,
            boolean filled) {
        byte[] g = new byte[W * H], l = new byte[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int p = gradient ? Math.max(150, Math.min(250, paper + (x - 120) * 2)) : paper;
                if (localShadow && x >= L - 3 && x <= R + 3 && y >= T - 3 && y <= B + 3) p = 180;
                g[y * W + x] = (byte) p;
            }
        for (int y = T; y <= B; y++)
            for (int x = L; x <= R; x++) {
                double dx = (x - CX) / 9.,
                        dy = (y - CY + .24 * (x - CX)) / 6.,
                        q = dx * dx + dy * dy;
                if (q <= 1) {
                    l[y * W + x] = 2;
                    if (filled) g[y * W + x] = (byte) (q < .42 ? 185 : 175);
                    else if (q >= .42) g[y * W + x] = 35;
                }
            }
        // All figures carry the SAME genuine, long up-stem, so stem ownership cannot choose
        // duration.
        for (int y = 45; y <= 100; y++)
            for (int x = 127; x <= 128; x++) {
                g[y * W + x] = 35;
                l[y * W + x] = 1;
            }
        if (staff)
            for (int y : new int[] {73, 87, 100, 114, 127})
                for (int x = 55; x <= 185; x++) {
                    g[y * W + x] = 55;
                    l[y * W + x] = 4;
                }
        if (dot)
            for (int y = 96; y <= 98; y++)
                for (int x = 141; x <= 143; x++) {
                    g[y * W + x] = 35;
                    l[y * W + x] = 5;
                }
        return new F(g, l);
    }

    static boolean filled(F p) {
        return PaleFilledHeadBody.isFilled(p.gray, W, H, L, T, R, B, CX, CY, G);
    }

    @Test
    public void genuineNormalHalfIsNotAQuarter() {
        assertFalse(filled(page(255, false, false, false, false, false)));
    }

    @Test
    public void genuineDottedHalfIsNotADottedQuarter() {
        assertFalse(filled(page(255, false, false, false, true, false)));
    }

    @Test
    public void local180PaperShadowRetainsHalfCounter() {
        assertFalse(filled(page(235, false, true, false, false, false)));
    }

    @Test
    public void local180PaperShadowRetainsDottedHalfCounter() {
        assertFalse(filled(page(235, false, true, false, true, false)));
    }

    @Test
    public void globallyDarkPaperDoesNotBecomePaleFilledInk() {
        assertFalse(filled(page(180, false, false, false, false, false)));
    }

    @Test
    public void gradientPaperRetainsHollowHalf() {
        assertFalse(filled(page(220, true, false, false, false, false)));
    }

    @Test
    public void fullStaffRuleThroughCounterRetainsHalf() {
        assertFalse(filled(page(255, false, false, true, false, false)));
    }

    @Test
    public void shadedStaffCrossedDottedHalfRetainsBothCounterParts() {
        assertFalse(filled(page(235, false, true, true, true, false)));
    }

    @Test
    public void genuinePaleQuarterWithSameStemIsFilledReadOnly() {
        var p = page(235, false, false, false, false, true);
        byte[] a = p.gray.clone(), b = p.labels.clone();
        assertTrue(filled(p));
        assertArrayEquals(a, p.gray);
        assertArrayEquals(b, p.labels);
    }
}
