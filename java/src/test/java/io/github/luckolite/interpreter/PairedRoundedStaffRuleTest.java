// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original rounded printed rules beside full-size beam cores; no score pixels. */
public class PairedRoundedStaffRuleTest {
    static final int W = 400, H = 220, G = 14, TOP = 60, A = 180;

    byte[] page(int span, boolean secondBeam) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 250);
        for (int x = 10; x < W - 10; x++)
            for (int y = TOP; y <= TOP + 4 * G; y += G)
                for (int dy = 0; dy < 2; dy++) gray[(y + dy) * W + x] = 0;
        for (int x = A - 12; x <= A + span + 12; x++)
            for (int dy = -2; dy <= 2; dy++)
                gray[(88 + dy) * W + x] = (byte) (secondBeam ? 0 : 153);
        for (int x = A; x <= A + span; x++) for (int y = 97; y <= 102; y++) gray[y * W + x] = 0;
        for (int y = 38; y <= 102; y++) {
            gray[y * W + A] = 0;
            gray[y * W + A + span] = 0;
        }
        return gray;
    }

    int count(byte[] gray, int span) {
        return PairedGraceBeamInk.countPrintedSize(
                gray, W, H, new int[] {A, 102, 1}, new int[] {A + span, 102, 1}, G, TOP, null);
    }

    @Test
    public void roundedRuleCannotSupplySecondPrintedRail() {
        assertEquals(0, count(page(38, false), 38));
    }

    @Test
    public void widerPrintedPairStillUsesStaffEvidence() {
        assertEquals(0, count(page(56, false), 56));
    }

    @Test
    public void darkSecondBeamSurvivesPrintedRulePhase() {
        assertEquals(2, count(page(38, true), 38));
    }

    @Test
    public void wideDarkSecondBeamSurvives() {
        assertEquals(2, count(page(56, true), 56));
    }

    @Test
    public void fullSizeRecoveryRejectsRoundedRule() {
        assertEquals(
                0,
                PairedGraceBeamInk.countFullSize(
                        page(38, false),
                        W,
                        H,
                        new int[] {A, 102, 1},
                        new int[] {A + 38, 102, 1},
                        G,
                        TOP,
                        null));
    }

    @Test
    public void pixelsAndShaftsRemainExact() {
        byte[] gray = page(38, false), copy = gray.clone();
        int[] a = {A, 102, 1}, b = {A + 38, 102, 1};
        PairedGraceBeamInk.countPrintedSize(gray, W, H, a, b, G, TOP, null);
        assertArrayEquals(copy, gray);
        assertArrayEquals(new int[] {A, 102, 1}, a);
        assertArrayEquals(new int[] {A + 38, 102, 1}, b);
    }
}
