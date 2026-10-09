// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original wide four-staff systems and independently owned short returning contours. */
public class WideSystemIncomingTieControlsTest {
    static final int W = 1000, H = 1280, GAP = 12;

    static void box(byte[] g, int l, int t, int r, int b, int c) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = (byte) c;
    }

    static byte[] frame() {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int system : new int[] {60, 710})
            for (int staff = 0; staff < 4; staff++)
                for (int rule = 0; rule < 5; rule++)
                    box(
                            g,
                            20,
                            system + staff * 130 + rule * GAP,
                            979,
                            system + staff * 130 + rule * GAP,
                            0);
        box(g, 742, 258, 758, 266, 0);
        box(g, 232, 908, 248, 916, 0);
        return g;
    }

    static ScoreNoteEvent note(int measure, float pos, int step, float y) {
        return new ScoreNoteEvent(
                measure, pos, step, 1, 4, y / H, false, 0, 0, 2, 2, 1, 0, 0, 18, false, 0);
    }

    static WideSystemIncomingTieProof.Endpoint before() {
        return new WideSystemIncomingTieProof.Endpoint(
                note(0, .7f, -4, 262), 742, 758, 258, 266, 750, 262, 12);
    }

    static WideSystemIncomingTieProof.Endpoint after() {
        return new WideSystemIncomingTieProof.Endpoint(
                note(1, .1f, -4, 912), 232, 248, 908, 916, 240, 912, 12);
    }

    static void bowl(byte[] g, float bend, boolean angular) {
        for (int x = 208; x <= 229; x++) {
            float t = (x - 208f) / 21;
            int y = Math.round(917 + bend * (angular ? 1 - Math.abs(2 * t - 1) : 4 * t * (1 - t)));
            box(g, x, y - 1, x, y + 1, 0);
        }
    }

    static byte[] positive() {
        var g = frame();
        bowl(g, 4, false);
        return g;
    }

    static boolean prove(byte[] g) {
        return WideSystemIncomingTieProof.prove(g, W, H, before(), after(), true, 1).proved();
    }

    static void yes(byte[] g) {
        assertTrue(
                WideSystemIncomingTieProof.prove(g, W, H, before(), after(), true, 1).toString(),
                prove(g));
    }

    static void no(byte[] g) {
        assertFalse(
                WideSystemIncomingTieProof.prove(g, W, H, before(), after(), true, 1).toString(),
                prove(g));
    }

    @Test
    public void wideFourStaffSystemsWithShortReturningBowl() {
        yes(positive());
    }

    @Test
    public void originalFortyGapLimitIsExceeded() {
        assertTrue((after().y() - before().y()) / GAP > 40);
        yes(positive());
    }

    @Test
    public void outgoingOnlyDoesNotEstablishTie() {
        no(frame());
    }

    @Test
    public void incomingOnlyWithoutOutgoingDoesNotEstablishTie() {
        assertFalse(
                WideSystemIncomingTieProof.prove(positive(), W, H, before(), after(), false, 1)
                        .proved());
    }

    @Test
    public void wrongWrittenPitchRejects() {
        var a = after();
        var wrong =
                new WideSystemIncomingTieProof.Endpoint(
                        note(1, .1f, -2, 912),
                        a.left(),
                        a.right(),
                        a.top(),
                        a.bottom(),
                        a.x(),
                        a.y(),
                        a.gap());
        assertFalse(
                WideSystemIncomingTieProof.prove(positive(), W, H, before(), wrong, true, 1)
                        .proved());
    }

    @Test
    public void nonadjacentLogicalMeasureRejects() {
        var a = after();
        var wrong =
                new WideSystemIncomingTieProof.Endpoint(
                        note(2, .1f, -4, 912),
                        a.left(),
                        a.right(),
                        a.top(),
                        a.bottom(),
                        a.x(),
                        a.y(),
                        a.gap());
        assertFalse(
                WideSystemIncomingTieProof.prove(positive(), W, H, before(), wrong, true, 1)
                        .proved());
    }

    @Test
    public void wrongPhysicalStaffRankRejects() {
        var a = after();
        var wrong =
                new WideSystemIncomingTieProof.Endpoint(
                        note(1, .1f, -4, 1042), 232, 248, 1038, 1046, 240, 1042, 12);
        assertFalse(
                WideSystemIncomingTieProof.prove(positive(), W, H, before(), wrong, true, 1)
                        .proved());
    }

    @Test
    public void missingPrintedRuleDoesNotInventSystem() {
        var g = positive();
        box(g, 20, 190 + 24, 979, 190 + 24, 255);
        no(g);
    }

    @Test
    public void extraMissingStaffCannotShiftRank() {
        var g = positive();
        for (int i = 0; i < 5; i++) box(g, 20, 60 + i * 12, 979, 60 + i * 12, 255);
        no(g);
    }

    @Test
    public void straightStrokeRejects() {
        var g = frame();
        box(g, 208, 919, 229, 921, 0);
        no(g);
    }

    @Test
    public void angularVIsNotRoundedTie() {
        var g = frame();
        bowl(g, 7, true);
        no(g);
    }

    @Test
    public void brokenCurveRejects() {
        var g = positive();
        box(g, 217, 915, 220, 926, 255);
        no(g);
    }

    @Test
    public void deepSlurCannotBeShortIncomingTie() {
        var g = frame();
        bowl(g, 17, false);
        no(g);
    }

    @Test
    public void distantNeighboringBowlDoesNotOwnHead() {
        var g = frame();
        for (int x = 192; x <= 213; x++) {
            float t = (x - 192f) / 21;
            int y = Math.round(917 + 4 * 4 * t * (1 - t));
            box(g, x, y - 1, x, y + 1, 0);
        }
        no(g);
    }

    @Test
    public void connectedNeighboringShaftRejects() {
        var g = positive();
        box(g, 219, 919, 220, 948, 0);
        no(g);
    }

    @Test
    public void lowerNeighboringStrokeDoesNotManufactureCurve() {
        var g = frame();
        box(g, 208, 919, 229, 920, 0);
        box(g, 214, 922, 223, 923, 0);
        no(g);
    }

    @Test
    public void systemLayoutMustRemainHomologous() {
        var g = positive();
        for (int i = 0; i < 5; i++) {
            box(g, 20, 970 + i * 12, 979, 970 + i * 12, 255);
            box(g, 20, 1002 + i * 12, 979, 1002 + i * 12, 0);
        }
        no(g);
    }

    @Test
    public void originalInputPixelsRemainExact() {
        var g = positive();
        var before = g.clone();
        prove(g);
        assertArrayEquals(before, g);
    }

    @Test
    public void stafflessBowDoesNotEstablishSystem() {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        bowl(g, 4, false);
        no(g);
    }

    @Test
    public void oppositeArcSideDoesNotMatch() {
        assertFalse(
                WideSystemIncomingTieProof.prove(positive(), W, H, before(), after(), true, -1)
                        .proved());
    }

    @Test
    public void faintLastShoulderAtCropIsProvedByNextPaperColumn() {
        var g = frame();
        for (int x = 208; x <= 230; x++) {
            float t = (x - 208f) / 22;
            int y = Math.round(917 + 4 * 4 * t * (1 - t));
            box(g, x, y - 1, x, y + 1, x == 230 ? 200 : 0);
        }
        yes(g);
    }

    @Test
    public void unknownStrokeBeyondCroppedShoulderRejects() {
        var g = frame();
        for (int x = 208; x <= 230; x++) {
            float t = (x - 208f) / 22;
            int y = Math.round(917 + 4 * 4 * t * (1 - t));
            box(g, x, y - 1, x, y + 1, 0);
        }
        box(g, 231, 916, 231, 925, 0);
        no(g);
    }

    @Test
    public void emptyClaimedEndpointCannotOwnBow() {
        var g = positive();
        box(g, 232, 908, 248, 916, 255);
        no(g);
    }
}
