// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original single/double beam bodies beside independently drawn five printed rules. */
public final class TrackedParallelBeamPromotionTest {
    static final int W = 480, H = 240, X = 220, G = 16, TOP = 80, Y = 119;

    byte[] page(float slope, int paper, boolean realSecond, boolean longSecond) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        for (int x = 20; x < W - 20; x++)
            for (int line = 0; line < 5; line++)
                for (int dy = 0; dy <= 1; dy++)
                    p[(TOP + line * G + Math.round(slope * (x - X)) + dy) * W + x] =
                            (byte) (paper - 80);
        int[] blur = {40, 60, 80, 60, 40};
        for (int x = 20; x < W - 20; x++)
            for (int dy = -2; dy <= 2; dy++)
                p[(112 + Math.round(slope * (x - X)) + dy) * W + x] = (byte) (paper - blur[dy + 2]);
        body(p, slope, paper - 140, 126, 70, 3);
        if (realSecond) body(p, slope, paper - 140, 112, longSecond ? 100 : 35, 3);
        return p;
    }

    void body(byte[] p, float slope, int shade, int center, int span, int radius) {
        for (int x = X - span; x <= X + span; x++)
            for (int dy = -radius; dy <= radius; dy++)
                p[(center + Math.round(slope * (x - X)) + dy) * W + x] = (byte) Math.max(0, shade);
    }

    StaffPitchTrack track(float slope, boolean verified) {
        return verified
                ? StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {20, TOP + 4 * G + slope * (20 - X), G},
                                new float[] {W - 20, TOP + 4 * G + slope * (W - 20 - X), G}))
                : StaffPitchTrack.linear(W, TOP + 4 * G + slope * (W * .5f - X), G, slope);
    }

    boolean legacy(byte[] p) {
        return ParallelBeamTip.matches(p, W, H, X, Y, G);
    }

    boolean tracked(byte[] p, float slope, boolean verified) {
        return ParallelBeamTip.matches(p, W, H, X, Y, G, track(slope, verified));
    }

    void rule(float slope, int paper) {
        byte[] p = page(slope, paper, false, false);
        assertTrue("legacy promotion witness", legacy(p));
        assertFalse(tracked(p, slope, true));
    }

    @Test
    public void levelRuleCannotPromoteSoleBeam() {
        rule(0, 190);
    }

    @Test
    public void risingRuleCannotPromoteSoleBeam() {
        rule(.1f, 190);
    }

    @Test
    public void fallingRuleCannotPromoteSoleBeam() {
        rule(-.1f, 190);
    }

    @Test
    public void darkPaperRuleCannotPromoteSoleBeam() {
        rule(0, 145);
    }

    @Test
    public void lightPaperWithoutCompleteCoresIsNotPromoted() {
        byte[] p = page(0, 235, false, false);
        assertFalse(legacy(p));
        assertFalse(tracked(p, 0, true));
    }

    @Test
    public void genuineLevelDoubleBeamRemains() {
        byte[] p = page(0, 190, true, false);
        assertTrue(tracked(p, 0, true));
    }

    @Test
    public void genuineRisingDoubleBeamRemains() {
        assertTrue(tracked(page(.1f, 190, true, false), .1f, true));
    }

    @Test
    public void genuineFallingDoubleBeamRemains() {
        assertTrue(tracked(page(-.1f, 190, true, false), -.1f, true));
    }

    @Test
    public void longDarkerDoubleBeamRemains() {
        assertTrue(tracked(page(0, 190, true, true), 0, true));
    }

    @Test
    public void faintFiniteDoubleBeamRemains() {
        byte[] p = page(0, 190, false, false);
        body(p, 0, 105, 112, 35, 3);
        assertTrue(tracked(p, 0, true));
    }

    @Test
    public void unverifiedFrameKeepsLegacyProof() {
        assertTrue(tracked(page(0, 190, false, false), 0, false));
    }

    @Test
    public void missingPrintedRailsCannotDemote() {
        byte[] p = page(0, 190, false, false);
        for (int x = 20; x < W - 20; x++)
            for (int y = TOP; y <= TOP + 4 * G + 2; y++)
                if (Math.abs(y - 112) > 4) p[y * W + x] = (byte) 190;
        body(p, 0, 50, 126, 70, 3);
        assertTrue(tracked(p, 0, true));
    }

    @Test
    public void compactGraceLegacyEntryPointIsUnchanged() {
        byte[] p = page(0, 190, true, false);
        assertTrue(legacy(p));
    }

    @Test
    public void sourceAndTrackRemainUnchanged() {
        byte[] p = page(.1f, 190, false, false), before = p.clone();
        StaffPitchTrack t = track(.1f, true);
        float[] at = t.at(100);
        ParallelBeamTip.matches(p, W, H, X, Y, G, t);
        assertArrayEquals(before, p);
        assertArrayEquals(at, t.at(100), 0);
    }
}
