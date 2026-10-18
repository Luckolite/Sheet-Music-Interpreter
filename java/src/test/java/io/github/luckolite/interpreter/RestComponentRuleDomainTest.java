// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original physical-rule/continuation roles, independent of rest glyph classification. */
public class RestComponentRuleDomainTest {
    static final int W = 760, H = 460, TOP = 128, BOTTOM = 248, L = 210, R = 230;
    static final float CENTER = 192, GAP = 16;

    static byte[] page(int phase, int from, int to) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int k = 0; k < 5; k++)
            for (int x = from; x <= to; x++) gray[(160 + 16 * k + phase) * W + x] = 0;
        return gray;
    }

    static boolean[] mask() {
        boolean[] mask = new boolean[BOTTOM - TOP + 1];
        for (int y = 144; y <= 224; y += 16) mask[y - TOP] = true;
        return mask;
    }

    static RestComponentRuleDomain.Domain bind(byte[] gray, boolean[] mask, int left, int right) {
        return RestComponentRuleDomain.bind(
                gray, W, H, left, right, TOP, BOTTOM, mask, CENTER, GAP);
    }

    @Test
    public void remoteOffStaffStripeCannotEraseLocalInk() {
        byte[] gray = page(0, 20, 740);
        for (int x = 500; x < 650; x++) gray[144 * W + x] = 0;
        boolean[] mask = mask(), before = mask.clone();
        byte[] pixels = gray.clone();
        var d = bind(gray, mask, L, R);
        assertNotNull(d);
        assertTrue(d.registered());
        assertFalse(d.line()[144 - TOP]);
        assertTrue(d.line()[160 - TOP]);
        assertArrayEquals(before, mask);
        assertArrayEquals(pixels, gray);
    }

    @Test
    public void fivePhysicalRulesStayMaskedAcrossGlyph() {
        byte[] gray = page(0, 20, 740);
        boolean[] mask = mask();
        for (int y = 170; y <= 185; y++) for (int x = 214; x <= 220; x++) gray[y * W + x] = 0;
        var d = bind(gray, mask, L, R);
        assertNotNull(d);
        for (int y = 160; y <= 224; y += 16) assertTrue(d.line()[y - TOP]);
    }

    @Test
    public void remoteOnlyFiveCannotCertifyLocalDomain() {
        byte[] gray = page(0, 500, 740);
        boolean[] mask = mask();
        var d = bind(gray, mask, L, R);
        assertNotNull(d);
        assertFalse(d.registered());
        assertSame(mask, d.line());
    }

    @Test
    public void clippedFlankCannotSupplyPhysicalFive() {
        byte[] gray = page(0, 0, 740);
        boolean[] mask = mask();
        var d = bind(gray, mask, 20, 40);
        assertNotNull(d);
        assertFalse(d.registered());
        assertSame(mask, d.line());
    }

    @Test
    public void differentRowPhaseCannotBeBorrowed() {
        byte[] gray = page(8, 20, 740);
        boolean[] mask = mask();
        var d = bind(gray, mask, L, R);
        assertNotNull(d);
        assertFalse(d.registered());
        assertSame(mask, d.line());
    }

    @Test
    public void restoredInkExtendsToOriginalPaperColumns() {
        byte[] gray = page(0, 20, 740);
        for (int x = 190; x <= 250; x++) gray[144 * W + x] = 0;
        var d = bind(gray, mask(), L, R);
        assertNotNull(d);
        assertTrue(d.registered());
        assertEquals(190, d.left());
        assertEquals(250, d.right());
        assertFalse(d.line()[144 - TOP]);
    }

    @Test
    public void restoredContinuingInkCannotBeClippedAtBudget() {
        byte[] gray = page(0, 20, 740);
        for (int x = 100; x <= 400; x++) gray[144 * W + x] = 0;
        assertNull(bind(gray, mask(), L, R));
    }

    @Test
    public void mirroredOffStaffStripeHasSameOwnership() {
        byte[] upper = page(0, 20, 740), gray = new byte[W * H];
        for (int y = 0; y < H; y++) System.arraycopy(upper, y * W, gray, (H - 1 - y) * W, W);
        boolean[] original = mask(), mirror = new boolean[original.length];
        for (int i = 0; i < original.length; i++) mirror[mirror.length - 1 - i] = original[i];
        var d =
                RestComponentRuleDomain.bind(
                        gray, W, H, L, R, H - 1 - BOTTOM, H - 1 - TOP, mirror, H - 1 - CENTER, GAP);
        assertNotNull(d);
        assertTrue(d.registered());
        assertFalse(d.line()[H - 1 - 144 - (H - 1 - BOTTOM)]);
    }
}
