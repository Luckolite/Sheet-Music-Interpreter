// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original unevenly inked ribbon crossing a continuous narrow printed rule. */
public class PairedBeamCoreOwnershipTest {
    private static final int W = 400, H = 220, G = 14, A = 180, B = 236;

    private byte[] page(int top) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 250);
        for (int x = 10; x < W - 10; x++)
            for (int y = top; y <= top + 4 * G; y += G)
                for (int dy = 0; dy < 2; dy++) p[(y + dy) * W + x] = 20;
        for (int x = A; x <= B; x++)
            for (int n = 0; n < 3; n++)
                for (int dy = 0; dy < 8; dy++) {
                    boolean dense = n != 1;
                    for (int center : new int[] {A + 2, A + 14, A + 28, A + 42, B - 2})
                        dense |= Math.abs(x - center) <= 1;
                    int i = (90 + n * 9 + dy) * W + x;
                    p[i] = (byte) Math.min(p[i] & 255, dense ? 20 : 190);
                }
        for (int y = 40; y <= 115; y++) {
            p[y * W + A] = 20;
            p[y * W + B] = 20;
        }
        return p;
    }

    private int count(byte[] p, int top, boolean reverse) {
        StaffPitchTrack track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {20, top + 4 * G, G},
                                new float[] {380, top + 4 * G, G}));
        return PairedGraceBeamInk.countPrintedSize(
                p,
                W,
                H,
                new int[] {reverse ? B : A, 115, 1},
                new int[] {reverse ? A : B, 115, 1},
                G,
                top,
                track);
    }

    @Test
    public void narrowReferenceCannotOwnWiderBeamCore() {
        assertEquals(3, count(page(46), 46, false));
    }

    @Test
    public void neighboringPrintedPhasesPreserveTheBeam() {
        assertEquals(3, count(page(45), 45, false));
        assertEquals(3, count(page(47), 47, false));
    }

    @Test
    public void oppositeStemOrderPreservesTheBeam() {
        assertEquals(3, count(page(46), 46, true));
    }

    @Test
    public void sourceInkIsUnchanged() {
        byte[] p = page(46), copy = p.clone();
        assertEquals(3, count(p, 46, false));
        assertArrayEquals(copy, p);
    }
}
