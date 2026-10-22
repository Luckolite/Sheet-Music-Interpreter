// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original two-owner beam figures, independent of score pixels and coordinates. */
public class PartialBeamHeadContourTest {
    private static final class Figure {
        final int w = 320, h = 220;
        byte[] gray = new byte[w * h];
        int[] a = {90, 150, 1}, b = {142, 130, 1};
        int left = 94, right = 110, top = 125, bottom = 135, area = 114;
        float cx = 102, cy = 130, gap = 14;

        Figure() {
            Arrays.fill(gray, (byte) 255);
        }

        void beam(boolean secondary, boolean detached, boolean full, boolean broken) {
            for (int x = 90; x <= 142; x++) {
                int center = Math.round(147 - 20f * (x - 90) / 52);
                if (!broken || x < 115 || x > 123)
                    for (int y = center - 3; y <= center + 3; y++) gray[y * w + x] = 35;
                if (secondary && x <= (full ? 142 : 110) && (!detached || x >= 100))
                    for (int y = center - 17; y <= center - 11; y++) gray[y * w + x] = 35;
            }
            for (int y = 90; y <= 150; y++) gray[y * w + 90] = 35;
            for (int y = 70; y <= 130; y++) gray[y * w + 142] = 35;
            oval(99, 90, 9, 6);
            oval(151, 70, 9, 6);
        }

        void oval(int x, int y, int rx, int ry) {
            for (int yy = y - ry; yy <= y + ry; yy++)
                for (int xx = x - rx; xx <= x + rx; xx++)
                    if ((xx - x) * (xx - x) / (double) (rx * rx)
                                    + (yy - y) * (yy - y) / (double) (ry * ry)
                            <= 1) gray[yy * w + xx] = 35;
        }

        void staff(int phase) {
            for (int y = 80 + phase; y <= 164; y += 14)
                for (int x = 12; x < 300; x++) gray[y * w + x] = 35;
        }

        void mirror(boolean horizontal, boolean vertical) {
            byte[] next = new byte[gray.length];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    next[(vertical ? h - 1 - y : y) * w + (horizontal ? w - 1 - x : x)] =
                            gray[y * w + x];
            gray = next;
            if (horizontal) {
                a[0] = w - 1 - a[0];
                b[0] = w - 1 - b[0];
                int old = left;
                left = w - 1 - right;
                right = w - 1 - old;
                cx = w - 1 - cx;
            }
            if (vertical) {
                a[1] = h - 1 - a[1];
                b[1] = h - 1 - b[1];
                a[2] = -a[2];
                b[2] = -b[2];
                int old = top;
                top = h - 1 - bottom;
                bottom = h - 1 - old;
                cy = h - 1 - cy;
            }
        }

        boolean proof() {
            byte[] before = gray.clone();
            boolean result =
                    PartialBeamHeadContour.proved(
                            gray, w, h, a, b, cx, cy, left, top, right, bottom, area, gap);
            assertArrayEquals(before, gray);
            return result;
        }

        boolean doubledProof() {
            byte[] twice = new byte[w * h * 4];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    for (int dy = 0; dy < 2; dy++)
                        for (int dx = 0; dx < 2; dx++)
                            twice[(y * 2 + dy) * (w * 2) + x * 2 + dx] = gray[y * w + x];
            int[] aa = {a[0] * 2, a[1] * 2 + (a[2] > 0 ? 1 : 0), a[2]},
                    bb = {b[0] * 2, b[1] * 2 + (b[2] > 0 ? 1 : 0), b[2]};
            return PartialBeamHeadContour.proved(
                    twice,
                    w * 2,
                    h * 2,
                    aa,
                    bb,
                    cx * 2 + .5f,
                    cy * 2 + .5f,
                    left * 2,
                    top * 2,
                    right * 2 + 1,
                    bottom * 2 + 1,
                    area * 4,
                    gap * 2);
        }

