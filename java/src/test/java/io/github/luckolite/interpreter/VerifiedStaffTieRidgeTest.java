// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original analytic ink and independently specified five-rule frames; no score pixels. */
public class VerifiedStaffTieRidgeTest {
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
                if (t >= 0 && t <= 1 && !mode.equals("staff") && !(mode.equals("half") && t > .5)) {
                    double bow = .75 * GAP * 4 * t * (1 - t);
                    if (mode.equals("straight") || mode.equals("variableWidth")) bow = 0;
                    if (mode.equals("slope")) bow = GAP * t;
                    if (mode.equals("step")) bow = t < .5 ? 0 : GAP * .45;
                    if (mode.equals("hairpin")) bow = .75 * GAP * (1 - Math.abs(2 * t - 1));
                    double center = base(track, x) + side * (.3 * GAP + bow);
                    if (mode.equals("longSlur"))
                        center = base(track, x) - 1.8 * GAP - 1.5 * GAP * 4 * t * (1 - t);
                    double sigma = mode.equals("variableWidth") ? 1 + 1.5 * 4 * t * (1 - t) : .9;
                    double d = y - center, amplitude = mode.equals("paper") ? 8 : 75;
                    ink += amplitude * Math.exp(-d * d / (2 * sigma * sigma));
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

    static boolean proof(String mode, int side, boolean curved) {
        Raster r = draw(mode, side, curved);
        return VerifiedStaffTieRidge.proved(
                r.labels, r.gray, W, H, LEFT, RIGHT, AX, r.ay, BX, r.by, STEP, r.track);
    }

    @Test
    public void compactAboveRetainsReturningShoulders() {
        assertTrue(proof("bow", -1, false));
    }

    @Test
    public void compactBelowRetainsReturningShoulders() {
        assertTrue(proof("bow", 1, false));
    }

    @Test
    public void curvedStaffAboveDoesNotFlattenTiePixels() {
        assertTrue(proof("bow", -1, true));
    }

    @Test
    public void curvedStaffBelowDoesNotFlattenTiePixels() {
        assertTrue(proof("bow", 1, true));
    }

    @Test
    public void curvedStaffRuleAloneCannotSupplyTheArc() {
        assertFalse(proof("staff", 1, true));
    }

    @Test
    public void straightBeamCannotSupplyTheArc() {
        assertFalse(proof("straight", 1, true));
    }

    @Test
    public void slopedBeamCannotSupplyTheArc() {
        assertFalse(proof("slope", 1, true));
    }

    @Test
    public void beamStepCannotSupplyTheArc() {
        assertFalse(proof("step", 1, true));
    }

    @Test
    public void variableBeamWidthCannotSupplyTheArc() {
        assertFalse(proof("variableWidth", 1, true));
    }

    @Test
    public void palePaperCannotSupplyTheArc() {
        assertFalse(proof("paper", 1, true));
    }

    @Test
    public void incompleteShoulderCannotSupplyTheArc() {
        assertFalse(proof("half", 1, true));
    }

    @Test
    public void noteheadMaskCannotSupplyTheArc() {
        assertFalse(proof("headMask", 1, true));
    }

    @Test
    public void letterBranchesCannotSupplyTheArc() {
        assertFalse(proof("text", 1, true));
    }

    @Test
    public void hairpinCannotSupplyTheArc() {
        assertFalse(proof("hairpin", 1, true));
    }

    @Test
    public void longSlurEndpointsOutsideHeadsCannotSupplyTheArc() {
        assertFalse(proof("longSlur", -1, true));
    }

    @Test
    public void unverifiedLinearEstimateCannotSupplyTheFrame() {
        Raster r = draw("bow", -1, false);
        assertFalse(
                VerifiedStaffTieRidge.proved(
                        r.labels,
                        r.gray,
                        W,
                        H,
                        LEFT,
                        RIGHT,
                        AX,
                        r.ay,
                        BX,
                        r.by,
                        STEP,
                        StaffPitchTrack.linear(W, 130, GAP, 0)));
    }

    @Test
    public void differentPrintedPitchCannotSupplyTheFrame() {
        Raster r = draw("bow", -1, false);
        assertFalse(
                VerifiedStaffTieRidge.proved(
                        r.labels,
                        r.gray,
                        W,
                        H,
                        LEFT,
                        RIGHT,
                        AX,
                        r.ay,
                        BX,
                        r.by + GAP * .5f,
                        STEP,
                        r.track));
    }

    @Test
    public void missingFrameCannotSupplyTheArc() {
        Raster r = draw("bow", -1, false);
        assertFalse(
                VerifiedStaffTieRidge.proved(
                        r.labels, r.gray, W, H, LEFT, RIGHT, AX, r.ay, BX, r.by, STEP, null));
    }

    static float[] ruleCenters(StaffPitchTrack track, int rule) {
        float[] centers = new float[50];
        for (int i = 0; i < 50; i++) {
            float[] frame = track.at(Math.round(LEFT + (i + .5f) * (RIGHT - LEFT) / 50));
            centers[i] = frame[0] - rule * frame[1];
        }
        return centers;
    }

    @Test
    public void completeCurvedRuleIsOwnedByItsStaff() {
        var t = track(true);
        assertTrue(VerifiedStaffTieRidge.followsRule(t, LEFT, RIGHT, ruleCenters(t, 3)));
    }

    @Test
    public void aRuleWithOneInterruptedSampleRetainsOwnership() {
        var t = track(true);
        float[] c = ruleCenters(t, 3);
        c[12] += 5;
        assertTrue(VerifiedStaffTieRidge.followsRule(t, LEFT, RIGHT, c));
    }

    @Test
    public void distinctReturningContourIsNotOwnedByTheRule() {
        var t = track(true);
        float[] c = ruleCenters(t, 3);
        for (int i = 0; i < c.length; i++) {
            float x = (i + .5f) / 50;
            c[i] -= 8 * 4 * x * (1 - x);
        }
        assertFalse(VerifiedStaffTieRidge.followsRule(t, LEFT, RIGHT, c));
    }

    @Test
    public void staffCrossingShouldersAreNotWhollyRuleInk() {
        var t = track(true);
        float[] c = ruleCenters(t, 3);
        for (int i = 0; i < 8; i++) c[i] -= 5;
        assertFalse(VerifiedStaffTieRidge.followsRule(t, LEFT, RIGHT, c));
    }

    @Test
    public void separateRulesCannotBeJoinedAsOneOwnedContour() {
        var t = track(true);
        float[] c = ruleCenters(t, 3);
        for (int i = 25; i < 50; i++) c[i] += GAP;
        assertFalse(VerifiedStaffTieRidge.followsRule(t, LEFT, RIGHT, c));
    }

    @Test
    public void estimatedRuleCannotVetoAnIndependentContour() {
        var t = StaffPitchTrack.linear(W, 130, GAP, 0);
        assertFalse(VerifiedStaffTieRidge.followsRule(t, LEFT, RIGHT, ruleCenters(t, 3)));
    }

    @Test
    public void tooFewVisibleSamplesCannotEstablishRuleOwnership() {
        var t = track(true);
        float[] c = ruleCenters(t, 3);
        Arrays.fill(c, 0, 17, Float.NaN);
        assertFalse(VerifiedStaffTieRidge.followsRule(t, LEFT, RIGHT, c));
    }
}
