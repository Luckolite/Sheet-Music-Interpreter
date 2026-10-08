// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original main tie plus an independent longer slur, including actual head-mask intersections. */
public class ConnectedCounterAmbiguityTest {
    static final int W = 180, H = 180, AX = 45, BX = 100;
    static final float GAP = 16;

    record Raster(byte[] labels, byte[] gray, StaffPitchTrack track) {}

    static Raster draw(String mode, int step, double offset, double rise) {
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {0, 130, GAP},
                                new float[] {67, 134, GAP},
                                new float[] {179, 130, GAP}));
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        int left = AX - 6, right = BX + 6;
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) {
                double paper = 145 + x * .08, ink = 0;
                float[] f = track.at(x);
                double base = f[0] - step * GAP * .5;
                for (int rule = 0; rule < 5; rule++) {
                    double d = y - (f[0] - rule * GAP);
                    ink += 65 * Math.exp(-d * d / 1.2);
                }
                double t = (x - left) / (double) (right - left);
                if (t >= 0 && t <= 1) {
                    double bow = rise * GAP * 4 * t * (1 - t), d = y - (base - offset * GAP - bow);
                    ink += 75 * Math.exp(-d * d / (2 * .9 * .9));
                    if (mode.startsWith("ring")) {
                        double other = y - (base - offset * GAP + bow);
                        ink += 75 * Math.exp(-other * other / (2 * .9 * .9));
                    }
                }
                if (mode.startsWith("opposite")) {
                    double q = (x - (left - 26)) / (double) (right - left + 52);
                    if (q >= 0 && q <= 1) {
                        double d =
                                y
                                        - (base
                                                + (mode.equals("oppositeTouchingSlur") ? -.1 : .4)
                                                        * GAP
                                                + (mode.equals("oppositeTouchingSlur") ? .45 : rise)
                                                        * GAP
                                                        * 4
                                                        * q
                                                        * (1 - q));
                        ink += 75 * Math.exp(-d * d / (2 * .9 * .9));
                    }
                }
                if (mode.equals("ringRightTail") && x >= right - 2 && x <= right + 16) {
                    double d =
                            y
                                    - (base
                                            - offset * GAP
                                            - 4 * rise * GAP * (x - right) / (right - left));
                    ink += 75 * Math.exp(-d * d / (2 * .9 * .9));
                }
                if (mode.equals("ringLeftTail") && x >= left - 16 && x <= left + 2) {
                    double d =
                            y
                                    - (base
                                            - offset * GAP
                                            + 4 * rise * GAP * (x - left) / (right - left));
                    ink += 75 * Math.exp(-d * d / (2 * .9 * .9));
                }
                for (int hx : new int[] {AX, BX}) {
                    float[] h = track.at(hx);
                    double dx = (x - hx) / 7., dy = (y - (h[0] - step * GAP * .5)) / 5.;
                    if (dx * dx + dy * dy < 1) {
                        ink = 100;
                        labels[y * W + x] = 2;
                    }
                }
                gray[y * W + x] = (byte) Math.round(Math.max(0, Math.min(255, paper - ink)));
            }
        return new Raster(labels, gray, track);
    }

    static boolean proof(String mode, int step, double offset, double rise) {
        var r = draw(mode, step, offset, rise);
        float[] a = r.track.at(AX), b = r.track.at(BX);
        return VerifiedStaffTieRidge.provedAtHeadBounds(
                r.labels,
                r.gray,
                W,
                H,
                AX - 7,
                BX + 7,
                AX,
                a[0] - step * GAP * .5f,
                BX,
                b[0] - step * GAP * .5f,
                step,
                r.track);
    }

    @Test
    public void theCompleteMainTieRemainsProved() {
        assertTrue(proof("main", 5, .3, .75));
    }

    @Test
    public void anIndependentOppositeSlurBeyondTheHeadsRemainsSeparate() {
        assertTrue(proof("oppositeSlur", 5, .3, .75));
    }

    @Test
    public void anOppositeSlurPassingThroughBothActualHeadsPreservesTheTieClearance() {
        var r = draw("oppositeTouchingSlur", 5, .3, .75);
        for (int hx : new int[] {AX, BX}) {
            double q = (hx - (AX - 6 - 26)) / (double) (BX - AX + 12 + 52);
            float[] f = r.track.at(hx);
            int yy =
                    Math.round((float) (f[0] - 5 * GAP * .5 + (-.1 + .45 * 4 * q * (1 - q)) * GAP));
            assertEquals(OmrMeasurePostProcessor.NOTEHEAD, r.labels[yy * W + hx]);
        }
        assertTrue(proof("oppositeTouchingSlur", 5, .3, .75));
    }
}
