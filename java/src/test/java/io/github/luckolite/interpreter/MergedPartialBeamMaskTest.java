// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original gapped two-lane masks; no score pixels, source coordinates or learned fixtures. */
public class MergedPartialBeamMaskTest {
    private static final class Figure {
        final int w = 320, h = 220;
        byte[] gray = new byte[w * h];
        int[] a = {90, 150, 1}, b = {142, 130, 1};
        int left = 92, right = 110, top = 125, bottom = 149, area = 270;
        float cx = 102, cy = 137, gap = 14;

        Figure(boolean primary, boolean secondary, boolean full, boolean detached, boolean broken) {
            Arrays.fill(gray, (byte) 255);
            for (int x = 90; x <= 142; x++) {
                int center = Math.round(147 - 20f * (x - 90) / 52);
                if (primary && (!broken || x < 115 || x > 123))
                    for (int y = center - 3; y <= center + 3; y++) gray[y * w + x] = 35;
                if (secondary && x <= (full ? 142 : 110) && (!detached || x >= 100))
                    for (int y = center - 17; y <= center - 11; y++) gray[y * w + x] = 35;
            }
            for (int y = 90; y <= 150; y++) gray[y * w + 90] = 35;
            for (int y = 70; y <= 130; y++) gray[y * w + 142] = 35;
            oval(99, 90, 11, 7, false);
            oval(151, 70, 11, 7, false);
        }

