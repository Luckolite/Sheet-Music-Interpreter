// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic outline family at independently specified staff phases. */
public class ClosedCounterStaffOwnershipTest {
    static final int W = 180, H = 180, AX = 45;
    static final float GAP = 16;

    static boolean proof(int step, int bx, double offset, double bow, boolean closed) {
        StaffPitchTrack track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {0, 130, GAP},
                                new float[] {67, 134, GAP},
                                new float[] {179, 130, GAP}));
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) {
                double paper = 145 + x * .08, ink = 0;
                float[] f = track.at(x);
                double base = f[0] - step * GAP * .5;
                for (int rule = 0; rule < 5; rule++) {
                    double d = y - (f[0] - rule * GAP);
                    ink += 65 * Math.exp(-d * d / 1.2);
                }
                double t = (x - (AX - 6)) / (double) (bx - AX + 12);
                if (t >= 0 && t <= 1) {
                    double rise = bow * GAP * 4 * t * (1 - t), d = y - (base - offset * GAP - rise);
                    ink += 75 * Math.exp(-d * d / (2 * .9 * .9));
                    if (closed) {
                        double other = y - (base - offset * GAP + rise);
                        ink += 75 * Math.exp(-other * other / (2 * .9 * .9));
                    }
                }
                for (int hx : new int[] {AX, bx}) {
                    float[] h = track.at(hx);
                    double dx = (x - hx) / 7., dy = (y - (h[0] - step * GAP * .5)) / 5.;
                    if (dx * dx + dy * dy < 1) {
                        ink = 100;
                        labels[y * W + x] = 2;
                    }
                }
                gray[y * W + x] = (byte) Math.round(Math.max(0, Math.min(255, paper - ink)));
            }
        float[] a = track.at(AX), b = track.at(bx);
        return VerifiedStaffTieRidge.provedAtHeadBounds(
                labels,
                gray,
                W,
                H,
                AX - 7,
                bx + 7,
                AX,
                a[0] - step * GAP * .5f,
                bx,
                b[0] - step * GAP * .5f,
                step,
                track);
    }

    @Test
    public void firstSpaceBroadCounterCannotSupplyATie() {
        assertFalse(proof(5, 90, .2, 1, true));
    }

    @Test
    public void firstSpaceShallowCounterCannotSupplyATie() {
        assertFalse(proof(5, 100, .8, .25, true));
    }

    @Test
    public void upperSpaceBroadCounterCannotSupplyATie() {
        assertFalse(proof(7, 90, .2, 1, true));
    }

    @Test
    public void upperSpaceTallCounterCannotSupplyATie() {
        assertFalse(proof(7, 90, .2, 1.25, true));
    }

    @Test
    public void upperSpaceShallowCounterCannotSupplyATie() {
        assertFalse(proof(7, 100, .8, .25, true));
    }

    @Test
    public void upperSpaceOffsetCounterCannotSupplyATie() {
        assertFalse(proof(7, 100, 1.1, 1, true));
    }

    @Test
    public void theCorrespondingOpenContoursRetainTheirOwnReturningShoulders() {
        for (double[] v :
                new double[][] {
                    {5, 90, .2, 1},
                    {5, 100, .8, .25},
                    {7, 90, .2, 1},
                    {7, 90, .2, 1.25},
                    {7, 100, .8, .25},
                    {7, 100, 1.1, 1}
                }) assertTrue(Arrays.toString(v), proof((int) v[0], (int) v[1], v[2], v[3], false));
    }
}
