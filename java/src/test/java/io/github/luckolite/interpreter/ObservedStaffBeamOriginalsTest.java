// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent complete beams crossing an independently visible five-rule carrier. */
public class ObservedStaffBeamOriginalsTest {
    private static final class Field {
        final ObservedBeamSlurOriginalsTest.Field f;
        final int left;
        final StaffPitchTrack track;

        Field(boolean above, int scale, float phase, boolean beyondSample) {
            f = new ObservedBeamSlurOriginalsTest.Field(2, above, scale, phase);
            left = above ? 120 : 100;
            for (int x = (left + 2) * scale; x < (left + 64) * scale; x++)
                for (int y = 150 * scale; y <= 198 * scale; y++)
                    f.gray[f.physical(y) * f.w + x] = (byte) 255;
            for (int x = (left + 2) * scale; x < (left + 64) * scale; x++) {
                double t = (x - left * (double) scale) / (64 * scale);
                for (int rail = 0; rail < 2; rail++) {
                    double end = (190 - 8 * t - 12 * rail) * scale + phase;
                    for (int y = (int) Math.round(end - 7 * scale); y <= Math.round(end); y++)
                        f.gray[f.physical(y) * f.w + x] = 0;
                }
            }
            rules(5);
            float bottom = above ? f.physical(100 * scale + phase) : 164 * scale + phase;
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, bottom, 16 * scale},
                                    new float[] {
                                        beyondSample ? (left - 5) * scale : f.w - 1,
                                        bottom,
                                        16 * scale
                                    }));
            var points = new ArrayList<PrintedSlurContours.Point>();
            for (int x = (left + 2) * scale; x < (left + 64) * scale; x++) {
                double t = (x - left * (double) scale) / (64 * scale);
                float y = (float) ((174.5 - 8 * t) * scale + phase);
                points.add(new PrintedSlurContours.Point(x, f.physical(y)));
            }
            f.bow = new PrintedSlurContours.Bow(points, above, .3f, 0);
        }

        void rules(int count) {
            int s = f.s;
            for (int line = 0; line < count; line++) {
                int center = Math.round((100 + 16 * line) * s + f.phase);
                for (int x = 10; x < f.w - 10; x++)
                    for (int y = center - s; y <= center + s; y++)
                        f.gray[f.physical(y) * f.w + x] =
                                (byte) Math.min(f.gray[f.physical(y) * f.w + x] & 255, 60);
            }
        }

        boolean owned() {
            byte[] before = f.gray.clone();
            var notes = List.copyOf(f.notes);
            boolean result =
                    new ObservedStemBeam(f.notes, f.gray, f.w, f.h)
                            .owns(f.bow, f.span, f.heads, track);
            assertArrayEquals(before, f.gray);
            assertEquals(notes, f.notes);
            return result;
        }

        void erase(int l, int r, int top, int bottom) {
            f.erase(l, r, top, bottom);
        }
    }

    @Test
    public void lowerSecondaryBeamMayShareInkWithAProvedRule() {
        assertTrue(new Field(false, 1, 0, false).owned());
    }

    @Test
    public void upperSecondaryBeamUsesTheSameRawPartition() {
        assertTrue(new Field(true, 1, 0, false).owned());
    }

    @Test
    public void freshFiveRulesMayProveBeyondTheOldSampleExtent() {
        var f = new Field(true, 1, 0, true);
        assertFalse(f.track.verifiedAt((f.left + 30) * f.f.s));
        assertTrue(f.owned());
    }

    @Test
    public void lowerFreshProofDoesNotExtendTheOldTrack() {
        var f = new Field(false, 1, 0, true);
        assertTrue(f.owned());
        assertFalse(f.track.verifiedAt(f.left + 30));
    }

    @Test
    public void doubleDensityLowerPartitionUsesTheSameInkRoles() {
        assertTrue(new Field(false, 2, 0, false).owned());
    }

    @Test
    public void doubleDensityUpperPartitionUsesTheSameInkRoles() {
        assertTrue(new Field(true, 2, 0, false).owned());
    }

    @Test
    public void tripleDensityLowerPartitionRemainsBounded() {
        assertTrue(new Field(false, 3, 0, false).owned());
    }

    @Test
    public void tripleDensityUpperPartitionRemainsBounded() {
        assertTrue(new Field(true, 3, 0, false).owned());
    }

    @Test
    public void quarterPixelPhaseRetainsTheCompletePartition() {
        assertTrue(new Field(false, 1, .25f, false).owned());
    }

    @Test
    public void halfPixelPhaseRetainsTheCompletePartition() {
        assertTrue(new Field(true, 1, .5f, false).owned());
    }

    @Test
    public void missingFifthRuleCannotBeSuppliedByAnExtrapolatedTrack() {
        var f = new Field(true, 1, 0, true);
        f.erase(f.left - 32, f.left - 9, 99, 101);
        assertFalse(f.owned());
    }

    @Test
    public void aOneSidedCarrierCannotDonateItsBoundary() {
        var f = new Field(false, 1, 0, false);
        f.erase(f.left + 74, f.left + 95, 163, 165);
        assertFalse(f.owned());
    }

    @Test
    public void aBrokenCarrierCannotBeInterpolatedThroughPaper() {
        var f = new Field(false, 1, 0, false);
        f.erase(f.left - 5, f.left - 5, 163, 165);
        assertFalse(f.owned());
    }

    @Test
    public void outsideBeamForegroundCannotBeAssignedToTheStaff() {
        var f = new Field(false, 1, 0, false);
        for (int y = 157; y <= 172; y++) f.f.gray[f.f.physical(y) * f.f.w + f.left + 30] = 0;
        assertFalse(f.owned());
    }

    @Test
    public void aGenuineArchOutsideBothRolesRemainsASlur() {
        var f = new Field(false, 1, 0, false);
        f.f.proposal(1, 18);
        assertFalse(f.owned());
    }

    @Test
    public void rawBeamColumnLossCannotBeFilledByTheRule() {
        var f = new Field(true, 1, 0, false);
        f.erase(f.left + 32, f.left + 32, 166, 179);
        assertFalse(f.owned());
    }

    @Test
    public void aForeignHeadAtTheCarrierMakesTheRoleAmbiguous() {
        var f = new Field(false, 1, 0, false);
        f.f.heads.add(new PrintedSlurOwners.GeometryHead(2, f.left + 30, 164, 0, 1));
        assertFalse(f.owned());
    }

    @Test
    public void rawCarrierEdgesCannotChangeInsideTheReferenceWindows() {
        var f = new Field(false, 1, 0, false);
        f.erase(f.left - 25, f.left - 20, 163, 163);
        assertFalse(f.owned());
    }

    @Test
    public void aCarrierOnlyPathCannotBorrowTheNearbyBeam() {
        var f = new Field(false, 1, 0, false);
        var points = new ArrayList<PrintedSlurContours.Point>();
        for (int x = f.left + 2; x < f.left + 64; x++)
            points.add(new PrintedSlurContours.Point(x, 164));
        f.f.bow = new PrintedSlurContours.Bow(points, false, .3f, 0);
        assertFalse(f.owned());
    }

    @Test
    public void aCurvedSecondaryContourCannotBecomeAParallelBeam() {
        var f = new Field(false, 1, 0, false);
        var points = new ArrayList<PrintedSlurContours.Point>();
        for (int x = f.left + 2; x < f.left + 64; x++) {
            double t = (x - f.left) / 64.;
            int oldEnd = (int) Math.round(178 - 8 * t);
            for (int y = oldEnd - 7; y <= oldEnd; y++)
                f.f.gray[y * f.f.w + x] = (byte) (y >= 163 && y <= 165 ? 60 : 255);
            double end = 178 - 8 * t - 6 * 4 * t * (1 - t);
            for (int y = (int) Math.round(end - 7); y <= Math.round(end); y++)
                f.f.gray[y * f.f.w + x] = 0;
            points.add(new PrintedSlurContours.Point(x, (float) (end - 3.5)));
        }
        f.f.bow = new PrintedSlurContours.Bow(points, false, .4f, 0);
        assertFalse(f.owned());
    }

    @Test
    public void isolatedFiveRuleColumnsDoNotProveACompleteReferenceGroup() {
        var f = new Field(true, 1, 0, true);
        for (int x = f.left - 32; x <= f.left - 9; x += 2) f.erase(x, x, 99, 101);
        assertFalse(f.owned());
    }

    @Test
    public void insufficientPartitionBudgetCannotIssuePartialOwnership() {
        var f = new Field(false, 1, 0, false);
        byte[] before = f.f.gray.clone();
        assertFalse(
                new ObservedStemBeam(f.f.notes, f.f.gray, f.f.w, f.f.h, 700_000L)
                        .owns(f.f.bow, f.f.span, f.f.heads, f.track));
        assertArrayEquals(before, f.f.gray);
    }
}