        void oval(int cx, int cy, int rx, int ry, boolean hollow) {
            for (int y = cy - ry; y <= cy + ry; y++)
                for (int x = cx - rx; x <= cx + rx; x++) {
                    double r =
                            (x - cx) * (x - cx) / (double) (rx * rx)
                                    + (y - cy) * (y - cy) / (double) (ry * ry);
                    if (r <= 1 && (!hollow || r >= .45)) gray[y * w + x] = 35;
                }
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

        void staff(int phase) {
            for (int y = 80 + phase; y <= 164; y += 14)
                for (int x = 12; x < 300; x++) gray[y * w + x] = 35;
        }

        boolean proof() {
            byte[] before = gray.clone();
            boolean result =
                    PartialBeamHeadContour.provedMerged(
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
            int[] aa = {a[0] * 2, a[1] * 2 + 1, a[2]}, bb = {b[0] * 2, b[1] * 2 + 1, b[2]};
            return PartialBeamHeadContour.provedMerged(
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

        boolean consumer(int count, boolean raw) throws Exception {
            var component = Class.forName(OmrScoreInterpreter.class.getName() + "$Component");
            var ctor =
                    component.getDeclaredConstructor(
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            float.class,
                            float.class);
            ctor.setAccessible(true);
            Object target = ctor.newInstance(area, left, right, top, bottom, cx, cy);
            var heads = new java.util.ArrayList<Object>();
            heads.add(target);
            for (int i = 0; i < count; i++) {
                int x = i == 0 ? 99 : 151, y = i == 0 ? 90 : 70;
                heads.add(
                        ctor.newInstance(237, x - 11, x + 11, y - 7, y + 7, (float) x, (float) y));
            }
            var staff = Class.forName(OmrScoreInterpreter.class.getName() + "$Staff");
            var sc = staff.getDeclaredConstructor(float.class, float.class, float.class);
            sc.setAccessible(true);
            var method =
                    OmrScoreInterpreter.class.getDeclaredMethod(
                            "mergedPartialBeamMaskHeads",
                            byte[].class,
                            int.class,
                            int.class,
                            java.util.List.class,
                            java.util.List.class);
            method.setAccessible(true);
            return ((java.util.List<?>)
                            method.invoke(
                                    null,
                                    raw ? gray : null,
                                    w,
                                    h,
                                    heads,
                                    java.util.List.of(sc.newInstance(80f, 136f, gap))))
                    .contains(target);
        }
    }

    private Figure normal() {
        return new Figure(true, true, false, false, false);
    }

    @Test
    public void bothGappedLanesMayShareOneSemanticMask() {
        assertTrue(normal().proof());
    }

    @Test
    public void doubledDensityKeepsTheSameTwoLaneProof() {
        assertTrue(normal().doubledProof());
    }

    @Test
    public void upStemMasksRetainIndependentShaftOwnership() {
        Figure f = normal();
        f.mirror(false, true);
        assertTrue(f.proof());
    }

    @Test
    public void rightOwnedMasksUseTheSameContourProof() {
        Figure f = normal();
        f.mirror(true, false);
        assertTrue(f.proof());
    }

    @Test
    public void bothMirrorsPreserveTwoLaneOwnership() {
        Figure f = normal();
        f.mirror(true, true);
        assertTrue(f.proof());
    }

    @Test
    public void independentFilledOvalMustSurvive() {
        Figure f = normal();
        f.oval(102, 137, 9, 9, false);
        assertFalse(f.proof());
    }

    @Test
    public void independentCueOvalMustSurvive() {
        Figure f = normal();
        f.oval(102, 137, 6, 4, false);
        assertFalse(f.proof());
    }

    @Test
    public void independentHollowOvalMustSurvive() {
        Figure f = normal();
        f.oval(102, 137, 9, 9, true);
        assertFalse(f.proof());
    }

    @Test
    public void completeParallelBeamsRemainOutsideThePartialRule() {
        assertFalse(new Figure(true, true, true, false, false).proof());
    }

    @Test
    public void detachedSecondaryLaneCannotOwnTheMask() {
        assertFalse(new Figure(true, true, false, true, false).proof());
    }

    @Test
    public void brokenPrimaryLaneCannotOwnTheMask() {
        assertFalse(new Figure(true, true, false, false, true).proof());
    }

    @Test
    public void absentSecondaryLaneCannotOwnTheMask() {
        assertFalse(new Figure(true, false, false, false, false).proof());
    }

    @Test
    public void opposingShaftsCannotOwnTheMask() {
        Figure f = normal();
        f.b[2] = -1;
        assertFalse(f.proof());
    }

    @Test
    public void oneShaftCannotSupplyTwoOwners() {
        Figure f = normal();
        f.b[0] = f.a[0];
        assertFalse(f.proof());
    }

    @Test
    public void recognizedOwnersAdmitTheNewProof() throws Exception {
        assertTrue(normal().consumer(2, true));
    }

    @Test
    public void oneRecognizedHeadCannotSupplyBothShafts() throws Exception {
        assertFalse(normal().consumer(1, true));
    }

    @Test
    public void semanticOnlyInputRemainsUnchanged() throws Exception {
        assertFalse(normal().consumer(2, false));
    }

    @Test
    public void consumerPreservesAnIndependentCue() throws Exception {
        Figure f = normal();
        f.oval(102, 137, 6, 4, false);
        assertFalse(f.consumer(2, true));
    }

    @Test
    public void staffRulesMayOccludeOneProvedBeamEdge() {
        Figure f = normal();
        f.staff(0);
        assertTrue(f.proof());
    }

    @Test
    public void shiftedStaffRulesMayOccludeOneProvedBeamEdge() {
        Figure f = normal();
        f.staff(1);
        assertTrue(f.proof());
    }

    @Test
    public void aCueOnAStaffRuleStillNeedsItsIndependentContour() {
        Figure f = normal();
        f.oval(102, 137, 6, 4, false);
        f.staff(0);
        assertFalse(f.proof());
    }

    @Test
    public void aCueOnAShiftedStaffRuleStillNeedsItsIndependentContour() {
        Figure f = normal();
        f.oval(102, 137, 6, 4, false);
        f.staff(1);
        assertFalse(f.proof());
    }

    @Test
    public void aSideAttachedCueOutsideThePartialMaskRetainsItsOwnShaft() throws Exception {
        Figure f = normal();
        f.oval(84, 133, 6, 4, false);
        f.left = 88;
        assertFalse(f.consumer(2, true));
    }
}
