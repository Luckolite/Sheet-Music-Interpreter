// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original shifted search cores against a separately established printed-rule frame. */
public final class CanonicalPrintedRailPromotionTest {
    static final int W = 420, H = 220, X = 210, G = 12, TOP = 80;

    byte[] page(float slope, int paper, boolean twoBeams) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        for (int x = 20; x < W - 20; x++) {
            int d = Math.round(slope * (x - X));
            for (int l = 0; l < 5; l++)
                for (int dy = 0; dy <= 1; dy++)
                    p[(TOP + l * G + d + dy) * W + x] = (byte) (paper - 80);
            for (int dy = -2; dy <= 2; dy++)
                p[(117 + d + dy) * W + x] = (byte) (paper - new int[] {40, 60, 80, 60, 40}[dy + 2]);
            if (Math.abs(x - X) <= 50)
                for (int dy = -3; dy <= 3; dy++) p[(125 + d + dy) * W + x] = (byte) (paper - 125);
            if (twoBeams && Math.abs(x - X) <= 50)
                for (int dy = -3; dy <= 3; dy++) p[(115 + d + dy) * W + x] = (byte) (paper - 125);
        }
        return p;
    }

    StaffPitchTrack track(float slope, boolean verified) {
        return verified
                ? StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {20, TOP + .5f + 4 * G + slope * (20 - X), G},
                                new float[] {W - 20, TOP + .5f + 4 * G + slope * (W - 20 - X), G}))
                : StaffPitchTrack.linear(W, TOP + .5f + 4 * G + slope * (W * .5f - X), G, slope);
    }

    @Test
    public void independentlyProvedRailUsesPrintedPhase() throws Exception {
        byte[] p = page(0, 160, false);
        StaffPitchTrack t = track(0, true);
        assertFalse(
                "displaced search stripe cannot establish its own phase",
                PrintedRuleInkOwnership.matchesWithNarrowReference(
                        p, W, H, X, 114, 116, TOP + .5f, G, 0, t));
        assertTrue(
                "physical raw rail has independent support",
                PrintedRuleInkOwnership.matchesWithNarrowReference(
                        p, W, H, X, 116, 118, TOP + .5f, G, 0, t));
        var m =
                ParallelBeamTip.class.getDeclaredMethod(
                        "printedCore",
                        byte[].class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class,
                        int.class,
                        int.class,
                        float.class,
                        int.class,
                        StaffPitchTrack.class);
        m.setAccessible(true);
        assertTrue(
                "guard must recognize the independently supported rail across the searched core",
                (boolean) m.invoke(null, p, W, H, (float) X, 120f, (float) G, 1, 0, 0f, -1, t));
        assertFalse(ParallelBeamTip.matches(p, W, H, X, 120, G, t));
    }

    @Test
    public void genuineDarkerSecondCoreRemains() {
        assertTrue(ParallelBeamTip.matches(page(0, 160, true), W, H, X, 120, G, track(0, true)));
    }

    @Test
    public void oppositeSlopeSecondCoreRemains() {
        for (float s : new float[] {-.1f, .1f})
            assertTrue(
                    ParallelBeamTip.matches(page(s, 160, true), W, H, X, 120, G, track(s, true)));
    }

    @Test
    public void numericalFrameCannotEraseRawBeam() {
        assertTrue(ParallelBeamTip.matches(page(0, 160, false), W, H, X, 120, G, track(0, false)));
    }

    @Test
    public void lightPaperHasNoCompleteLegacyPair() {
        assertFalse(ParallelBeamTip.matches(page(0, 235, false), W, H, X, 120, G, track(0, true)));
    }

    @Test
    public void callerPixelsAndTrackArePreserved() {
        byte[] p = page(0, 160, false), before = p.clone();
        StaffPitchTrack t = track(0, true);
        float[] f = t.at(230);
        ParallelBeamTip.matches(p, W, H, X, 120, G, t);
        assertArrayEquals(before, p);
        assertArrayEquals(f, t.at(230), 0);
    }
}
