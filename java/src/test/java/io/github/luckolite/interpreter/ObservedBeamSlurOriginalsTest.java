// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent complete beam strips, filled heads and literal shafts prove a distinct role. */
public class ObservedBeamSlurOriginalsTest {
    static final class Field {
        final int w, h, s, count, direction;
        final float gap, phase;
        final byte[] gray;
        final StaffPitchTrack track;
        final ArrayList<ScoreNoteEvent> notes = new ArrayList<>();
        final ArrayList<PrintedSlurOwners.GeometryHead> heads = new ArrayList<>();
        final ScoreSlurPages.Span span;
        PrintedSlurContours.Bow bow;

        Field(int count, boolean above, int s, float phase) {
            this.count = count;
            this.s = s;
            this.phase = phase;
            direction = above ? -1 : 1;
            gap = 16 * s;
            w = 320 * s;
            h = 360 * s;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            for (int owner = 0; owner < 2; owner++) {
                float x = (owner == 0 ? 110 : 174) * s,
                        y = physical((owner == 0 ? 120 : 110) * s + phase);
                heads.add(new PrintedSlurOwners.GeometryHead(owner, x, y, 0, 1));
                notes.add(note(owner, count, -direction, 0, 0, 1));
                for (int yy = Math.round(y - 6 * s); yy <= y + 6 * s; yy++)
                    for (int xx = Math.round(x - 9 * s); xx <= x + 9 * s; xx++)
                        if (Math.pow((xx - x) / (8. * s), 2) + Math.pow((yy - y) / (5. * s), 2)
                                <= 1) gray[yy * w + xx] = 0;
                int shaft = ((above ? 120 : 100) + owner * 64) * s;
                int start = (owner == 0 ? 120 : 110) * s;
                int end = Math.round((owner == 0 ? 190 : 186) * s + phase);
                for (int xx = shaft; xx < shaft + 2 * s; xx++)
                    for (int yy = start; yy <= end; yy++) gray[physical(yy) * w + xx] = 0;
            }
            beam(false);
            proposal(0, 0);
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, 280 * s, gap},
                                    new float[] {w - 1, 280 * s, gap}));
            span =
                    new ScoreSlurPages.Span(
                            "original-complete-beam",
                            new ScoreSlurPages.Anchor(0, 0),
                            new ScoreSlurPages.Anchor(0, 1),
                            above ? ScoreSlurSpan.Placement.ABOVE : ScoreSlurSpan.Placement.BELOW,
                            ScoreSlurSpan.LineType.SOLID,
                            List.of(
                                    new ScoreSlurSpan.ArcEvidence(
                                            0,
                                            bow.start().x() / (float) w,
                                            bow.start().y() / h,
                                            bow.end().x() / (float) w,
                                            bow.end().y() / h)));
        }

        int physical(int y) {
            return direction > 0 ? y : h - 1 - y;
        }

        float physical(float y) {
            return direction > 0 ? y : h - 1 - y;
        }

        ScoreNoteEvent note(
                int owner, int beams, int stem, int measure, int staff, int staffCount) {
            var head = heads.get(owner);
            return new ScoreNoteEvent(
                            measure,
                            head.x() / w,
                            owner + 10,
                            staff,
                            staffCount,
                            head.y() / h,
                            false,
                            0,
                            beams,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            0)
                    .withStemDirection(stem);
        }

        void beam(boolean curved) {
            int left = direction < 0 ? 120 : 100;
            for (int x = left * s; x <= (left + 65) * s; x++) {
                double t = (x - left * (double) s) / (64 * s);
                for (int rail = 0; rail < count; rail++) {
                    double end =
                            (190 - 4 * t - 12 * rail - (curved ? 12 * 4 * t * (1 - t) : 0)) * s
                                    + phase;
                    int first = (int) Math.round(end - 7 * s), last = (int) Math.round(end);
                    for (int y = first; y <= last; y++) gray[physical(y) * w + x] = 20;
                }
            }
        }

        void proposal(int rail, float bend) {
            int left = direction < 0 ? 120 : 100;
            var points = new ArrayList<PrintedSlurContours.Point>();
            for (int x = left * s; x <= (left + 64) * s; x++) {
                double t = (x - left * (double) s) / (64 * s);
                float y = (float) ((187 - 4 * t - 12 * rail - bend * 4 * t * (1 - t)) * s + phase);
                points.add(new PrintedSlurContours.Point(x, physical(y)));
            }
            bow = new PrintedSlurContours.Bow(points, direction < 0, .3f, 0);
        }

        boolean owned() {
            return owned(8_000_000L);
        }

        boolean owned(long budget) {
            byte[] before = gray.clone();
            var previous = List.copyOf(notes);
            boolean result =
                    new ObservedStemBeam(notes, gray, w, h, budget).owns(bow, span, heads, track);
            assertArrayEquals(before, gray);
            assertEquals(previous, notes);
            return result;
        }

        void erase(int left, int right, int top, int bottom) {
            for (int x = left * s; x <= right * s; x++)
                for (int y = top * s; y <= bottom * s; y++) gray[physical(y) * w + x] = (byte) 255;
        }
    }

    @Test
    public void completeLowerSingleBeamOwnsItsPixels() {
        assertTrue(new Field(1, false, 1, 0).owned());
    }

    @Test
    public void completeUpperSingleBeamUsesMirroredLiteralProof() {
        assertTrue(new Field(1, true, 1, 0).owned());
    }

    @Test
    public void twoCompleteParallelBeamsOwnTheirSecondaryBand() {
        var f = new Field(2, false, 1, 0);
        f.proposal(1, 0);
        assertTrue(f.owned());
    }

    @Test
    public void upperSecondaryBeamUsesSameParallelAuthority() {
        var f = new Field(2, true, 1, 0);
        f.proposal(1, 0);
        assertTrue(f.owned());
    }

    @Test
    public void threeCompleteBeamsKeepTheirExactBandIdentity() {
        var f = new Field(3, false, 1, 0);
        f.proposal(2, 0);
        assertTrue(f.owned());
    }

    @Test
    public void doubleDensityRetainsCompleteContact() {
        assertTrue(new Field(1, false, 2, 0).owned());
    }

    @Test
    public void tripleDensityRetainsCompleteContact() {
        assertTrue(new Field(1, true, 3, 0).owned());
    }

    @Test
    public void halfPixelEdgePhaseRetainsLiteralContact() {
        assertTrue(new Field(1, false, 1, .5f).owned());
    }

    @Test
    public void noBeamMetadataCannotBeReplacedByProximity() {
        var f = new Field(1, false, 1, 0);
        f.notes.set(0, f.note(0, 0, -1, 0, 0, 1));
        assertFalse(f.owned());
    }

    @Test
    public void inconsistentBeamCountsRemainUnknown() {
        var f = new Field(1, false, 1, 0);
        f.notes.set(1, f.note(1, 2, -1, 0, 0, 1));
        assertFalse(f.owned());
    }

    @Test
    public void contraryStemDirectionRemainsUnknown() {
        var f = new Field(1, false, 1, 0);
        f.notes.set(1, f.note(1, 1, 1, 0, 0, 1));
        assertFalse(f.owned());
    }

    @Test
    public void unknownStemDirectionRemainsUnknown() {
        var f = new Field(1, false, 1, 0);
        f.notes.set(1, f.note(1, 1, 0, 0, 0, 1));
        assertFalse(f.owned());
    }

    @Test
    public void differentMeasureCannotBorrowThisBeam() {
        var f = new Field(1, false, 1, 0);
        f.notes.set(1, f.note(1, 1, -1, 1, 0, 1));
        assertFalse(f.owned());
    }

    @Test
    public void differentStaffCannotBorrowThisBeam() {
        var f = new Field(1, false, 1, 0);
        f.notes.set(1, f.note(1, 1, -1, 0, 1, 2));
        assertFalse(f.owned());
    }

    @Test
    public void crossStaffBeamNeedsItsOwnTypedProof() {
        var f = new Field(1, false, 1, 0);
        f.notes.set(0, f.notes.get(0).withCrossStaffBeam());
        assertFalse(f.owned());
    }

    @Test
    public void missingRawHeadCannotBeDonatedByMetadata() {
        var f = new Field(1, false, 1, 0);
        f.erase(102, 118, 114, 126);
        assertFalse(f.owned());
    }

    @Test
    public void brokenShaftCannotBorrowAOneRowGap() {
        var f = new Field(1, false, 1, 0);
        f.erase(100, 101, 150, 150);
        assertFalse(f.owned());
    }

    @Test
    public void absentBeamColumnCannotBeInterpolated() {
        var f = new Field(1, false, 1, 0);
        f.erase(132, 132, 165, 195);
        assertFalse(f.owned());
    }

    @Test
    public void missingSecondaryBeamCannotBeInvented() {
        var f = new Field(1, false, 1, 0);
        f.notes.set(0, f.note(0, 2, -1, 0, 0, 1));
        f.notes.set(1, f.note(1, 2, -1, 0, 0, 1));
        assertFalse(f.owned());
    }

    @Test
    public void realArcOutsideTheBeamRetainsItsRole() {
        var f = new Field(1, false, 1, 0);
        f.proposal(0, 18);
        assertFalse(f.owned());
    }

    @Test
    public void zeroBudgetCannotIssueBeamOwnership() {
        assertFalse(new Field(1, false, 1, 0).owned(0));
    }

    @Test
    public void aForeignHeadCannotSupplyTheShaftBody() {
        var f = new Field(1, false, 1, 0);
        f.heads.add(new PrintedSlurOwners.GeometryHead(2, 101, 150, 0, 1));
        assertFalse(f.owned());
    }

    @Test
    public void curvedBandCannotUseOneFavorableStraightFit() {
        var f = new Field(1, false, 1, 0);
        f.erase(102, 163, 160, 195);
        f.beam(true);
        assertFalse(f.owned());
    }

    @Test
    public void headListPermutationKeepsLiteralOwnership() {
        var f = new Field(1, false, 1, 0);
        Collections.reverse(f.heads);
        assertTrue(f.owned());
    }

    @Test
    public void equallyWrongStaffMetadataCannotOwnAnotherStaffsPixels() {
        var f = new Field(1, false, 1, 0);
        for (int i = 0; i < 2; i++) f.notes.set(i, f.note(i, 1, -1, 0, 1, 2));
        assertFalse(f.owned());
    }

    @Test
    public void spentPageBudgetCannotBeResetForTheNextBow() throws Exception {
        var f = new Field(1, false, 1, 0);
        var counter = ObservedStemBeam.class.getDeclaredField("work");
        counter.setAccessible(true);
        var probe = new ObservedStemBeam(f.notes, f.gray, f.w, f.h);
        assertTrue(probe.owns(f.bow, f.span, f.heads, f.track));
        long charge = 8_000_000L - counter.getLong(probe);
        assertTrue(charge > 0 && charge < 8_000_000L);
        var once = new ObservedStemBeam(f.notes, f.gray, f.w, f.h, charge);
        assertTrue(once.owns(f.bow, f.span, f.heads, f.track));
        assertFalse(once.owns(f.bow, f.span, f.heads, f.track));
        assertEquals(0, counter.getLong(once));
    }

    @Test
    public void compatibleSlopeIntervalsShareOneParallelDirection() {
        var first =
                FiniteParallelBeamBand.prove(
                        new int[] {20, 20, 19, 19}, new int[] {26, 26, 25, 25}, 7);
        var second =
                FiniteParallelBeamBand.prove(
                        new int[] {40, 40, 39, 39}, new int[] {46, 46, 45, 45}, 7);
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.parallelTo(second));
        assertTrue(second.parallelTo(first));
    }

    @Test
    public void contrarySlopeIntervalsDoNotBecomeParallel() {
        var first =
                FiniteParallelBeamBand.prove(
                        new int[] {20, 19, 18, 17, 16}, new int[] {26, 25, 24, 23, 22}, 7);
        var second =
                FiniteParallelBeamBand.prove(
                        new int[] {40, 41, 42, 43, 44}, new int[] {46, 47, 48, 49, 50}, 7);
        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first.parallelTo(second));
    }

    @Test
    public void finiteEndFloorsAreExteriorOnly() {
        var band = FiniteParallelBeamBand.prove(new int[] {20, 20, 20}, new int[] {26, 26, 26}, 7);
        assertNotNull(band);
        // The one-pixel edge uncertainty permits slopes -1..1 across this three-column strip.
        assertEquals(27, band.terminalFloor(-1));
        assertEquals(27, band.terminalFloor(3));
        assertThrows(IllegalArgumentException.class, () -> band.terminalFloor(1));
    }
}
