// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original paired grace beams, narrow staff rules, and an oblique grace slash. */
public final class PairedGraceBeamInkTest {
    private static final int W = 240, H = 180;

    private byte[] page(int beams, int paper, boolean slash, boolean rule) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        for (int x = 100; x <= 124; x++)
            for (int i = 0; i < beams; i++)
                for (int dy = 0; dy < 4; dy++)
                    p[(60 + Math.round((x - 100) * .125f) + i * 8 + dy) * W + x] = 20;
        if (slash)
            for (int x = 100; x <= 124; x++)
                for (int dy = 0; dy < 2; dy++)
                    p[(77 - Math.round((x - 100) * .65f) + dy) * W + x] = 20;
        if (rule) for (int x = 80; x <= 145; x++) p[78 * W + x] = 60;
        for (int y = 60; y < 100; y++) p[y * W + 100] = 20;
        for (int y = 63; y < 107; y++) p[y * W + 124] = 20;
        return p;
    }

    private int count(byte[] p) {
        return PairedGraceBeamInk.count(
                p, W, H, new int[] {100, 60, -1}, new int[] {124, 63, -1}, 14);
    }

    @Test
    public void twoParallelBeamsAreRecovered() {
        assertEquals(2, count(page(2, 255, false, false)));
    }

    @Test
    public void risingGracePairKeepsBothParallelBeams() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 255);
        for (int x = 100; x <= 124; x++)
            for (int i = 0; i < 2; i++)
                for (int dy = 0; dy < 4; dy++)
                    p[(72 - Math.round((x - 100) * .5f) + i * 8 + dy) * W + x] = 20;
        for (int y = 72; y < 110; y++) p[y * W + 100] = 20;
        for (int y = 60; y < 102; y++) p[y * W + 124] = 20;
        assertEquals(
                2,
                PairedGraceBeamInk.count(
                        p, W, H, new int[] {100, 72, -1}, new int[] {124, 60, -1}, 14));
    }

    private byte[] slurAtShaftTip(int beams) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 255);
        for (int x = 100; x <= 124; x++) {
            int rise = Math.round((x - 100) * .08f);
            for (int b = 0; b < beams; b++)
                for (int dy = 0; dy < 4; dy++) p[(60 - rise + b * 7 + dy) * W + x] = 20;
            for (int dy = 0; dy < 4; dy++) p[(60 - Math.round((x - 100) * .45f) + dy) * W + x] = 20;
        }
        for (int y = 60; y < 110; y++) p[y * W + 100] = 20;
        for (int y = 49; y < 110; y++) p[y * W + 124] = 20;
        return p;
    }

    @Test
    public void slurAtOneShaftTipDoesNotHideParallelPair() {
        assertEquals(
                2,
                PairedGraceBeamInk.count(
                        slurAtShaftTip(2),
                        W,
                        H,
                        new int[] {100, 60, -1},
                        new int[] {124, 49, -1},
                        14));
    }

    @Test
    public void slurAtTipCannotUpgradeSingleBeam() {
        assertEquals(
                0,
                PairedGraceBeamInk.count(
                        slurAtShaftTip(1),
                        W,
                        H,
                        new int[] {100, 60, -1},
                        new int[] {124, 49, -1},
                        14));
    }

    @Test
    public void shadedTwoBeamPairIsRecovered() {
        assertEquals(2, count(page(2, 180, false, false)));
    }

    @Test
    public void thinStaffRuleDoesNotBecomeThirdBeam() {
        assertEquals(2, count(page(2, 180, false, true)));
    }

    @Test
    public void singleBeamIsNotUpgraded() {
        assertEquals(0, count(page(1, 255, false, false)));
    }

    @Test
    public void diagonalGraceSlashIsNotAParallelBeam() {
        assertEquals(0, count(page(1, 255, true, false)));
    }

    @Test
    public void missingSecondStemDoesNotProveAPair() {
        assertEquals(
                0,
                PairedGraceBeamInk.count(
                        page(2, 255, false, false), W, H, new int[] {100, 60, -1}, null, 14));
    }

    @Test
    public void oppositeStemDirectionsDoNotFormAPair() {
        assertEquals(
                0,
                PairedGraceBeamInk.count(
                        page(2, 255, false, false),
                        W,
                        H,
                        new int[] {100, 60, -1},
                        new int[] {124, 63, 1},
                        14));
    }

    @Test
    public void reversedInputOrderPreservesCount() {
        assertEquals(
                2,
                PairedGraceBeamInk.count(
                        page(2, 255, false, false),
                        W,
                        H,
                        new int[] {124, 63, -1},
                        new int[] {100, 60, -1},
                        14));
    }

    @Test
    public void sourcePixelsArePreserved() {
        byte[] p = page(2, 180, false, false), before = p.clone();
        count(p);
        assertArrayEquals(before, p);
    }

    private byte[] fullPair(boolean detached) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 255);
        for (int x = 80; x <= 120; x++) for (int y = 50; y <= 56; y++) p[y * W + x] = 0;
        for (int x = 80; x <= (detached ? 115 : 120); x++)
            for (int y = 64; y <= 70; y++) p[y * W + x] = 0;
        for (int y = 50; y < 120; y++) {
            p[y * W + 80] = 0;
            p[y * W + 120] = 0;
        }
        return p;
    }

    @Test
    public void fullSizePairRequiresBothBeamEndpoints() {
        assertEquals(
                2,
                PairedGraceBeamInk.countFullSize(
                        fullPair(false),
                        W,
                        H,
                        new int[] {80, 50, -1},
                        new int[] {120, 50, -1},
                        20));
    }

    @Test
    public void longPartialHookDoesNotUpgradeItsNeighbour() {
        assertEquals(
                0,
                PairedGraceBeamInk.countFullSize(
                        fullPair(true), W, H, new int[] {80, 50, -1}, new int[] {120, 50, -1}, 20));
    }

    @Test
    public void reversingPartialHookStillRejectsIt() {
        assertEquals(
                0,
                PairedGraceBeamInk.countFullSize(
                        fullPair(true), W, H, new int[] {120, 50, -1}, new int[] {80, 50, -1}, 20));
    }

    private void paintSeparatedFullSizeRails(byte[] gray, int rails) {
        Arrays.fill(gray, (byte) 255);
        for (int rail = 0; rail < rails; rail++)
            for (int x = 60; x <= 100; x++)
                for (int dy = 0; dy < 6; dy++) gray[(60 + rail * 12 + dy) * W + x] = 0;
        for (int y = 60; y < 130; y++) {
            gray[y * W + 60] = 0;
            gray[y * W + 100] = 0;
        }
    }

    @Test
    public void fourSeparatedFullSizeRailsAreRejectedAndPreserveCaller() {
        byte[] gray = new byte[W * H];
        paintSeparatedFullSizeRails(gray, 4);
        int[] a = {60, 60, -1}, b = {100, 60, -1};
        byte[] before = gray.clone();
        int[] aBefore = a.clone(), bBefore = b.clone();
        assertEquals(0, PairedGraceBeamInk.countFullSize(gray, W, H, a, b, 20));
        assertArrayEquals(before, gray);
        assertArrayEquals(aBefore, a);
        assertArrayEquals(bBefore, b);
    }

    @Test
    public void manyCoreRejectionDoesNotLeakIntoLaterTwoOrThreeRailQueries() {
        byte[] gray = new byte[W * H];
        int[] a = {60, 60, -1}, b = {100, 60, -1};
        int[] aBefore = a.clone(), bBefore = b.clone();
        for (int rails : new int[] {4, 2, 3, 4, 2, 3}) {
            paintSeparatedFullSizeRails(gray, rails);
            byte[] before = gray.clone();
            assertEquals(
                    rails == 4 ? 0 : rails, PairedGraceBeamInk.countFullSize(gray, W, H, a, b, 20));
            assertArrayEquals(before, gray);
            assertArrayEquals(aBefore, a);
            assertArrayEquals(bBefore, b);
        }
    }
}
