// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrackedFlatRestFrameTest {
    private static final int W = 440, H = 260;

    private byte[] scene() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int i = 0; i < 5; i++) {
            int row = Math.round(80 + i * 13.75f);
            for (int d = 0; d < 2; d++) for (int x = 12; x < W - 12; x++) g[(row + d) * W + x] = 0;
        }
        for (int y = 97; y <= 135; y++) {
            int x = 210 - (y - 97) * 10 / 38;
            g[y * W + x] = 0;
            g[y * W + x + 1] = 0;
        }
        for (int[] b : new int[][] {{204, 100}, {200, 114}})
            for (int y = b[1] - 4; y <= b[1] + 4; y++)
                for (int x = b[0] - 5; x <= b[0] + 5; x++)
                    if ((x - b[0]) * (x - b[0]) / 25d + (y - b[1]) * (y - b[1]) / 16d <= 1)
                        g[y * W + x] = 0;
        return g;
    }

    private int[] rows() {
        return new int[] {80, 94, 108, 121, 135};
    }

    @Test
    public void allFiveCompleteContrastedRulesProveAPlane() {
        assertTrue(FlatRestRulePlane.proved(scene(), W, H, rows(), 13.75f));
    }

    @Test
    public void aMissingRuleDoesNotFlattenTheTrack() {
        byte[] g = scene();
        for (int y = 107; y <= 110; y++) for (int x = 0; x < W; x++) g[y * W + x] = (byte) 255;
        assertFalse(FlatRestRulePlane.proved(g, W, H, rows(), 13.75f));
    }

    @Test
    public void localGlyphStrokesCannotProveAPlane() {
        byte[] g = scene();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) if (x < 140 || x > 220) g[y * W + x] = (byte) 255;
        assertFalse(FlatRestRulePlane.proved(g, W, H, rows(), 13.75f));
    }

    @Test
    public void aSixthRuleKeepsThePhaseAmbiguous() {
        byte[] g = scene();
        for (int y = 66; y <= 67; y++) for (int x = 12; x < W - 12; x++) g[y * W + x] = 0;
        assertFalse(FlatRestRulePlane.proved(g, W, H, rows(), 13.75f));
    }

    @Test
    public void aFilledBandDoesNotSupplyFiveContrastedRules() {
        byte[] g = scene();
        for (int y = 70; y <= 145; y++) for (int x = 12; x < W - 12; x++) g[y * W + x] = 0;
        assertFalse(FlatRestRulePlane.proved(g, W, H, rows(), 13.75f));
    }

    @Test
    public void genuinelyCurvedRulesDoNotProveTheFlatPlane() {
        byte[] g = scene();
        for (int y = 70; y <= 145; y++) for (int x = 12; x < W - 12; x++) g[y * W + x] = (byte) 255;
        for (int row : rows())
            for (int x = 12; x < W - 12; x++) {
                int y = row + Math.round(5 * (float) Math.sin(x * Math.PI / (W - 1)));
                g[y * W + x] = 0;
                g[(y + 1) * W + x] = 0;
            }
        assertFalse(FlatRestRulePlane.proved(g, W, H, rows(), 13.75f));
    }

    @Test
    public void inconsistentRuleSpacingCannotFlattenTheTrack() {
        int[] r = rows();
        r[2] += 3;
        assertFalse(FlatRestRulePlane.proved(scene(), W, H, r, 13.75f));
    }

    @Test
    public void nonFiniteGeometryCannotFlattenTheTrack() {
        assertFalse(FlatRestRulePlane.proved(scene(), W, H, rows(), Float.NaN));
    }

    private void shortRest(float bottom, float slope) {
        byte[] g = scene(), before = g.clone();
        var track = StaffPitchTrack.linear(W, bottom, 13.75f, slope);
        var rest =
                SixteenthRestDetector.detect(
                        g,
                        W,
                        H,
                        List.of(new MeasureRegion(.02f, .98f, .15f, .85f)),
                        List.of(new SixteenthRestDetector.Staff(82, 137, 13.75f, 0, 1, track)),
                        List.of());
        assertEquals(rest.toString(), 1, rest.size());
        assertEquals(.25, rest.get(0).durationBeats(), 0);
        assertArrayEquals(before, g);
    }

    @Test
    public void completeFlatRulesOverrideAnOffsetTrackForRestShape() {
        shortRest(137, 0);
    }

    @Test
    public void completeFlatRulesOverrideASlightRisingTrackForRestShape() {
        shortRest(137, .001f);
    }

    @Test
    public void completeFlatRulesOverrideASlightFallingTrackForRestShape() {
        shortRest(137, -.001f);
    }

    private SixteenthRestDetector.Staff boundedSeed(StaffPitchTrack track) {
        return new SixteenthRestDetector.Staff(80, 160, 20, 0, 1, track);
    }

    @Test
    public void aConstantTrackShiftBeyondItsOwnFrameIsPreserved() {
        var t = StaffPitchTrack.linear(600, 166, 20, 0);
        assertFalse(FlatRestRulePlane.trackFitsSeed(boundedSeed(t), 600));
    }

    @Test
    public void agreeingEndpointsDoNotHideAnInteriorTrackBend() {
        var t =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {0, 160, 20},
                                new float[] {300, 164, 20},
                                new float[] {599, 160, 20}));
        assertFalse(FlatRestRulePlane.trackFitsSeed(boundedSeed(t), 600));
    }

    @Test
    public void pairedSmallEdgeOffsetsStillRejectExcessiveGapDrift() {
        float bottom = 162.4f, gap = 21.2f;
        assertTrue(Math.abs(bottom - 160) < 3);
        assertTrue(Math.abs(bottom - 4 * gap - 80) < 3);
        assertFalse(
                FlatRestRulePlane.trackFitsSeed(
                        boundedSeed(StaffPitchTrack.linear(600, bottom, gap, 0)), 600));
    }

    @Test
    public void exactFrameAndScaleCapsAreInclusiveButNextUpIsOutside() {
        assertTrue(
                FlatRestRulePlane.trackFitsSeed(
                        boundedSeed(StaffPitchTrack.linear(600, 163, 20, 0)), 600));
        assertFalse(
                FlatRestRulePlane.trackFitsSeed(
                        boundedSeed(StaffPitchTrack.linear(600, Math.nextUp(163f), 20, 0)), 600));
        assertTrue(
                FlatRestRulePlane.trackFitsSeed(
                        boundedSeed(StaffPitchTrack.linear(600, 162, 21, 0)), 600));
        assertFalse(
                FlatRestRulePlane.trackFitsSeed(
                        boundedSeed(StaffPitchTrack.linear(600, 162, Math.nextUp(21f), 0)), 600));
    }
}
