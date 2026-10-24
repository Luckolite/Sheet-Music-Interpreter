// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Method;
import java.util.Arrays;

/** Regression controls for complete rail bounds and 8-connected centerline continuity. */
public final class RailBoundsAnd8ConnectivityOriginal {
    private static final int W = 128, H = 112;
    private static final byte HEAD = 2;

    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }

    private static byte[] staffGray(boolean clipped) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 248);
        for (int i = 0; i < 5; i++) {
            int y = 20 + 10 * i;
            if (clipped && i == 0) y = 17;
            int start = clipped && i == 0 ? 17 : y, end = clipped && i == 0 ? 21 : y + 1;
            for (int yy = start; yy <= end; yy++) for (int x = 8; x < 120; x++) g[yy * W + x] = 30;
            if (clipped && i == 0) for (int x = 8; x < 120; x++) g[16 * W + x] = 30;
        }
        return g;
    }

    private static Object frame(byte[] g, int x) throws Exception {
        Method m =
                NaturalHarmonicRingProof.class.getDeclaredMethod(
                        "literalFiveRailFrame",
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        NaturalHarmonicRingProof.Budget.class);
        m.setAccessible(true);
        return m.invoke(
                null, g, W, H, x, 20f, 10f, new NaturalHarmonicRingProof.Budget(100000, 100000));
    }

    private static NaturalHarmonicRingProof.HeadAssessment assess(
            boolean alternatingTwoPixelJumps) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        Arrays.fill(gray, (byte) 248);
        for (int y = 52; y <= 62; y++)
            for (int x = 54; x <= 66; x++) {
                double q =
                        ((x - 60) / 6.0) * ((x - 60) / 6.0) + ((y - 57) / 5.0) * ((y - 57) / 5.0);
                if (q <= 1) {
                    labels[y * W + x] = HEAD;
                    gray[y * W + x] = 20;
                }
            }
        for (int y = 22; y <= 59; y++) {
            int x = alternatingTwoPixelJumps ? ((y & 1) == 0 ? 59 : 61) : 59;
            gray[y * W + x] = 24;
        }
        var n =
                new NaturalHarmonicRingProof.NoteIdentity(
                        "8-connected", 0, 0, 1, 2, 60, 57, 17, 57, .5f, .5f);
        return NaturalHarmonicRingProof.assessRawHeadAndStem(
                gray,
                labels,
                W,
                H,
                HEAD,
                n,
                new NaturalHarmonicRingProof.Budget(2_000_000, 8_000_000));
    }

    public static void main(String[] args) throws Exception {
        check(frame(staffGray(false), 60) != null, "complete five-rail frame should pass");
        check(
                frame(staffGray(true), 60) == null,
                "continued ink beyond clipped search bound must refuse");
        var intact = assess(false);
        check(
                intact.decision() == NaturalHarmonicRingProof.HeadDecision.OWNED
                        && intact.head() != null
                        && intact.head().attachedStem(),
                "continuous 8-connected shaft remains supported");
        var jump = assess(true);
        check(
                jump.decision() == NaturalHarmonicRingProof.HeadDecision.ATTACHED_STEM_NOT_PROVEN
                        && jump.head() == null,
                "alternating 2px disconnected shaft jumps must refuse");
        System.out.println("complete rail bounds and 8-connected shaft controls PASS");
    }
}
