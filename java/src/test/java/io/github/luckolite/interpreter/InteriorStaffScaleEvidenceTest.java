// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent original line and texture fixtures, never score pixels. */
public class InteriorStaffScaleEvidenceTest {
    static final int W = 800, H = 700, TOP = 300;

    static byte[] paper() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 250);
        return g;
    }

    static PrintedStaffAdmission.Evidence evidence() {
        return new PrintedStaffAdmission.Evidence(W, 0, 0, 24, 2000, 9600, 180);
    }

    static boolean rejected(byte[] g, float gap, float above, float below, int right) {
        return InteriorStaffScaleEvidence.unsupported(
                g, W, H, 0, right, gap, (line, x) -> TOP + line * gap, evidence(), above, below);
    }

    static void lines(byte[] g, int count, int left, int right, int shade, int phase) {
        for (int n = 0; n < count; n++)
            for (int x = left; x < right; x++) g[(TOP + n * 8 + phase) * W + x] = (byte) shade;
    }

    @Test
    public void absentCompressedRulesContradictPrintedNeighbors() {
        assertTrue(rejected(paper(), 8, 14, 14, W - 1));
    }

    @Test
    public void texturedCompressedBandIsRejected() {
        byte[] g = paper();
        Random r = new Random(426);
        for (int y = TOP - 12; y < TOP + 50; y++)
            for (int x = 0; x < W; x++) g[y * W + x] = (byte) (70 + r.nextInt(140));
        assertTrue(rejected(g, 8, 14, 14, W - 1));
    }

    @Test
    public void sameScaleHiddenStaffRemains() {
        assertFalse(rejected(paper(), 14, 14, 14, W - 1));
    }

    @Test
    public void disagreeingNeighborScalesCannotReject() {
        assertFalse(rejected(paper(), 8, 12, 18, W - 1));
    }

    @Test
    public void insetCueRemains() {
        assertFalse(rejected(paper(), 8, 14, 14, 350));
    }

    @Test
    public void veryFaintFiveLinesRemain() {
        byte[] g = paper();
        lines(g, 5, 40, 760, 246, 0);
        assertFalse(rejected(g, 8, 14, 14, W - 1));
    }

    @Test
    public void shortThreeRuleFragmentRemains() {
        byte[] g = paper();
        lines(g, 3, 150, 185, 245, 0);
        assertFalse(rejected(g, 8, 14, 14, W - 1));
    }

    @Test
    public void staffHintPhaseErrorRemains() {
        byte[] g = paper();
        lines(g, 4, 140, 200, 240, 4);
        assertFalse(rejected(g, 8, 14, 14, W - 1));
    }

    @Test
    public void malformedImageCannotReject() {
        assertFalse(rejected(null, 8, 14, 14, W - 1));
        assertFalse(rejected(new byte[5], 8, 14, 14, W - 1));
    }

    @Test
    public void existingFourRuleEvidenceRemains() {
        byte[] g = paper();
        var e = new PrintedStaffAdmission.Evidence(W, 1, 1, 24, 2000, 9600, 180);
        assertFalse(
                InteriorStaffScaleEvidence.unsupported(
                        g, W, H, 0, W - 1, 8, (line, x) -> TOP + line * 8, e, 14, 14));
    }

    @Test
    public void threeThickBeamBandsAreNotPartialStaff() {
        byte[] g = paper();
        for (int n = 0; n < 3; n++)
            for (int y = TOP + n * 8 - 2; y <= TOP + n * 8 + 2; y++)
                for (int x = 150; x < 200; x++) g[y * W + x] = 0;
        assertTrue(rejected(g, 8, 14, 14, W - 1));
    }

    @Test
    public void fourThickRulesPreservePartialStaff() {
        byte[] g = paper();
        for (int n = 0; n < 4; n++)
            for (int y = TOP + n * 8 - 2; y <= TOP + n * 8 + 2; y++)
                for (int x = 150; x < 200; x++) g[y * W + x] = 0;
        assertFalse(rejected(g, 8, 14, 14, W - 1));
    }

    @Test
    public void preservesPixels() {
        byte[] g = paper(), before = g.clone();
        rejected(g, 8, 14, 14, W - 1);
        assertArrayEquals(before, g);
    }
}
