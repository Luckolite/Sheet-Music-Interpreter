// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original five ruled lines, locally blurred rail and separately printed finite strokes. */
public final class PrintedRuleInkOwnershipTest {
    static final int W = 480, H = 240, X = 220, G = 16, TOP = 80;

    byte[] page(float slope, int paper, int rules) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) paper);
        for (int line = 0; line < rules; line++)
            for (int x = 20; x < W - 20; x++)
                for (int dy = 0; dy <= 1; dy++)
                    p[(TOP + line * G + Math.round((x - X) * slope) + dy) * W + x] = 90;
        for (int x = X - 20; x <= X + 20; x++)
            for (int dy = -2; dy <= 2; dy++)
                p[(TOP + 2 * G + Math.round((x - X) * slope) + dy) * W + x] =
                        (byte) (90 + Math.abs(dy) * 10);
        return p;
    }

    StaffPitchTrack track(float slope, boolean verified) {
        return verified
                ? StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {20, TOP + 4 * G + slope * (20 - X), G},
                                new float[] {W - 20, TOP + 4 * G + slope * (W - 20 - X), G}))
                : StaffPitchTrack.linear(W, TOP + 4 * G + slope * (W * .5f - X), G, slope);
    }

    boolean proof(byte[] p, float slope, boolean verified) {
        return PrintedRuleInkOwnership.matches(
                p,
                W,
                H,
                X,
                TOP + 2 * G - 2,
                TOP + 2 * G + 2,
                TOP,
                G,
                slope,
                track(slope, verified));
    }

    void stroke(byte[] p, int shade, float slope, int halfWidth, int thickness) {
        for (int dx = -halfWidth; dx <= halfWidth; dx++)
            for (int dy = -thickness; dy <= thickness; dy++)
                p[(TOP + 2 * G + Math.round(dx * slope) + dy) * W + X + dx] = (byte) shade;
    }

    @Test
    public void localBlurRetainsContinuousRailContrast() {
        assertTrue(proof(page(0, 150, 5), 0, true));
    }

    @Test
    public void risingRawRuleUsesVerifiedFrame() {
        assertTrue(proof(page(.12f, 150, 5), .12f, true));
    }

    @Test
    public void fallingRawRuleUsesVerifiedFrame() {
        assertTrue(proof(page(-.12f, 150, 5), -.12f, true));
    }

    @Test
    public void completeDarkerHorizontalStrokeRemains() {
        byte[] p = page(0, 150, 5);
        stroke(p, 20, 0, 14, 2);
        assertFalse(proof(p, 0, true));
    }

    @Test
    public void genuineRisingSlashRemains() {
        byte[] p = page(0, 150, 5);
        stroke(p, 20, .25f, 14, 2);
        assertFalse(proof(p, 0, true));
    }

    @Test
    public void genuineFallingSlashRemains() {
        byte[] p = page(0, 150, 5);
        stroke(p, 20, -.25f, 14, 2);
        assertFalse(proof(p, 0, true));
    }

    @Test
    public void faintFiniteThickBodyRemains() {
        byte[] p = page(0, 150, 5);
        stroke(p, 75, 0, 20, 3);
        assertFalse(proof(p, 0, true));
    }

    @Test
    public void longFiniteBeamBodyRemains() {
        byte[] p = page(0, 150, 5);
        stroke(p, 30, 0, 70, 3);
        assertFalse(proof(p, 0, true));
    }

    @Test
    public void narrowOwningShaftDoesNotEraseOtherRuleWitnesses() {
        byte[] p = page(0, 150, 5);
        for (int y = TOP + G; y < TOP + 3 * G; y++) p[y * W + X - Math.round(G * .65f)] = 20;
        assertTrue(proof(p, 0, true));
    }

    @Test
    public void broadNeighborBodyCannotSupplyLevelRuleWitness() {
        byte[] p = page(0, 150, 5);
        for (int y = TOP + G; y < TOP + 3 * G; y++)
            for (int x = X + 4; x < X + 19; x++) p[y * W + x] = 20;
        assertFalse(proof(p, 0, true));
    }

    @Test
    public void missingOuterContinuationDoesNotEraseInk() {
        byte[] p = page(0, 150, 5);
        for (int x = X + 32; x < X + 81; x++)
            for (int y = TOP + 2 * G - 3; y <= TOP + 2 * G + 3; y++) p[y * W + x] = (byte) 150;
        assertFalse(proof(p, 0, true));
    }

    @Test
    public void oneRuleCannotInventFrame() {
        assertFalse(proof(page(0, 150, 1), 0, true));
    }

    @Test
    public void oneNeighboringRailCannotEraseInk() {
        byte[] p = page(0, 150, 3);
        for (int x = 20; x < W - 20; x++)
            for (int y = TOP - 2; y <= TOP + 2; y++) p[y * W + x] = (byte) 150;
        assertFalse(proof(p, 0, true));
    }

    @Test
    public void unverifiedFrameKeepsExistingDecision() {
        assertFalse(proof(page(0, 150, 5), 0, false));
    }

    @Test
    public void wrongFramePhaseCannotEraseStroke() {
        assertFalse(
                PrintedRuleInkOwnership.matches(
                        page(0, 150, 5),
                        W,
                        H,
                        X,
                        110,
                        114,
                        88,
                        G,
                        0,
                        StaffPitchTrack.fromVerifiedSamples(
                                List.of(new float[] {20, 152, G}, new float[] {460, 152, G}))));
    }

    @Test
    public void sourcePixelsAndTrackSamplesRemainUnchanged() {
        byte[] p = page(.12f, 150, 5), before = p.clone();
        var t = track(.12f, true);
        float[] at = t.at(100);
        PrintedRuleInkOwnership.matches(p, W, H, X, 110, 114, TOP, G, .12f, t);
        assertArrayEquals(before, p);
        assertArrayEquals(at, t.at(100), 0);
    }

    @Test
    public void malformedImageAndEdgeAreRejected() {
        var t = track(0, true);
        assertFalse(PrintedRuleInkOwnership.matches(new byte[1], W, H, X, 110, 114, TOP, G, 0, t));
        assertFalse(
                PrintedRuleInkOwnership.matches(page(0, 150, 5), W, H, 1, 110, 114, TOP, G, 0, t));
        assertFalse(
                PrintedRuleInkOwnership.matches(
                        page(0, 150, 5), W, H, X, 110, 114, TOP, Float.MAX_VALUE, 0, t));
    }
}
