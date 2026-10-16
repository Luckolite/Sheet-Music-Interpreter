// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic curves and antialiased recovery fringes; no scanned pixels. */
public class DetachedSlurRecoveryTest {
    private byte[] curve(boolean up, boolean bowed) {
        byte[] gray = new byte[240 * 240];
        Arrays.fill(gray, (byte) 255);
        int side = up ? -1 : 1, center = up ? 80 : 160, refined = up ? 88 : 152;
        for (int dx = -40; dx <= 40; dx++) {
            int y = center - (bowed ? side * Math.round(dx * dx / 324f) : 0);
            for (int dy = -2; dy <= 2; dy++) gray[(y + dy) * 240 + 100 + dx] = 20;
        }
        for (int y = Math.min(refined, 120); y <= Math.max(refined, 120); y++)
            gray[y * 240 + 100] = 20;
        return gray;
    }

    private boolean check(byte[] gray, int[] strict, int[] pale) {
        return DetachedSlurStemCap.crossesRecovery(gray, 240, 240, strict, pale, 12, 170);
    }

    @Test
    public void upperFringeAndShiftRetainDetachedOwnership() {
        assertTrue(check(curve(true, true), new int[] {100, 88, -1}, new int[] {101, 75, -1}));
    }

    @Test
    public void lowerFringeAndShiftRetainDetachedOwnership() {
        assertTrue(check(curve(false, true), new int[] {100, 152, 1}, new int[] {101, 165, 1}));
    }

    @Test
    public void straightBeamIsNotDetachedSlur() {
        assertFalse(check(curve(true, false), new int[] {100, 88, -1}, new int[] {101, 75, -1}));
    }

    @Test
    public void remoteShaftCannotBorrowCurve() {
        assertFalse(check(curve(true, true), new int[] {100, 88, -1}, new int[] {105, 75, -1}));
    }

    @Test
    public void oppositeStemCannotBorrowCurve() {
        assertFalse(check(curve(true, true), new int[] {100, 88, 1}, new int[] {101, 75, -1}));
    }

    @Test
    public void distantPaleFringeCannotSearchForInk() {
        assertFalse(check(curve(true, true), new int[] {100, 88, -1}, new int[] {101, 69, -1}));
    }

    @Test
    public void connectedShaftCannotBecomeBlankCap() {
        byte[] gray = curve(true, true);
        for (int y = 80; y <= 88; y++) gray[y * 240 + 101] = 20;
        assertFalse(check(gray, new int[] {100, 88, -1}, new int[] {101, 75, -1}));
    }

    @Test
    public void proofDoesNotChangeRasterOrStemArrays() {
        byte[] gray = curve(true, true), copy = gray.clone();
        int[] strict = {100, 88, -1}, pale = {101, 75, -1};
        assertTrue(check(gray, strict, pale));
        assertArrayEquals(copy, gray);
        assertArrayEquals(new int[] {100, 88, -1}, strict);
        assertArrayEquals(new int[] {101, 75, -1}, pale);
    }
}
