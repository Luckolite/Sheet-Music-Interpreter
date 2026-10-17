// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class SharedNumeralSecondaryOwnershipTest {
    static final int W = 400, H = 300;
    static final String[] THREE = {
        "..#######...", ".##########.", "###......###", "####.....###",
        "####.....###", "####.....###", ".##.....####", ".......####.",
        "......####..", "....#####...", "....#####...", "....#####...",
        "......####..", ".......####.", "##.....####.", "###....####.",
        "###....####.", "###....####.", ".###....###.", "..########..",
        "..########..", "....####...."
    };

    static byte[] ink(int top, int left, boolean staff) {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        if (staff)
            for (int y : new int[] {110, 124, 138, 152, 166})
                for (int x = 20; x < 380; x++) g[y * W + x] = 0;
        for (int x = 69; x <= 194; x++) for (int y = 112; y <= 115; y++) g[y * W + x] = 0;
        for (int x = 119; x <= 169; x++) for (int y = 104; y <= 107; y++) g[y * W + x] = 0;
        for (int x : new int[] {69, 94, 119, 144, 169, 194})
            for (int y = 72; y <= 115; y++) g[y * W + x] = 0;
        for (int y = 0; y < THREE.length; y++)
            for (int x = 0; x < 12; x++)
                if (THREE[y].charAt(x) == '#') g[(top + y) * W + left + x] = 0;
        return g;
    }

    static List<ScoreNoteEvent> notes() {
        var n = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 6; i++)
            n.add(new ScoreNoteEvent(0, (75 + i * 25f) / W, 11, 0, 1, 79f / H, false, 0, 2, 2, 0));
        return n;
    }

    static List<ScoreNoteEvent> apply(byte[] gray) {
        return TripletRhythmDetector.apply(
                notes(), List.of(new MeasureRegion(0, 1, 60f / H, 172f / H)), gray, W, H);
    }

    static void expect(List<ScoreNoteEvent> result, int... divisors) {
        for (int i = 0; i < divisors.length; i++)
            org.junit.Assert.assertEquals(
                    "attack " + i, divisors[i], result.get(i).tupletDivisor());
    }

    @org.junit.Test
    public void boundedSecondaryOwnsSharedNumeral() {
        expect(apply(ink(140, 135, false)), 1, 1, 3, 3, 3, 1);
    }

    @org.junit.Test
    public void nearbySharedNumeralStillUsesBeamOwnership() {
        expect(apply(ink(150, 140, false)), 1, 1, 3, 3, 3, 1);
    }

    @org.junit.Test
    public void noNumeralCannotInventTriplet() {
        byte[] g = ink(140, 135, false);
        for (int y = 140; y < 162; y++) for (int x = 135; x < 147; x++) g[y * W + x] = (byte) 255;
        expect(apply(g), 1, 1, 1, 1, 1, 1);
    }

    @org.junit.Test
    public void missingSecondaryKeepsOrdinaryNumeralOwnership() {
        byte[] g = ink(140, 135, false);
        for (int y = 104; y <= 107; y++) for (int x = 119; x <= 169; x++) g[y * W + x] = (byte) 255;
        expect(apply(g), 1, 3, 3, 3, 1, 1);
    }

    @org.junit.Test
    public void secondaryExtendingToNextAttackCannotClaimThree() {
        byte[] g = ink(140, 135, false);
        for (int y = 104; y <= 107; y++) for (int x = 170; x <= 194; x++) g[y * W + x] = 0;
        expect(apply(g), 1, 3, 3, 3, 1, 1);
    }

    @org.junit.Test
    public void pixelsArePreserved() {
        byte[] g = ink(140, 135, false), before = g.clone();
        apply(g);
        org.junit.Assert.assertArrayEquals(before, g);
    }
}
