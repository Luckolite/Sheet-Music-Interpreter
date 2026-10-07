// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original straight stroke drawings, split by pale scan bridges; no score pixels. */
public class ShadedHairpinFragmentsTest {
    static final int W = 1000, H = 800, G = 16, L = 120, R = 760;
    static final PlayingTechniqueDetector.Staff STAFF =
            new PlayingTechniqueDetector.Staff(300, 364, G, 0, 1);

    byte[] image(boolean whiteGap, boolean bend, boolean mirror) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 140);
        for (int x = L; x <= R; x++) {
            int xx = mirror ? L + R - x : x;
            double upper = 410 - .05 * (x - L), lower = 410 + (x - L) / 60.0;
            if (bend) upper += 12 * Math.sin(Math.PI * (x - L) / (R - L));
            int ink = x < 250 || x >= 440 && x <= 449 ? 114 : 90;
            if (whiteGap && x >= 440 && x <= 449) continueUpper(p, xx, upper, 140);
            else continueUpper(p, xx, upper, ink);
            continueUpper(p, xx, lower, 90);
        }
        return p;
    }

    void continueUpper(byte[] p, int x, double y, int ink) {
        for (int dy = -1; dy <= 1; dy++) p[(Math.round((float) y) + dy) * W + x] = (byte) ink;
    }

    List<ShadedHairpinFragments.Rail> rails() {
        return List.of(
                new ShadedHairpinFragments.Rail(STAFF, 250, 439, -.05, 416),
                new ShadedHairpinFragments.Rail(STAFF, 450, R, -.05, 416),
                new ShadedHairpinFragments.Rail(STAFF, L, R, 1 / 60.0, 408));
    }

    List<ShadedHairpinFragments.Shape> recover(byte[] p, List<ShadedHairpinFragments.Rail> r) {
        byte[] before = p.clone();
        var result = ShadedHairpinFragments.recover(r, p, W, H);
        assertArrayEquals(before, p);
        return result;
    }

    List<ScoreDynamicChange> detect(byte[] p) {
        byte[] before = p.clone();
        var found =
                ScoreDynamicsDetector.detect(
                        List.of(),
                        List.of(STAFF),
                        List.of(
                                new MeasureRegion(.08f, .46f, .37f, .46f),
                                new MeasureRegion(.46f, .92f, .37f, .46f)),
                        List.of(),
                        p,
                        W,
                        H);
        assertArrayEquals(before, p);
        return found;
    }

    @Test
    public void paleBridgesAndMissingLeadingStrongArmKeepCrescendo() {
        var found = detect(image(false, false, false));
        assertEquals(1, found.size());
        assertEquals(1, found.get(0).direction());
    }

    @Test
    public void reflectedFragmentsKeepDiminuendo() {
        var found = detect(image(false, false, true));
        assertEquals(1, found.size());
        assertEquals(-1, found.get(0).direction());
    }

    @Test
    public void rawPairProvesCompleteFiniteWedge() {
        var s = recover(image(false, false, false), rails());
        assertEquals(1, s.size());
        assertEquals(L, s.get(0).left());
        assertEquals(R, s.get(0).right());
    }

    @Test
    public void whiteGapCannotBeInventedAsPaleBridge() {
        assertTrue(recover(image(true, false, false), rails()).isEmpty());
    }

    @Test
    public void bentRawCurveCannotSubstituteForStraightArms() {
        assertTrue(recover(image(false, true, false), rails()).isEmpty());
    }

    @Test
    public void missingRawApexCannotBeExtrapolated() {
        byte[] p = image(false, false, false);
        for (int x = L; x < L + 12; x++) for (int y = 400; y <= 420; y++) p[y * W + x] = (byte) 140;
        assertTrue(recover(p, rails()).isEmpty());
    }

    @Test
    public void emptyUpperArmCannotBeExtrapolated() {
        byte[] p = image(false, false, false);
        for (int x = L; x < 250; x++) for (int y = 395; y <= 411; y++) p[y * W + x] = (byte) 140;
        assertTrue(recover(p, rails()).isEmpty());
    }

    @Test
    public void filledInteriorCannotBecomeOpenWedge() {
        byte[] p = image(false, false, false);
        for (int x = 300; x <= R; x++)
            for (int y = Math.round(410 - .05f * (x - L));
                    y <= Math.round(410 + (x - L) / 60f);
                    y++) p[y * W + x] = 90;
        assertTrue(recover(p, rails()).isEmpty());
    }

    @Test
    public void onePixelFitErrorStillNeedsTheActualSharedCap() {
        var r = new ArrayList<>(rails());
        r.set(0, new ShadedHairpinFragments.Rail(STAFF, 250, 439, -.05, 417));
        r.set(1, new ShadedHairpinFragments.Rail(STAFF, 450, R, -.05, 417));
        assertEquals(1, recover(image(false, false, false), r).size());
    }

    @Test
    public void unshadedPaperKeepsPreviousRecognition() {
        byte[] p = image(false, false, false);
        for (int i = 0; i < p.length; i++) if ((p[i] & 255) == 140) p[i] = (byte) 250;
        assertTrue(recover(p, rails()).isEmpty());
        assertEquals(1, detect(p).size());
    }

    @Test
    public void parallelRulesCannotBecomeOpeningArms() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 140);
        for (int x = L; x <= R; x++) for (int y : new int[] {410, 426}) continueUpper(p, x, y, 90);
        var r =
                List.of(
                        new ShadedHairpinFragments.Rail(STAFF, L, R, 0, 410),
                        new ShadedHairpinFragments.Rail(STAFF, L, R, 0, 426));
        assertTrue(recover(p, r).isEmpty());
    }

    @Test
    public void notationInsideStaffCannotBecomeDirection() {
        var s = new PlayingTechniqueDetector.Staff(395, 459, G, 0, 1);
        var r = new ArrayList<ShadedHairpinFragments.Rail>();
        for (var a : rails())
            r.add(
                    new ShadedHairpinFragments.Rail(
                            s, a.left(), a.right(), a.slope(), a.intercept()));
        assertTrue(recover(image(false, false, false), r).isEmpty());
    }

    @Test
    public void incompleteImageCannotProveDirection() {
        assertTrue(ShadedHairpinFragments.recover(rails(), new byte[4], W, H).isEmpty());
    }

    @Test
    public void nonfiniteStaffCannotProveFragment() {
        assertNull(
                ShadedHairpinFragments.fragment(
                        new PlayingTechniqueDetector.Staff(300, 364, Float.NaN, 0, 1),
                        120,
                        150,
                        new int[31],
                        new int[31]));
    }

    @Test
    public void tinyTerminalCapDoesNotInvalidateStraightCore() {
        int[] a = new int[121], b = new int[121];
        Arrays.fill(a, 410);
        Arrays.fill(b, 412);
        for (int x = 118; x < 121; x++) {
            a[x] += 3;
            b[x] += 3;
        }
        int[] aa = a.clone(), bb = b.clone();
        assertNotNull(ShadedHairpinFragments.fragment(STAFF, L, L + 120, a, b));
        assertArrayEquals(aa, a);
        assertArrayEquals(bb, b);
    }

    @Test
    public void sameInteriorBuckleCannotBecomeStraightRail() {
        int[] a = new int[121], b = new int[121];
        Arrays.fill(a, 410);
        Arrays.fill(b, 412);
        for (int x = 58; x < 61; x++) {
            a[x] += 3;
            b[x] += 3;
        }
        assertNull(ShadedHairpinFragments.fragment(STAFF, L, L + 120, a, b));
    }

    @Test
    public void weakLeadingArmStillNeedsContinuousRawInkAndApex() {
        var r =
                List.of(
                        new ShadedHairpinFragments.Rail(STAFF, 416, R, -.05, 416),
                        new ShadedHairpinFragments.Rail(STAFF, L, R, 1 / 60.0, 408));
        assertEquals(1, recover(image(false, false, false), r).size());
    }

    @Test
    public void twoShortArmCoresCannotInventLongWedge() {
        var r =
                List.of(
                        new ShadedHairpinFragments.Rail(STAFF, 300, 680, -.05, 416),
                        new ShadedHairpinFragments.Rail(STAFF, 300, 680, 1 / 60.0, 408));
        assertTrue(recover(image(false, false, false), r).isEmpty());
    }

    @Test
    public void rasterPhaseToleranceCannotInventShiftedRawArm() {
        var r =
                List.of(
                        new ShadedHairpinFragments.Rail(STAFF, 250, 439, -.05, 416),
                        new ShadedHairpinFragments.Rail(STAFF, 450, R, -.05, 419),
                        new ShadedHairpinFragments.Rail(STAFF, L, R, 1 / 60.0, 408));
        assertTrue(recover(image(false, false, false), r).isEmpty());
    }

    @Test
    public void boundedLeadingScanWarpStillNeedsTheRealArm() {
        byte[] p = image(false, false, false);
        for (int x = L; x < 416; x++) {
            double y = 410 - .05 * (x - L);
            continueUpper(p, x, y, 140);
            continueUpper(p, x, y + 3 * Math.sin(Math.PI * (x - L) / (416 - L)), 114);
        }
        var r =
                List.of(
                        new ShadedHairpinFragments.Rail(STAFF, 416, R, -.05, 416),
                        new ShadedHairpinFragments.Rail(STAFF, L, R, 1 / 60.0, 408));
        assertEquals(1, recover(p, r).size());
    }

    @Test
    public void whiteGapInExtendedArmCannotBorrowOppositeArm() {
        byte[] p = image(false, false, false);
        for (int x = 230; x <= 244; x++) continueUpper(p, x, 410 - .05 * (x - L), 140);
        var r =
                List.of(
                        new ShadedHairpinFragments.Rail(STAFF, 416, R, -.05, 416),
                        new ShadedHairpinFragments.Rail(STAFF, L, R, 1 / 60.0, 408));
        assertTrue(recover(p, r).isEmpty());
    }

    @Test
    public void grayApexCanJoinBothCloseArms() {
        byte[] p = image(false, false, false);
        for (int x = L; x < 235; x++)
            for (int y = Math.round(410 - .05f * (x - L)) - 3;
                    y <= Math.round(410 + (x - L) / 60f) + 3;
                    y++) p[y * W + x] = 95;
        assertEquals(1, recover(p, rails()).size());
    }

    byte[] partlyConnected(boolean mirror) {
        byte[] p = image(false, false, false);
        for (int x = L; x <= R; x++) {
            int xx = mirror ? L + R - x : x;
            continueUpper(p, x, 410 - .05 * (x - L), 140);
            continueUpper(p, x, 410 + (x - L) / 60.0, 140);
            continueUpper(p, xx, 410 - .05 * (x - L), x <= 200 || x >= 416 ? 90 : 114);
            continueUpper(p, xx, 410 + (x - L) / 60.0, 90);
        }
        return p;
    }

    @Test
    public void mixedConnectedTipAndSeparateRailKeepCrescendo() {
        var d = detect(partlyConnected(false));
        assertEquals(1, d.size());
        assertEquals(1, d.get(0).direction());
    }

    @Test
    public void mixedConnectedTipAndSeparateRailKeepDiminuendo() {
        byte[] p = partlyConnected(false), m = new byte[p.length];
        Arrays.fill(m, (byte) 140);
        for (int y = 0; y < H; y++)
            for (int x = L; x <= R; x++) m[y * W + L + R - x] = p[y * W + x];
        var d = detect(m);
        assertEquals(1, d.size());
        assertEquals(-1, d.get(0).direction());
    }

    @Test
    public void longOpposedArmsCannotMergeAtTheirSharedCap() {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 140);
        for (int x = L; x <= R; x++) {
            continueUpper(p, x, 410 - .015 * (x - L), 90);
            continueUpper(p, x, 410 + .015 * (x - L), 90);
        }
        var r =
                List.of(
                        new ShadedHairpinFragments.Rail(STAFF, L, R, -.015, 411.8),
                        new ShadedHairpinFragments.Rail(STAFF, L, R, .015, 408.2));
        assertEquals(1, recover(p, r).size());
    }
}
