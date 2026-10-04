// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class RawStaffLineDetectorTest {
    @Test
    public void periodicBroadShadingDoesNotInventFiveLineStaffs() {
        int width = 600, height = 240;
        byte[] gray = new byte[width * height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                gray[y * width + x] = (byte) (155 + x / 20 + (y % 8) / 2);
        assertEquals(0, RawStaffLineDetector.detect(gray, width, height).size());
    }

    @Test
    public void faintShortStaffSurvivesOnShadedPaper() {
        int width = 2048, height = 240;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 175);
        int[] rows = {80, 94, 108, 121, 135};
        for (int row : rows) for (int x = 275; x < 810; x++) gray[row * width + x] = (byte) 150;
        var staffs = RawStaffLineDetector.detect(gray, width, height);
        assertEquals(1, staffs.size());
        assertArrayEquals(rows, staffs.get(0).rows());
    }

    @Test
    public void shortStaffWithFractionalPixelSpacingStillHasFiveRules() {
        int width = 2048, height = 240;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        int[] rows = {80, 94, 108, 121, 135}; // 13.75-pixel engraving rounded to the raster grid.
        for (int row : rows) for (int x = 275; x < 810; x++) gray[row * width + x] = 0;
        var staffs = RawStaffLineDetector.detect(gray, width, height);
        assertEquals(1, staffs.size());
        assertArrayEquals(rows, staffs.get(0).rows());
    }

    @Test
    public void denseBeamCannotCollapseSeveralStaffLinesIntoOneBand() {
        int width = 600, height = 220, top = 80, gap = 8;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 0xff);
        for (int line = 0; line < 5; line++)
            for (int x = 60; x <= 540; x++) gray[(top + line * gap) * width + x] = 0;

        // A broad beam dark enough to pass the page-wide line threshold joins three staff-line
        // projection bands. The old contiguous-run detector emitted only three line rows here.
        for (int y = top + gap; y <= top + gap * 3; y++)
            for (int x = 120; x <= 480; x++) gray[y * width + x] = 0;

        List<RawStaffLineDetector.StaffLines> staffs =
                RawStaffLineDetector.detect(gray, width, height);

        assertEquals(1, staffs.size());
        assertArrayEquals(new int[] {80, 88, 96, 104, 112}, staffs.get(0).rows());
    }

    @Test
    public void nearbyNotationPeakDoesNotShiftAnOtherwiseRegularStaff() {
        int width = 600, height = 220, top = 80, gap = 8;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 0xff);
        for (int line = 0; line < 5; line++)
            for (int x = 60; x <= 540; x++) gray[(top + line * gap) * width + x] = 0;
        for (int x = 180; x <= 420; x++) gray[71 * width + x] = 0;

        List<RawStaffLineDetector.StaffLines> staffs =
                RawStaffLineDetector.detect(gray, width, height);

        assertEquals(1, staffs.size());
        assertArrayEquals(new int[] {80, 88, 96, 104, 112}, staffs.get(0).rows());
    }

    @Test
    public void reducedStaffRequiresAVisibleConnectionToTheNormalStaffs() {
        int width = 600, height = 650;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        for (int top : new int[] {160, 320, 460})
            for (int line = 0; line < 5; line++)
                for (int x = 60; x <= 540; x++) gray[(top + line * 10) * width + x] = 0;
        for (int line = 0; line < 5; line++)
            for (int x = 60; x <= 540; x++) gray[(50 + line * 6) * width + x] = 0;
        assertEquals(
                "Unconnected narrow bands are rejected",
                3,
                RawStaffLineDetector.detect(gray, width, height).size());
        for (int y = 50; y <= 200; y++) gray[y * width + 60] = 0;
        assertEquals(
                "The system rule validates the cue-sized part",
                4,
                RawStaffLineDetector.detect(gray, width, height).size());
    }

    @Test
    public void repeatedMiniatureBeamPatternsCannotBecomeExtraStaffs() {
        int width = 600, height = 760;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 0xff);
        for (int staff = 0; staff < 6; staff++) {
            int top = 40 + staff * 90;
            for (int line = 0; line < 5; line++)
                for (int x = 45; x <= 555; x++) gray[(top + line * 8) * width + x] = 0;
        }
        for (int beam = 0; beam < 3; beam++) {
            int top = 590 + beam * 50;
            for (int line = 0; line < 5; line++)
                for (int x = 90; x <= 510; x++) gray[(top + line * 3) * width + x] = 0;
        }

        List<RawStaffLineDetector.StaffLines> staffs =
                RawStaffLineDetector.detect(gray, width, height);

        assertEquals(6, staffs.size());
        for (RawStaffLineDetector.StaffLines staff : staffs) assertEquals(8f, staff.gap(), .001f);
    }

    @Test
    public void connectionSearchReusesOnlyItsCurrentStaffAndRaster() {
        int width = 120, height = 240;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) 255);
        int[] aboveRows = {20, 30, 40, 50, 60};
        int[] cueRows = {100, 106, 112, 118, 124};
        int[] belowRows = {160, 170, 180, 190, 200};
        for (int[] rows : new int[][] {aboveRows, cueRows, belowRows})
            for (int y : rows) for (int x = 40; x < 100; x++) gray[y * width + x] = 0;
        var above = new RawStaffLineDetector.StaffLines(aboveRows, 10f);
        var cue = new RawStaffLineDetector.StaffLines(cueRows, 6f);
        var below = new RawStaffLineDetector.StaffLines(belowRows, 10f);
        var candidates = List.of(above, cue, below);
        byte[] unconnected = gray.clone();
        org.junit.Assert.assertFalse(
                RawStaffLineDetector.connectedToStaff(cue, candidates, gray, width, height));
        assertArrayEquals(unconnected, gray);

        // The first eligible pair has the cue below its peer. Only the second pair, with the
        // cue above its peer, has a connector; reuse must not exchange either peer's edge.
        for (int y = 124; y <= 200; y++) gray[y * width + 40] = 0;
        byte[] connected = gray.clone();
        org.junit.Assert.assertTrue(
                RawStaffLineDetector.connectedToStaff(cue, candidates, gray, width, height));
        assertArrayEquals(connected, gray);
        assertArrayEquals(new int[] {20, 30, 40, 50, 60}, aboveRows);
        assertArrayEquals(new int[] {100, 106, 112, 118, 124}, cueRows);
        assertArrayEquals(new int[] {160, 170, 180, 190, 200}, belowRows);
        org.junit.Assert.assertSame(cueRows, cue.rows());

        // A later invocation must read its own raster, with no retained missing/positive edge.
        System.arraycopy(unconnected, 0, gray, 0, gray.length);
        org.junit.Assert.assertFalse(
                RawStaffLineDetector.connectedToStaff(cue, candidates, gray, width, height));
        assertArrayEquals(unconnected, gray);
    }

    @Test
    public void connectionSearchStillReadsBothEdgesBeforeReturningAConnection() {
        int width = 120, height = 240;
        byte[] gray = new byte[width * 150];
        Arrays.fill(gray, (byte) 255);
        var upper = new RawStaffLineDetector.StaffLines(new int[] {20, 30, 40, 50, 60}, 10f);
        var malformedLower =
                new RawStaffLineDetector.StaffLines(new int[] {160, 170, 180, 190, 200}, 10f);
        for (int y : upper.rows()) for (int x = 40; x < 100; x++) gray[y * width + x] = 0;
        for (int y = 60; y < 150; y++) gray[y * width + 40] = 0;
        byte[] original = gray.clone();
        try {
            RawStaffLineDetector.connectedToStaff(
                    upper, List.of(upper, malformedLower), gray, width, height);
            org.junit.Assert.fail("The malformed second edge must retain its array read failure");
        } catch (ArrayIndexOutOfBoundsException expected) {
            assertArrayEquals(original, gray);
            assertArrayEquals(new int[] {20, 30, 40, 50, 60}, upper.rows());
            assertArrayEquals(new int[] {160, 170, 180, 190, 200}, malformedLower.rows());
        }
    }
}
