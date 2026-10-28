// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original bounded normals; complete musical ownership remains the caller's responsibility. */
public final class BeamRelativePaperTest {
    private static final int W = 96, H = 96, X = 48, Y = 48;

    private static byte[] scene(int paper, int ink, int thickness) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) paper);
        for (int y = 20; y < 76; y++)
            for (int x = X - thickness / 2; x < X - thickness / 2 + thickness; x++)
                gray[y * W + x] = (byte) ink;
        return gray;
    }

    private static void stripe(byte[] gray, int dx, int tone) {
        for (int y = 20; y < 76; y++) gray[y * W + X + dx] = (byte) tone;
    }

    private static void check(byte[] gray, int gap, boolean legacy, boolean relative) {
        byte[] before = gray.clone();
        assertEquals(
                legacy,
                PrintedBeamRawContrast.shaftWithEnclosingPaper(
                        gray, W, H, X, Y, gap, n -> assertTrue(n >= 0)));
        assertArrayEquals(before, gray);
        assertEquals(
                relative,
                PrintedBeamRawContrast.shaftWithRelativePaper(
                        gray, W, H, X, Y, gap, n -> assertTrue(n >= 0)));
        assertArrayEquals(before, gray);
    }

    @Test
    public void nonclippingExposureRetainsTheSameClosedNormal() {
        check(scene(225, 185, 1), 14, true, true);
        check(scene(205, 165, 1), 14, false, true);
    }

    @Test
    public void thirtyContrastRemainsTheBoundary() {
        check(scene(205, 175, 1), 14, false, true);
        check(scene(205, 176, 1), 14, false, false);
    }

    @Test
    public void absolutePairPreservesPositiveDespiteNearerRelativeMidtones() {
        byte[] gray = scene(225, 175, 1);
        stripe(gray, -1, 140);
        stripe(gray, 1, 190);
        stripe(gray, -2, 205);
        stripe(gray, 2, 205);
        check(gray, 14, true, true);
    }

    @Test
    public void absolutePairGeometricRefusalNeverRetriesRelativeBanks() {
        byte[] gray = scene(225, 175, 1);
        for (int dx : new int[] {-1, 1, -2, 2}) stripe(gray, dx, 195);
        for (int dx : new int[] {-3, 3, -4, 4}) stripe(gray, dx, 205);
        check(gray, 8, false, false);
    }

    @Test
    public void oneMissingAbsoluteBankAllowsACompleteRelativePair() {
        byte[] gray = scene(205, 175, 1);
        stripe(gray, -1, 225);
        check(gray, 14, false, true);
    }

    @Test
    public void flatPaperHasNoClosedInkNormal() {
        check(scene(205, 205, 1), 14, false, false);
    }

    @Test
    public void broadShadedBandIsNotAThinShaft() {
        check(scene(205, 165, 9), 14, false, false);
    }

    @Test
    public void oneRelativePaperBankIsInsufficient() {
        byte[] gray = scene(205, 165, 1);
        for (int y = 0; y < H; y++) for (int x = 0; x <= X; x++) gray[y * W + x] = (byte) 165;
        check(gray, 14, false, false);
    }

    @Test
    public void monotoneGradientCannotSupplyTwoPaperBanks() {
        byte[] gray = new byte[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) gray[y * W + x] = (byte) (120 + x);
        check(gray, 14, false, false);
    }

    @Test
    public void exactFundingWorksAndShortAllowancesPropagateRefusal() {
        byte[] gray = scene(205, 175, 1), before = gray.clone();
        long[] required = {0};
        assertTrue(
                PrintedBeamRawContrast.shaftWithRelativePaper(
                        gray, W, H, X, Y, 14, n -> required[0] += n));
        assertEquals(233, required[0]);
        long[] exact = {required[0]};
        assertTrue(
                PrintedBeamRawContrast.shaftWithRelativePaper(
                        gray,
                        W,
                        H,
                        X,
                        Y,
                        14,
                        n -> {
                            if (n > exact[0]) throw new IllegalStateException("unfunded");
                            exact[0] -= n;
                        }));
        assertEquals(0, exact[0]);
        for (long allowance : new long[] {0, 1, required[0] - 1}) {
            long[] remaining = {allowance};
            try {
                PrintedBeamRawContrast.shaftWithRelativePaper(
                        gray,
                        W,
                        H,
                        X,
                        Y,
                        14,
                        n -> {
                            if (n > remaining[0]) throw new IllegalStateException("unfunded");
                            remaining[0] -= n;
                        });
                fail("Funding refusal must propagate: " + allowance);
            } catch (IllegalStateException expected) {
                assertEquals("unfunded", expected.getMessage());
            }
            assertArrayEquals(before, gray);
        }
        assertArrayEquals(before, gray);
    }
}
