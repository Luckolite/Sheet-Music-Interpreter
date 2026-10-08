// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic ink and independently specified five-rule frames; no score pixels. */
public class HeadBoundTieContourTest {
    static final int W = 180, H = 180, AX = 45, BX = 90, LEFT = 54, RIGHT = 80, STEP = 5;
    static final float GAP = 16;

    static StaffPitchTrack track(boolean curved) {
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(
                        new float[] {0, 130, GAP},
                        new float[] {67, curved ? 134 : 130, GAP},
                        new float[] {179, 130, GAP}));
    }

    static float base(StaffPitchTrack track, float x) {
        float[] f = track.at(x);
        return f[0] - STEP * f[1] * .5f;
    }

    record Raster(byte[] labels, byte[] gray, StaffPitchTrack track, float ay, float by) {}

    static Raster draw(String mode, int side, boolean curved) {
        StaffPitchTrack track = track(curved);
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        float ay = base(track, AX), by = base(track, BX);
        for (int x = 0; x < W; x++)
            for (int y = 0; y < H; y++) {
                double paper = 145 + x * .08, ink = 0;
                float[] frame = track.at(x);
                for (int rule = 0; rule < 5; rule++) {
                    double d = y - (frame[0] - rule * GAP);
                    ink += 65 * Math.exp(-d * d / 1.2);
                }
                double t = (x - AX) / (double) (BX - AX);
                if (mode.equals("longSlur")) t = (x - (AX - 45)) / (double) (BX - AX + 90);
                if (mode.equals("outerShoulders")
                        || mode.equals("outerEllipse")
                        || mode.equals("outerRing")) t = (x - (AX - 6)) / (double) (BX - AX + 12);
                if (mode.equals("nearbySlur")) t = (x - (AX - 18)) / (double) (BX - AX + 36);
                if (mode.equals("leftSlur")) t = (x - (AX - 18)) / (double) (BX - AX + 24);
                if (mode.equals("rightSlur")) t = (x - (AX - 6)) / (double) (BX - AX + 24);
                if (t >= 0 && t <= 1 && !mode.equals("staff") && !(mode.equals("half") && t > .5)) {
                    double bow = .75 * GAP * 4 * t * (1 - t);
                    if (mode.equals("straight") || mode.equals("variableWidth")) bow = 0;
                    if (mode.equals("slope")) bow = GAP * t;
                    if (mode.equals("step")) bow = t < .5 ? 0 : GAP * .45;
                    if (mode.equals("hairpin")) bow = .75 * GAP * (1 - Math.abs(2 * t - 1));
                    if (mode.equals("ellipse") || mode.equals("outerEllipse"))
                        bow = .75 * GAP * Math.sqrt(Math.max(0, 1 - (2 * t - 1) * (2 * t - 1)));
                    if (mode.equals("asymmetric"))
                        bow = .75 * GAP * 4 * t * (1 - t) + GAP * .1 * (t - .5);
                    if (mode.equals("offcenterHairpin"))
                        bow = .75 * GAP * (t < .35 ? t / .35 : (1 - t) / .65);
                    if (mode.equals("plateau"))
                        bow = .75 * GAP * Math.min(1, Math.min(t / .3, (1 - t) / .3));
                    if (mode.equals("double"))
                        bow = .75 * GAP * Math.abs(Math.sin(2 * Math.PI * t));
                    double center = base(track, x) + side * (.3 * GAP + bow);
                    if (mode.equals("longSlur"))
                        center = base(track, x) - 1.8 * GAP - 1.5 * GAP * 4 * t * (1 - t);
                    double sigma = mode.equals("variableWidth") ? 1 + 1.5 * 4 * t * (1 - t) : .9;
                    double d = y - center, amplitude = mode.equals("paper") ? 8 : 75;
                    ink += amplitude * Math.exp(-d * d / (2 * sigma * sigma));
                    if (mode.equals("ring") || mode.equals("outerRing")) {
                        double other = base(track, x) + side * (.3 * GAP - bow),
                                distance = y - other;
                        ink += amplitude * Math.exp(-distance * distance / (2 * sigma * sigma));
                    }
                    if (mode.equals("text")
                            && (Math.abs(x - LEFT - 5) < 2 || Math.abs(x - RIGHT + 5) < 2)
                            && side * (y - center) >= 0
                            && side * (y - center) < GAP) ink = 100;
                    if (mode.equals("headMask") && Math.abs(d) < 3) labels[y * W + x] = 2;
                }
                for (float hx : new float[] {AX, BX}) {
                    double dx = (x - hx) / 7, dy = (y - base(track, hx)) / 5;
                    if (dx * dx + dy * dy < 1) {
                        ink = 100;
                        labels[y * W + x] = 2;
                    }
                }
                gray[y * W + x] = (byte) Math.round(Math.max(0, Math.min(255, paper - ink)));
            }
        return new Raster(labels, gray, track, ay, by);
    }

    static boolean proof(String mode) {
        Raster r = draw(mode, -1, true);
        return VerifiedStaffTieRidge.provedAtHeadBounds(
                r.labels, r.gray, W, H, AX - 7, BX + 7, AX, r.ay, BX, r.by, STEP, r.track);
    }

    @Test
    public void printedShouldersOverTheHeadBodyCanReturn() {
        assertTrue(proof("outerShoulders"));
    }

    @Test
    public void aSlurWhoseEndpointsAreBeyondBothHeadsCannotSupplyATie() {
        assertFalse(proof("nearbySlur"));
    }

    @Test
    public void aSlurContinuingBeyondOnlyTheFirstHeadCannotSupplyATie() {
        assertFalse(proof("leftSlur"));
    }

    @Test
    public void aSlurContinuingBeyondOnlyTheLastHeadCannotSupplyATie() {
        assertFalse(proof("rightSlur"));
    }

    @Test
    public void closedOuterBodyCannotSupplyATie() {
        assertFalse(proof("outerRing"));
    }

    @Test
    public void smoothEllipticalOuterShouldersCanReturn() {
        assertTrue(proof("outerEllipse"));
    }

    @Test
    public void endpointInspectionPreservesAllPixels() {
        Raster r = draw("outerShoulders", -1, true);
        byte[] l = r.labels.clone(), g = r.gray.clone();
        VerifiedStaffTieRidge.provedAtHeadBounds(
                r.labels, r.gray, W, H, AX - 7, BX + 7, AX, r.ay, BX, r.by, STEP, r.track);
        assertArrayEquals(l, r.labels);
        assertArrayEquals(g, r.gray);
    }

    @Test
    public void boxesMustContainBothPrintedHeadCenters() {
        Raster r = draw("outerShoulders", -1, true);
        assertFalse(
                VerifiedStaffTieRidge.provedAtHeadBounds(
                        r.labels, r.gray, W, H, AX + 2, BX + 7, AX, r.ay, BX, r.by, STEP, r.track));
    }
}
