// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural hanging rectangles and optical rule fringes; no source pixels. */
public class CompleteRuleRowWholeRestTest {
    static final int W = 800, H = 320;
    final byte[] gray = new byte[W * H];
    final int gap, thickness, left, right, first, last;
    SixteenthRestDetector.Staff staff;

    CompleteRuleRowWholeRestTest(int g, int thick) {
        gap = g;
        thickness = thick;
        left = 640 - Math.round(g * .7f);
        right = 640 + Math.round(g * .7f);
        first = 100 + g + thick;
        last = first + Math.round(g * .4f);
        staff = new SixteenthRestDetector.Staff(100, 100 + 4 * g, g, 0, 1);
        Arrays.fill(gray, (byte) 255);
        for (int n = 0; n < 5; n++) {
            box(20, 100 + n * g, 779, 100 + n * g, 0);
            if (thick > 1) box(500, 101 + n * g, 779, 100 + n * g + thick - 1, 0);
        }
        box(left, first, right, last, 0);
        box(638, 100 + g, 642, 100 + g, 180);
    }

    public CompleteRuleRowWholeRestTest() {
        this(16, 3);
    }

    void box(int l, int t, int r, int b, int v) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) gray[y * W + x] = (byte) v;
    }

    List<WholeRestClassifier.Decision> read(List<ScoreNoteEvent> notes) {
        return WholeRestClassifier.detect(
                gray,
                W,
                H,
                List.of(new MeasureRegion(.625f, .97375f, .2f, .8f)),
                List.of(staff),
                notes,
                List.of());
    }

    boolean full() {
        return read(List.of()).stream().anyMatch(d -> d.event().isFullMeasure());
    }

    @Test
    public void completeCoreBelowPaleThreePixelFringe() {
        assertTrue(full());
    }

    @Test
    public void completeCoreBelowPaleTwoPixelFringe() {
        assertTrue(new CompleteRuleRowWholeRestTest(16, 2).full());
    }

    @Test
    public void independentlyScaledRuleBody() {
        var p = new CompleteRuleRowWholeRestTest(20, 3);
        p.box(637, 120, 643, 120, 180);
        assertTrue(p.full());
    }

    @Test
    public void nominalWholeGlyphResolvesOnlyThroughMeter() {
        var d = read(List.of());
        assertEquals(1, d.size());
        assertTrue(d.get(0).event().isFullMeasure());
        assertEquals(4, d.get(0).event().durationBeats(), 0);
        assertEquals(3, d.get(0).event().resolvedDurationBeats(3), 0);
    }

    @Test
    public void noCompleteSupportingRowFailsClosed() {
        box(638, 100 + gap, 642, 100 + gap + thickness - 1, 180);
        assertFalse(full());
    }

    @Test
    public void detachedPlateCannotBorrowSupportingRule() {
        box(left, first, right, first, 255);
        assertFalse(full());
    }

    @Test
    public void missingStaffCannotProveWholeRest() {
        for (int n : new int[] {0, 2, 3, 4})
            box(20, 100 + n * gap, 779, 100 + n * gap + thickness - 1, 255);
        assertFalse(full());
    }

    @Test
    public void standingHalfPlateCannotBecomeWhole() {
        box(left, first, right, last, 255);
        box(left, 100 + gap - 8, right, 100 + gap - 2, 0);
        assertFalse(full());
    }

    @Test
    public void lowerRuleBodyCannotBorrowSecondRule() {
        box(left, first, right, last, 255);
        box(left, first + gap, right, last + gap, 0);
        assertFalse(full());
    }

    @Test
    public void distantStaffCannotOwnPlate() {
        staff = new SixteenthRestDetector.Staff(132, 196, 16, 0, 1);
        assertFalse(full());
    }

    @Test
    public void soundingNotePreventsFullBarOwnership() {
        var n = new ScoreNoteEvent(0, .15f, 0, 0, 1, .5f, false, 0, 0, 2, 1);
        assertTrue(read(List.of(n)).stream().noneMatch(d -> d.event().isFullMeasure()));
    }

    @Test
    public void offCenterWholeGlyphIsStillLiteral() {
        box(left, first, right, last, 255);
        box(left + 100, first, right + 100, last, 0);
        assertFalse(full());
    }

    @Test
    public void isolatedAugmentationInkPreventsFullBar() {
        box(right + 8, first + 3, right + 10, first + 5, 0);
        assertFalse(full());
    }

    void halo() {
        for (int x = 500; x <= 779; x++)
            if (x < left || x > right) {
                gray[first * W + x] = (byte) 180;
                gray[(first + 1) * W + x] = (byte) 220;
            }
    }

    @Test
    public void independentThinFringeProfileRetainsWholeRest() {
        halo();
        assertTrue(full());
    }

    @Test
    public void faintOuterDotIsNotStaffFringe() {
        halo();
        box(right + 8, first + 1, right + 10, first + 1, 215);
        assertFalse(full());
    }

    @Test
    public void faintInnerAndOuterDotIsNotStaffFringe() {
        halo();
        box(right + 8, first, right + 10, first, 165);
        box(right + 8, first + 1, right + 10, first + 1, 215);
        assertFalse(full());
    }

    @Test
    public void twoRowFaintDotProtrudingBelowFringeRemainsOwned() {
        halo();
        box(right + 8, first + 1, right + 10, first + 2, 215);
        assertFalse(full());
    }

    @Test
    public void detachedFaintMarkCannotClaimOpticalFringe() {
        box(right + 8, first + 2, right + 10, first + 2, 220);
        assertFalse(full());
    }

    @Test
    public void darkenedReferenceCannotHideMatchingDot() {
        halo();
        int ref = right + Math.round(gap * 3) + 10;
        box(ref, first + 1, ref + 2, first + 1, 215);
        box(right + 8, first + 1, right + 10, first + 1, 215);
        assertFalse(full());
    }

    @Test
    public void referenceEdgeCannotSetPermissiveProfile() {
        halo();
        int ref = right + Math.round(gap * 3);
        box(ref, first + 1, ref + 2, first + 1, 210);
        box(right + 8, first + 1, right + 10, first + 1, 215);
        assertFalse(full());
    }

    @Test
    public void broadDarkerReferenceCannotHideLocalDot() {
        halo();
        int ref = left - Math.round(gap * 3);
        box(ref, first + 1, left - Math.round(gap * .75f), first + 1, 215);
        box(right + 8, first + 1, right + 10, first + 1, 215);
        assertFalse(full());
    }

    @Test
    public void missingReferenceInnerEdgeCannotOwnUnknownInk() {
        halo();
        box(left - Math.round(gap * 3), first, left - Math.round(gap * .75f), first, 255);
        assertFalse(full());
    }

    @Test
    public void originalRasterRemainsReadOnly() {
        var before = gray.clone();
        read(List.of());
        assertArrayEquals(before, gray);
    }
}
