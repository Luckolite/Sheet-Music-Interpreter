// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedSlurContoursTest {
    private static final int W = 240, H = 220;

    private byte[] paper() {
        var gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        return gray;
    }

    private StaffPitchTrack staff() {
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {0, 150, 12}, new float[] {W - 1, 150, 12}));
    }

    private void curve(
            byte[] gray,
            int left,
            int right,
            double center,
            double slope,
            double bend,
            boolean hinge) {
        for (int x = left; x <= right; x++) {
            double t = 2.0 * (x - left) / (right - left) - 1;
            int y = (int) Math.round(center + slope * t + bend * (hinge ? Math.abs(t) : t * t));
            gray[y * W + x] = 0;
        }
    }

    private List<PrintedSlurContours.Bow> find(byte[] gray) {
        return PrintedSlurContours.find(gray, null, W, H, 0, 20, W - 1, H - 1, staff()).bows();
    }

    @Test
    public void unequalHeightReturningBowIsNotRestrictedToSamePitch() {
        var gray = paper();
        curve(gray, 40, 190, 55, 8, 14, false);
        var bows = find(gray);
        assertEquals(1, bows.size());
        assertTrue(bows.get(0).above());
        assertEquals(40, bows.get(0).start().x());
        assertEquals(190, bows.get(0).end().x());
    }

    @Test
    public void lowerBowHasIndependentPlacement() {
        var gray = paper();
        curve(gray, 40, 190, 188, 0, -14, false);
        assertEquals(1, find(gray).size());
        assertFalse(find(gray).get(0).above());
    }

    @Test
    public void straightBeamAndHairpinAreRejected() {
        var gray = paper();
        curve(gray, 40, 190, 55, 8, 0, false);
        assertTrue(find(gray).isEmpty());
        gray = paper();
        curve(gray, 40, 190, 55, 0, 14, true);
        assertTrue(find(gray).isEmpty());
    }

    @Test
    public void closedCounterContourDoesNotBecomeTwoPhrasingArcs() {
        var gray = paper();
        for (int x = 40; x <= 190; x++) {
            double t = 2.0 * (x - 40) / 150 - 1;
            int rise = (int) Math.round(14 * Math.sqrt(Math.max(0, 1 - t * t)));
            gray[(65 - rise) * W + x] = 0;
            gray[(65 + rise) * W + x] = 0;
        }
        assertTrue(find(gray).isEmpty());
    }

    @Test
    public void oneRasterColumnGapIsAllowedButTwoDoNotInventContinuity() {
        var gray = paper();
        curve(gray, 40, 190, 55, 0, 14, false);
        for (int y = 0; y < H; y++) gray[y * W + 115] = (byte) 255;
        assertEquals(1, find(gray).size());
        for (int y = 0; y < H; y++) gray[y * W + 116] = (byte) 255;
        // Two halves may remain raw curved fragments, but cannot claim the original two caps.
        for (var bow : find(gray)) assertFalse(bow.start().x() == 40 && bow.end().x() == 190);
    }

    @Test
    public void semanticNoteheadsCannotSupplyABow() {
        var gray = paper();
        curve(gray, 40, 190, 55, 0, 14, false);
        var labels = new byte[gray.length];
        for (int i = 0; i < gray.length; i++)
            if (gray[i] == 0) labels[i] = OmrMeasurePostProcessor.NOTEHEAD;
        assertTrue(
                PrintedSlurContours.find(gray, labels, W, H, 0, 20, W - 1, H - 1, staff())
                        .bows()
                        .isEmpty());
    }

    @Test
    public void thickBeamedStrokeCannotSupplyABow() {
        var gray = paper();
        for (int x = 40; x <= 190; x++) {
            double t = 2.0 * (x - 40) / 150 - 1;
            int center = (int) Math.round(55 + 14 * t * t);
            for (int y = center - 3; y <= center + 3; y++) gray[y * W + x] = 0;
        }
        assertTrue(find(gray).isEmpty());
    }

    @Test
    public void unverifiedStaffGeometryDoesNotCreatePrintedBowEvidence() {
        var gray = paper();
        curve(gray, 40, 190, 55, 0, 14, false);
        assertTrue(
                PrintedSlurContours.find(
                                gray,
                                null,
                                W,
                                H,
                                0,
                                20,
                                W - 1,
                                H - 1,
                                StaffPitchTrack.linear(W, 150, 12, 0))
                        .bows()
                        .isEmpty());
    }

    @Test
    public void rawFiveLineRailsDoNotBecomeSlurs() {
        var gray = paper();
        for (int line = 0; line < 5; line++)
            for (int x = 10; x < 230; x++) gray[(150 - line * 12) * W + x] = 0;
        assertTrue(find(gray).isEmpty());
    }

    @Test
    public void nearbyUnconnectedDigitCannotMergeIntoAnOtherwiseContinuousBow() {
        var gray = paper();
        curve(gray, 40, 190, 55, 0, 14, false);
        for (int x = 110; x <= 122; x++) gray[62 * W + x] = 0;
        for (int y = 63; y <= 70; y++) gray[y * W + 118] = 0;
        var bows = find(gray);
        assertEquals(1, bows.size());
        assertEquals(40, bows.get(0).start().x());
        assertEquals(190, bows.get(0).end().x());
    }

    @Test
    public void actualDarkStaffCrossingsPreserveBothOriginalArcCaps() {
        var gray = paper();
        for (int line = 0; line < 5; line++)
            for (int x = 0; x < W; x++) gray[(150 - line * 12) * W + x] = 0;
        curve(gray, 40, 190, 70, 0, 50, false);
        var bows = find(gray);
        assertTrue(bows.stream().anyMatch(b -> b.start().x() == 40 && b.end().x() == 190));
    }

    @Test
    public void absentCrossingInkCannotBeReplacedByTheVerifiedFrameAlone() {
        var gray = paper();
        curve(gray, 40, 190, 70, 0, 50, false);
        for (int x = 53; x <= 57; x++) for (int y = 90; y < 112; y++) gray[y * W + x] = (byte) 255;
        assertFalse(find(gray).stream().anyMatch(b -> b.start().x() == 40 && b.end().x() == 190));
    }

    @Test
    public void steepEllipticalTailRemainsThinInItsTransverseDirection() {
        var gray = paper();
        int previous = 85;
        for (int x = 40; x <= 190; x++) {
            double t = 2.0 * (x - 40) / 150 - 1;
            int y = (int) Math.round(90 + 5 * t - 30 * Math.sqrt(Math.max(0, 1 - t * t)));
            for (int row = Math.min(y, previous); row <= Math.max(y, previous); row++)
                gray[row * W + x] = 0;
            previous = y;
        }
        var bows = find(gray);
        assertTrue(
                bows.stream()
                        .anyMatch(b -> b.start().x() == 40 && b.end().x() == 190 && b.above()));
    }

    @Test
    public void smallTerminalAttachmentDoesNotEraseTheProvedCentralBow() {
        var gray = paper();
        curve(gray, 40, 110, 55, 0, 14, false);
        for (int x = 45; x <= 50; x++) {
            double t = 2.0 * (x - 40) / 70 - 1;
            gray[((int) Math.round(55 + 14 * t * t) + 4) * W + x] = 0;
        }
        assertTrue(find(gray).stream().anyMatch(b -> b.start().x() == 40 && b.end().x() == 110));
    }

    @Test
    public void competingCentralContourCannotBorrowTheTerminalAttachmentException() {
        var gray = paper();
        curve(gray, 40, 110, 55, 0, 14, false);
        for (int x = 71; x <= 76; x++) {
            double t = 2.0 * (x - 40) / 70 - 1;
            gray[((int) Math.round(55 + 14 * t * t) + 4) * W + x] = 0;
        }
        assertTrue(find(gray).isEmpty());
    }

    @Test
    public void rasterRoundedRailsDoNotClipAShortReturningArc() {
        var gray = paper();
        for (int line = 0; line < 5; line++)
            for (int x = 0; x < W; x++) {
                int row = 150 - line * 12 + (x / 9 % 2);
                gray[row * W + x] = 0;
            }
        for (int x = 60; x <= 110; x++) {
            double t = 2.0 * (x - 60) / 50 - 1;
            int row = (int) Math.round(101 + 6 * t + 13 * t * t);
            for (int dy = -1; dy <= 1; dy++) gray[(row + dy) * W + x] = 0;
        }
        assertTrue(find(gray).stream().anyMatch(b -> b.start().x() == 60 && b.end().x() == 110));
    }

    @Test
    public void rasterRoundedRailsAloneNeverSupplyABow() {
        var gray = paper();
        for (int line = 0; line < 5; line++)
            for (int x = 0; x < W; x++) gray[(150 - line * 12 + (x / 9 % 2)) * W + x] = 0;
        assertTrue(find(gray).isEmpty());
    }
}
