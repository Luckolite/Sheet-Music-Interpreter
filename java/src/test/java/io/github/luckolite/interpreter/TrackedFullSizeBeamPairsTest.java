// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original physical shafts joining either one beam and a rule, or two written beams. */
public final class TrackedFullSizeBeamPairsTest {
    final TrackedParallelBeamPromotionTest draw = new TrackedParallelBeamPromotionTest();
    final int[] a = {210, 126, 1}, b = {250, 126, 1};

    byte[] page(float slope, int paper, boolean second) {
        byte[] p = draw.page(slope, paper, second, false);
        for (int x : new int[] {210, 250})
            for (int y = 75; y <= 126 + Math.round(slope * (x - 220)); y++) p[y * 480 + x] = 20;
        return p;
    }

    int[] shaft(int[] a, float slope) {
        return new int[] {a[0], a[1] + Math.round(slope * (a[0] - 220)), a[2]};
    }

    int legacy(byte[] p, float slope) {
        return PairedGraceBeamInk.countFullSize(
                p, 480, 240, shaft(a, slope), shaft(b, slope), 16, 80);
    }

    int tracked(byte[] p, float slope, boolean verified) {
        return PairedGraceBeamInk.countFullSize(
                p, 480, 240, shaft(a, slope), shaft(b, slope), 16, 80, draw.track(slope, verified));
    }

    void rule(float slope, int paper) {
        byte[] p = page(slope, paper, false);
        assertEquals("legacy rule pair witness", 2, legacy(p, slope));
        assertEquals(0, tracked(p, slope, true));
    }

    @Test
    public void physicalRuleCannotInventSecondFullSizeBeam() {
        rule(0, 190);
    }

    @Test
    public void risingPhysicalRuleCannotInventSecondBeam() {
        rule(.1f, 190);
    }

    @Test
    public void fallingPhysicalRuleCannotInventSecondBeam() {
        rule(-.1f, 190);
    }

    @Test
    public void darkPaperWithoutCompleteCoresIsNotRecovered() {
        byte[] p = page(0, 145, false);
        assertEquals(0, legacy(p, 0));
        assertEquals(0, tracked(p, 0, true));
    }

    @Test
    public void genuineTwoFullSizeBeamsRemain() {
        assertEquals(2, tracked(page(0, 190, true), 0, true));
    }

    @Test
    public void genuineRisingPairRemains() {
        assertEquals(2, tracked(page(.1f, 190, true), .1f, true));
    }

    @Test
    public void genuineFallingPairRemains() {
        assertEquals(2, tracked(page(-.1f, 190, true), -.1f, true));
    }

    @Test
    public void finiteFaintSecondBeamRemains() {
        byte[] p = page(0, 190, false);
        draw.body(p, 0, 105, 112, 35, 3);
        assertEquals(2, tracked(p, 0, true));
    }

    @Test
    public void unverifiedTrackKeepsLegacyPair() {
        assertEquals(2, tracked(page(0, 190, false), 0, false));
    }

    @Test
    public void compactGraceAPIKeepsOriginalEvidence() {
        byte[] p = page(0, 190, false);
        assertEquals(2, PairedGraceBeamInk.count(p, 480, 240, a, b, 16));
    }

    @Test
    public void opposedShaftsCannotSupplyPair() {
        assertEquals(
                0,
                PairedGraceBeamInk.countFullSize(
                        page(0, 190, true),
                        480,
                        240,
                        a,
                        new int[] {250, 126, -1},
                        16,
                        80,
                        draw.track(0, true)));
    }

    @Test
    public void pixelsAndShaftsAreImmutable() {
        byte[] p = page(.1f, 190, false), before = p.clone();
        int[] first = a.clone(), last = b.clone();
        tracked(p, .1f, true);
        assertArrayEquals(before, p);
        assertArrayEquals(first, a);
        assertArrayEquals(last, b);
    }
}
