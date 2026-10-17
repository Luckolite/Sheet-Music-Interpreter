// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural continuing rules, local shaft clutter and finite printed beams. */
public class CompleteStaffRuleBeamBandTest {
    static final int W = 1200, H = 240, X = 600;
    byte[] g = new byte[W * H], l = new byte[W * H];

    void rect(int a, int b, int c, int d, int v, int label) {
        for (int y = c; y <= d; y++)
            for (int x = a; x <= b; x++) {
                g[y * W + x] = (byte) v;
                l[y * W + x] = (byte) label;
            }
    }

    void setup() {
        Arrays.fill(g, (byte) 200);
        for (int k = 0; k < 5; k++) rect(0, W - 1, 79 + k * 16, 82 + k * 16, 100, 4);
        rect(530, 670, 110, 114, 200, 0);
        rect(530, 670, 111, 113, 100, 4);
        rect(599, 601, 110, 114, 160, 4);
        rect(599, 601, 111, 113, 100, 4);
        rect(520, 680, 75, 86, 20, 5);
        rect(420, 620, 125, 134, 20, 5);
        rect(605, 607, 100, 136, 20, 1);
    }

    boolean rule() {
        return CompleteStaffRuleBeamBand.matches(g, W, H, X, 80, 16, 165, 110, 114);
    }

    int bands() throws Exception {
        var sc = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
        var c = sc.getDeclaredConstructor(float.class, float.class, float.class);
        c.setAccessible(true);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "thickNonHeadBandsAtThreshold",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        sc,
                        int.class,
                        int.class);
        m.setAccessible(true);
        return (int)
                m.invoke(null, g, l, W, H, X, 104, 138, c.newInstance(80f, 144f, 16f), 165, 606);
    }

    @Test
    public void completeRemoteStaffRejectsLocalRuleOnly() throws Exception {
        setup();
        assertTrue(rule());
        assertEquals(1, bands());
    }

    @Test
    public void thickPrimaryBeamSurvives() {
        setup();
        assertFalse(CompleteStaffRuleBeamBand.matches(g, W, H, X, 80, 16, 165, 125, 134));
    }

    @Test
    public void finiteGenuineSecondaryIsTooMuchInkForReferenceStaff() {
        setup();
        rect(580, 620, 109, 115, 20, 5);
        assertFalse(rule());
    }

    @Test
    public void actualTwoPrintedBeamsRemainTwo() throws Exception {
        setup();
        rect(580, 620, 109, 115, 20, 5);
        assertEquals(2, bands());
    }

    @Test
    public void thinPaleFiniteSecondaryRemainsTwo() throws Exception {
        setup();
        rect(580, 620, 110, 114, 80, 5);
        assertFalse(rule());
        assertEquals(2, bands());
    }

    @Test
    public void coarsenedProceduralRasterWithoutMatchingBandBoundsAbstains() {
        setup();
        assertFalse(scaledRule(.75f));
    }

    @Test
    public void largerProceduralRasterRetainsRuleProof() {
        setup();
        assertTrue(scaledRule(1.25f));
    }

    boolean scaledRule(float scale) {
        int w = Math.round(W * scale), h = Math.round(H * scale);
        byte[] r = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                r[y * w + x] =
                        g[
                                Math.min(H - 1, (int) (y / scale)) * W
                                        + Math.min(W - 1, (int) (x / scale))];
        return CompleteStaffRuleBeamBand.matches(
                r,
                w,
                h,
                Math.round(X * scale),
                80 * scale,
                16 * scale,
                165,
                Math.round(110 * scale),
                Math.round(115 * scale) - 1);
    }

    @Test
    public void mirroredStemDirectionUsesSameStaffProof() {
        setup();
        byte[] m = new byte[g.length];
        for (int y = 0; y < H; y++) System.arraycopy(g, y * W, m, (H - 1 - y) * W, W);
        assertTrue(
                CompleteStaffRuleBeamBand.matches(
                        m, W, H, X, H - 1 - 144, 16, 165, H - 1 - 114, H - 1 - 110));
    }

    @Test
    public void clippedFrameAbstains() {
        setup();
        assertFalse(CompleteStaffRuleBeamBand.matches(g, W, H, 5, 80, 16, 165, 110, 114));
    }

    @Test
    public void disagreeingIndependentStaffPhasesAbstain() {
        setup();
        rect(0, 410, 70, 155, 200, 0);
        for (int k = 0; k < 5; k++) rect(0, 410, 81 + k * 16, 84 + k * 16, 100, 4);
        assertFalse(rule());
    }

    @Test
    public void missingRailOnBothSidesAbstains() {
        setup();
        rect(0, 529, 143, 147, 200, 0);
        rect(671, W - 1, 143, 147, 200, 0);
        assertFalse(rule());
    }

    @Test
    public void oneSidedStaffProofAbstains() {
        setup();
        rect(0, 529, 75, 150, 200, 0);
        assertFalse(rule());
    }

    @Test
    public void disconnectedSamePhaseStaffFragmentsAbstain() {
        setup();
        rect(450, 550, 107, 116, 200, 0);
        rect(650, 750, 107, 116, 200, 0);
        assertFalse(rule());
    }

    @Test
    public void wrongPhaseAbstains() {
        setup();
        assertFalse(CompleteStaffRuleBeamBand.matches(g, W, H, X, 85, 16, 165, 110, 114));
    }

    @Test
    public void beamOnlyFiveLineTextureHasNoCompleteRuleFrames() {
        setup();
        Arrays.fill(g, (byte) 200);
        for (int k = 0; k < 5; k++) rect(560, 640, 79 + k * 16, 82 + k * 16, 20, 5);
        assertFalse(rule());
    }

    @Test
    public void sourceArraysUnchanged() {
        setup();
        byte[] before = g.clone(), labels = l.clone();
        rule();
        assertArrayEquals(before, g);
        assertArrayEquals(labels, l);
    }
}