        boolean consumer(int ownerCount, boolean raw) throws Exception {
            Class<?> component = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
            var constructor =
                    component.getDeclaredConstructor(
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            float.class,
                            float.class);
            constructor.setAccessible(true);
            Object target = constructor.newInstance(area, left, right, top, bottom, cx, cy);
            var heads = new java.util.ArrayList<Object>();
            heads.add(target);
            for (int i = 0; i < ownerCount; i++) {
                int x = i == 0 ? 99 : 151, y = i == 0 ? 90 : 70;
                oval(x, y, 11, 7);
                heads.add(
                        constructor.newInstance(
                                237, x - 11, x + 11, y - 7, y + 7, (float) x, (float) y));
            }
            Class<?> staff = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
            var staffConstructor =
                    staff.getDeclaredConstructor(float.class, float.class, float.class);
            staffConstructor.setAccessible(true);
            var method =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "partialBeamContourHeads",
                            byte[].class,
                            int.class,
                            int.class,
                            java.util.List.class,
                            java.util.List.class);
            method.setAccessible(true);
            byte[] before = gray.clone();
            var rejected =
                    (java.util.List<?>)
                            method.invoke(
                                    null,
                                    raw ? gray : null,
                                    w,
                                    h,
                                    heads,
                                    java.util.List.of(
                                            staffConstructor.newInstance(80f, 136f, gap)));
            assertArrayEquals(before, gray);
            return rejected.contains(target);
        }
    }

    private Figure normal() {
        Figure f = new Figure();
        f.beam(true, false, false, false);
        return f;
    }

    @Test
    public void finiteSecondaryLaneBelongsToItsRealStemOwners() {
        assertTrue(normal().proof());
    }

    @Test
    public void upStemGeometryKeepsTheSameOwnership() {
        Figure f = normal();
        f.mirror(false, true);
        assertTrue(f.proof());
    }

    @Test
    public void theRightEndpointCanOwnTheShortLane() {
        Figure f = normal();
        f.mirror(true, false);
        assertTrue(f.proof());
    }

    @Test
    public void bothMirrorsPreserveTheProof() {
        Figure f = normal();
        f.mirror(true, true);
        assertTrue(f.proof());
    }

    @Test
    public void longStaffRulesDoNotBecomeExtraBeamContours() {
        Figure f = normal();
        f.staff(0);
        assertTrue(f.proof());
    }

    @Test
    public void shiftedStaffRulesNeedTheirOwnRawWitness() {
        Figure f = normal();
        f.staff(1);
        assertTrue(f.proof());
    }

    @Test
    public void brokenPrimaryLaneCannotSupplyOwnership() {
        Figure f = new Figure();
        f.beam(true, false, false, true);
        assertFalse(f.proof());
    }

    @Test
    public void aDetachedStrokeIsNotASecondaryBeam() {
        Figure f = new Figure();
        f.beam(true, true, false, false);
        assertFalse(f.proof());
    }

    @Test
    public void aPrimaryBeamAloneCannotSupplyTheShortLane() {
        Figure f = new Figure();
        f.beam(false, false, false, false);
        assertFalse(f.proof());
    }

    @Test
    public void twoCompleteBeamsRemainOutsideThisPartialBeamRule() {
        Figure f = new Figure();
        f.beam(true, false, true, false);
        assertFalse(f.proof());
    }

    @Test
    public void independentFullHeadContourIsPreserved() {
        Figure f = normal();
        f.oval(102, 130, 9, 6);
        assertFalse(f.proof());
    }

    @Test
    public void independentCueContourIsPreserved() {
        Figure f = normal();
        f.oval(102, 130, 6, 4);
        assertFalse(f.proof());
    }

    @Test
    public void aCueWithItsOwnPrintedShaftIsPreserved() {
        Figure f = normal();
        f.oval(102, 130, 6, 4);
        for (int y = 94; y <= 130; y++) f.gray[y * f.w + 108] = 35;
        assertFalse(f.proof());
    }

    @Test
    public void isolatedFilledHeadDoesNotBorrowRemoteBeams() {
        Figure f = normal();
        f.cx = 118;
        f.left = 112;
        f.right = 124;
        f.oval(118, 130, 6, 4);
        assertFalse(f.proof());
    }

    @Test
    public void horizontalStaffInkAloneIsNotABeam() {
        Figure f = new Figure();
        f.staff(0);
        assertFalse(f.proof());
    }

    @Test
    public void doubleDensitySourceKeepsBothOrientations() {
        Figure down = normal();
        assertTrue(down.doubledProof());
        Figure up = normal();
        up.mirror(false, true);
        assertTrue(up.doubledProof());
    }

    @Test
    public void doubleDensityCueContourRemainsANote() {
        Figure f = normal();
        f.oval(102, 130, 6, 4);
        assertFalse(f.doubledProof());
    }

    @Test
    public void consumerRequiresTwoIndependentRecognizedOwners() throws Exception {
        assertTrue(normal().consumer(2, true));
    }

    @Test
    public void consumerPreservesTheHeadWithOnlyOneOwner() throws Exception {
        assertFalse(normal().consumer(1, true));
    }

    @Test
    public void consumerPreservesSemanticOnlyInput() throws Exception {
        assertFalse(normal().consumer(2, false));
    }

    @Test
    public void consumerPreservesAnIndependentCueContour() throws Exception {
        Figure f = normal();
        f.oval(102, 130, 6, 4);
        assertFalse(f.consumer(2, true));
    }

    @Test
    public void consumerPreservesAnIndependentFullContour() throws Exception {
        Figure f = normal();
        f.oval(102, 130, 9, 6);
        assertFalse(f.consumer(2, true));
    }

    @Test
    public void consumerRequiresTheCompletePrimaryLane() throws Exception {
        Figure f = new Figure();
        f.beam(true, false, false, true);
        assertFalse(f.consumer(2, true));
    }

    @Test
    public void consumerRequiresAConnectedPartialLane() throws Exception {
        Figure f = new Figure();
        f.beam(true, true, false, false);
        assertFalse(f.consumer(2, true));
    }

    private Figure primary() {
        Figure f = normal();
        f.cx = 102;
        f.cy = 143;
        f.left = 92;
        f.right = 110;
        f.top = 137;
        f.bottom = 149;
        f.area = 177;
        return f;
    }

    @Test
    public void semanticPrimaryLaneAlsoNeedsThePartialSecondaryWitness() {
        assertTrue(primary().proof());
    }

    @Test
    public void primaryLaneOwnershipSurvivesUpStems() {
        Figure f = primary();
        f.mirror(false, true);
        assertTrue(f.proof());
    }

    @Test
    public void primaryLaneOwnershipSurvivesHorizontalMirroring() {
        Figure f = primary();
        f.mirror(true, false);
        assertTrue(f.proof());
    }

    @Test
    public void primaryLaneOwnershipSurvivesDoubleDensity() {
        assertTrue(primary().doubledProof());
    }

    @Test
    public void primaryLaneWithAnIndependentCueOvalRemainsANote() {
        Figure f = primary();
        f.oval(102, 143, 6, 4);
        assertFalse(f.proof());
    }

    @Test
    public void primaryLaneWithAnIndependentFullOvalRemainsANote() {
        Figure f = primary();
        f.oval(102, 143, 9, 6);
        assertFalse(f.proof());
    }

    @Test
    public void consumerRejectsAnOwnedPrimaryLane() throws Exception {
        assertTrue(primary().consumer(2, true));
    }

    @Test
    public void consumerPreservesAnIndependentPrimaryCue() throws Exception {
        Figure f = primary();
        f.oval(102, 143, 6, 4);
        assertFalse(f.consumer(2, true));
    }
}
