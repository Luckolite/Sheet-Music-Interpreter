// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Whole pixel-cell extent, unchanged numeric curve gates, and complete endpoint competition. */
public class SlurPixelCellSupportTest {
    static StaffPitchTrack track() {
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {0, 200, 14}, new float[] {299, 200, 14}));
    }

    static List<PrintedSlurContours.ForeignRun> profile(int kind) {
        var c = new ArrayList<PrintedSlurContours.ForeignRun>();
        for (int x = 100; x <= 122; x++) {
            double t = (x - 100) / 22.;
            double y =
                    kind == 0
                            ? 101 + 2 * t + 18 * t * (1 - t)
                            : kind == 1 ? 100 + 8 * t : 100 + 12 * (.5 - Math.abs(t - .5));
            int row = (int) Math.round(y);
            c.add(
                    new PrintedSlurContours.ForeignRun(
                            x, List.of(new PrintedSlurContours.RawBand(row - 1, row + 1))));
        }
        return c;
    }

    static final class Scene {
        final int w = 300, h = 240;
        final byte[] gray = new byte[w * h];
        final List<PrintedSlurOwners.GeometryHead> heads = new ArrayList<>();
        final PrintedSlurContours.Bow bow;

        Scene(float endpointHead, boolean body) {
            Arrays.fill(gray, (byte) 255);
            for (int x = 4; x < w - 4; x++)
                for (int i = 0; i < 5; i++) gray[(200 - 14 * i) * w + x] = 40;
            head(0, 160, 113, true);
            if (body) head(1, endpointHead, 114, false);
            else heads.add(new PrintedSlurOwners.GeometryHead(1, endpointHead, 114, 0, 1));
            var p = new ArrayList<PrintedSlurContours.Point>();
            for (int x = 160; x <= 190; x++) {
                double t = (x - 160) / 30.;
                float y = (float) (126 + 2 * t + 16 * t * (1 - t));
                p.add(new PrintedSlurContours.Point(x, y));
                gray[Math.round(y) * w + x] = 0;
            }
            bow = new PrintedSlurContours.Bow(p, false, .3f, 0);
        }

        void head(int owner, float x, float y, boolean grace) {
            heads.add(new PrintedSlurOwners.GeometryHead(owner, x, y, 0, 1, grace));
            for (int yy = (int) y - 5; yy <= y + 5; yy++)
                for (int xx = (int) x - 8; xx <= x + 8; xx++)
                    if (Math.pow((xx - x) / 7., 2) + Math.pow((yy - y) / 4.5, 2) <= 1)
                        gray[yy * w + xx] = 0;
        }

        PrintedSlurOwners.Binding old() {
            return PrintedSlurOwners.bind("test", 0, bow, heads, gray, w, h, track());
        }

        PrintedSlurOwners.Binding cells() {
            return PrintedSlurOwners.bindPixelCells("test", 0, bow, heads, gray, w, h, track());
        }
    }

    @Test
    public void fullCellExtentMeetsTheSamePhysicalMinimum() {
        var p = profile(0);
        int[] low = new int[p.size()], high = new int[p.size()];
        for (int i = 0; i < p.size(); i++) {
            low[i] = p.get(i).members().get(0).first();
            high[i] = p.get(i).members().get(0).last();
        }
        assertFalse(PrintedSlurContours.completeReturningProfile(low, high, 0, p.size() - 1, 14));
        assertNotNull(PrintedSlurContours.fitPixelCells(p, track()));
    }

    @Test
    public void straightPixelFieldCannotGainCurvature() {
        assertNull(PrintedSlurContours.fitPixelCells(profile(1), track()));
    }

    @Test
    public void sharpHingeCannotBorrowRoundedCellSupport() {
        assertNull(PrintedSlurContours.fitPixelCells(profile(2), track()));
    }

    @Test
    public void allBandsInAColumnRemainCompeting() {
        var p = profile(0);
        p.set(
                10,
                new PrintedSlurContours.ForeignRun(
                        110,
                        List.of(
                                new PrintedSlurContours.RawBand(104, 106),
                                new PrintedSlurContours.RawBand(110, 112))));
        assertNull(PrintedSlurContours.fitPixelCells(p, track()));
    }

    @Test
    public void duplicateColumnsAreNotSilentlySelected() {
        var p = profile(0);
        p.add(10, p.get(10));
        assertNull(PrintedSlurContours.fitPixelCells(p, track()));
    }

    @Test
    public void observedEndpointCellCanReachAdjacentHeadBoundary() {
        var s = new Scene(201.95f, true);
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, s.old().status());
        assertEquals(PrintedSlurOwners.Status.GEOMETRY_OWNED, s.cells().status());
    }

    @Test
    public void headBeyondTheWholeCellStillRefuses() {
        var s = new Scene(203, true);
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, s.cells().status());
    }

    @Test
    public void absentRawHeadCannotBorrowCellPhase() {
        var s = new Scene(201.95f, false);
        assertEquals(PrintedSlurOwners.Status.NO_END_OWNER, s.cells().status());
    }

    @Test
    public void everyReachableRawHeadParticipatesInAmbiguity() {
        var s = new Scene(201.95f, true);
        s.head(2, 202.1f, 114, false);
        assertEquals(PrintedSlurOwners.Status.AMBIGUOUS_END, s.cells().status());
    }

    @Test
    public void oldAcceptedHeadAndEvidenceStayExact() {
        var s = new Scene(201.7f, true);
        assertEquals(PrintedSlurOwners.Status.GEOMETRY_OWNED, s.old().status());
        assertEquals(s.old(), s.cells());
    }

    @Test
    public void reorderedHeadEvidenceCannotBecomeFirstWins() {
        var s = new Scene(201.95f, true);
        var before = s.cells();
        Collections.reverse(s.heads);
        assertEquals(before, s.cells());
    }

    @Test
    public void cellBindingStillHonorsActualClockPitchAndTies() {
        var s = new Scene(201.95f, true);
        var a = new PrintedSlurOwners.Head(0, 160, 113, 0, 1, 0, 1000, 60);
        var b = new PrintedSlurOwners.Head(1, 201.95f, 114, 0, 1, 0, 2000, 62);
        var c = new PrintedSlurOwners.Head(1, 201.95f, 114, 0, 1, 0, 2000, 60);
        var tie = Set.of(new PrintedSlurOwners.Tie(0, 1));
        assertEquals(
                PrintedSlurOwners.Status.TIE_PITCH_CONFLICT,
                PrintedSlurOwners.resolvePixelCells(
                                "test", 0, s.bow, List.of(a, b), tie, s.gray, s.w, s.h, track())
                        .status());
        assertEquals(
                PrintedSlurOwners.Status.TIE_ALREADY_RETAINED,
                PrintedSlurOwners.resolvePixelCells(
                                "test", 0, s.bow, List.of(a, c), tie, s.gray, s.w, s.h, track())
                        .status());
        assertEquals(
                PrintedSlurOwners.Status.SAME_PITCH_UNRESOLVED,
                PrintedSlurOwners.resolvePixelCells(
                                "test",
                                0,
                                s.bow,
                                List.of(a, c),
                                Set.of(),
                                s.gray,
                                s.w,
                                s.h,
                                track())
                        .status());
    }
}
