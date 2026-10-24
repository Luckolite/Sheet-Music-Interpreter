// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent complete chevrons, separate returning curves and explicit raw attack owners. */
public class OwnedCurveMarkOriginalsTest {
    static final class Field {
        final int scale, w, h;
        final boolean above;
        final float gap, phase;
        final byte[] gray;
        final ArrayList<ScoreNoteEvent> notes = new ArrayList<>();
        final ArrayList<PrintedSlurOwners.GeometryHead> heads = new ArrayList<>();
        final Set<Integer> markPixels = new HashSet<>();
        final StaffPitchTrack track;
        PrintedSlurContours.Bow bow;

        Field(boolean above, int scale, float phase) {
            this.above = above;
            this.scale = scale;
            this.phase = phase;
            w = 400 * scale;
            h = 360 * scale;
            gap = 16 * scale;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            for (int line = 0; line < 5; line++)
                for (int x = 10 * scale; x < 390 * scale; x++)
                    for (int y = (240 - line * 16) * scale - scale;
                            y <= (240 - line * 16) * scale + scale;
                            y++) gray[physical(y) * w + x] = 60;
            for (int owner = 0; owner < 3; owner++) {
                float x = (owner == 0 ? 60 : owner == 1 ? 260 : 220) * scale,
                        y = physical(184 * scale + phase);
                heads.add(new PrintedSlurOwners.GeometryHead(owner, x, y, 0, 1));
                notes.add(note(owner, owner == 2 ? NoteArticulation.ACCENT : 0));
                for (int yy = Math.round(y - 6 * scale); yy <= y + 6 * scale; yy++)
                    for (int xx = Math.round(x - 9 * scale); xx <= x + 9 * scale; xx++)
                        if (Math.pow((xx - x) / (8. * scale), 2)
                                        + Math.pow((yy - y) / (5. * scale), 2)
                                <= 1) gray[yy * w + xx] = 0;
            }
            var points = new ArrayList<PrintedSlurContours.Point>();
            for (int x = 60 * scale; x <= 260 * scale; x++) {
                double t = (x - 60. * scale) / (200 * scale);
                float y = physical((float) ((150 - 160 * t * (1 - t)) * scale + phase));
                stroke(x, y, 1.15 * scale, false);
                points.add(new PrintedSlurContours.Point(x, y));
            }
            bow = new PrintedSlurContours.Bow(points, above, 2.5f, 0);
            for (int x = 208 * scale; x <= 232 * scale; x++) {
                double t = (x - 208. * scale) / (24 * scale);
                stroke(x, physical((float) ((130 + 6.5 * t) * scale + phase)), 2 * scale, true);
                stroke(x, physical((float) ((143 - 6.5 * t) * scale + phase)), 2 * scale, true);
            }
            float bottom = above ? 240 * scale : h - 1 - 176 * scale;
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, bottom, gap},
                                    new float[] {w - 1, bottom, gap}));
        }

        int physical(int y) {
            return above ? y : h - 1 - y;
        }

        float physical(float y) {
            return above ? y : h - 1 - y;
        }

        ScoreNoteEvent note(int owner, int marks) {
            var head = heads.get(owner);
            return new ScoreNoteEvent(
                    0,
                    head.x() / w,
                    7,
                    head.staffIndex(),
                    head.staffCount(),
                    head.y() / h,
                    false,
                    0,
                    0,
                    ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                    1,
                    1,
                    0,
                    marks,
                    30);
        }

        void stroke(int x, float y, double radius, boolean mark) {
            for (int yy = (int) Math.floor(y - radius); yy <= y + radius; yy++) {
                int value =
                        (int) Math.round(255 * (1 - Math.max(0, 1 - Math.abs(yy - y) / radius)));
                gray[yy * w + x] = (byte) Math.min(gray[yy * w + x] & 255, value);
                if (mark && value <= 205) markPixels.add(yy * w + x);
            }
        }

        List<PrintedSlurContours.ForeignRun> rivals() {
            var result = new ArrayList<PrintedSlurContours.ForeignRun>();
            for (int x = 0; x < w; x++) {
                var ys = new ArrayList<Integer>();
                for (int pixel : markPixels) if (pixel % w == x) ys.add(pixel / w);
                if (ys.isEmpty()) continue;
                Collections.sort(ys);
                var bands = new ArrayList<PrintedSlurContours.RawBand>();
                int first = ys.get(0), last = first;
                for (int i = 1; i < ys.size(); i++) {
                    int y = ys.get(i);
                    if (y > last + 1) {
                        bands.add(new PrintedSlurContours.RawBand(first, last));
                        first = y;
                    }
                    last = y;
                }
                bands.add(new PrintedSlurContours.RawBand(first, last));
                result.add(new PrintedSlurContours.ForeignRun(x, bands));
            }
            return result;
        }

        PrintedSlurContours.HeadEvidence evidence(long budget) {
            var old = PrintedSlurOwners.headEvidence(heads, gray, w, h, track);
            return new PrintedCurveMarkRoles(notes, gray, w, h, budget).wrap(old, heads, track);
        }

        boolean owned() {
            return owned(8_000_000L);
        }

        boolean owned(long budget) {
            byte[] before = gray.clone();
            var n = List.copyOf(notes);
            boolean result = evidence(budget).ownsCentralRivals(bow, rivals());
            assertArrayEquals(before, gray);
            assertEquals(n, notes);
            return result;
        }

        void erase(int left, int right, int top, int bottom) {
            for (int x = left * scale; x <= right * scale; x++)
                for (int y = top * scale; y <= bottom * scale; y++)
                    gray[physical(y) * w + x] = (byte) 255;
        }
    }

    @Test
    public void completeUpperAccentKeepsItsOwnRawRole() {
        assertTrue(new Field(true, 1, 0).owned());
    }

    @Test
    public void completeLowerAccentUsesTheSameWholeGlyphProof() {
        assertTrue(new Field(false, 1, 0).owned());
    }

    @Test
    public void doubleDensityRetainsTheRawAttackOwner() {
        assertTrue(new Field(true, 2, 0).owned());
    }

    @Test
    public void tripleDensityRemainsInsideThePageBudget() {
        assertTrue(new Field(false, 3, 0).owned());
    }

    @Test
    public void quarterPixelPhaseKeepsEveryMarkPixel() {
        assertTrue(new Field(true, 1, .25f).owned());
    }

    @Test
    public void halfPixelPhaseKeepsEveryMarkPixel() {
        assertTrue(new Field(false, 1, .5f).owned());
    }

    @Test
    public void completeFainterForegroundIsNotAContrastSubset() {
        var f = new Field(true, 1, 0);
        for (int p : f.markPixels) f.gray[p] = (byte) 180;
        assertTrue(f.owned());
    }

    @Test
    public void absentTypedAccentCannotBeInferredFromShape() {
        var f = new Field(true, 1, 0);
        f.notes.set(2, f.note(2, 0));
        assertFalse(f.owned());
    }

    @Test
    public void anotherTypedMarkCannotBecomeAnAccent() {
        var f = new Field(true, 1, 0);
        f.notes.set(2, f.note(2, NoteArticulation.TENUTO));
        assertFalse(f.owned());
    }

    @Test
    public void missingRawAttackHeadCannotBeDonatedByMetadata() {
        var f = new Field(true, 1, 0);
        f.erase(210, 230, 177, 191);
        assertFalse(f.owned());
    }

    @Test
    public void twoTypedAttackOwnersRemainAmbiguous() {
        var f = new Field(true, 1, 0);
        f.heads.add(new PrintedSlurOwners.GeometryHead(3, 220, 184, 0, 1));
        f.notes.add(f.note(3, NoteArticulation.ACCENT));
        assertFalse(f.owned());
    }

    @Test
    public void wholeClosedCounterCannotBorrowItsTwoChevronArms() {
        var f = new Field(true, 1, 0);
        for (int y = 130; y <= 143; y++) f.gray[y * f.w + 208] = 0;
        assertFalse(f.owned());
    }

    @Test
    public void completeWideThreeCannotBeReducedToItsOuterArms() {
        var f = new Field(true, 1, 0);
        for (int pixel : f.markPixels) f.gray[pixel] = (byte) 255;
        f.markPixels.clear();
        for (int lobe = 0; lobe < 2; lobe++)
            for (int step = 0; step <= 80; step++) {
                double t = step / 80.;
                int x = (int) Math.round(205 + 28 * Math.sin(Math.PI * t));
                float y = (float) (127 + lobe * 9.5 + 9.5 * t);
                f.stroke(x, y, 2, true);
            }
        assertFalse(f.owned());
    }

    @Test
    public void aConnectedForeignTailCannotBeCroppedOut() {
        var f = new Field(true, 1, 0);
        for (int x = 231; x <= 265; x++) f.gray[136 * f.w + x] = 0;
        assertFalse(f.owned());
    }

    @Test
    public void rawContactWithTheCurveCannotBecomeTwoRoles() {
        var f = new Field(true, 1, 0);
        for (int y = 119; y <= 132; y++) f.gray[y * f.w + 208] = 0;
        assertFalse(f.owned());
    }

    @Test
    public void unclassifiedSecondRivalKeepsTheCentralVeto() {
        var f = new Field(true, 1, 0);
        var rivals = new ArrayList<>(f.rivals());
        for (int y = 120; y <= 123; y++) f.gray[y * f.w + 140] = 0;
        rivals.add(
                new PrintedSlurContours.ForeignRun(
                        140, List.of(new PrintedSlurContours.RawBand(120, 123))));
        assertFalse(f.evidence(8_000_000L).ownsCentralRivals(f.bow, rivals));
    }

    @Test
    public void emptyRawMembersCannotCertifyARealRival() {
        var f = new Field(true, 1, 0);
        assertFalse(
                f.evidence(8_000_000L)
                        .ownsCentralRivals(
                                f.bow,
                                List.of(new PrintedSlurContours.ForeignRun(220, List.of()))));
    }

    @Test
    public void emptyRivalCollectionCannotIssueOwnership() {
        var f = new Field(true, 1, 0);
        assertFalse(f.evidence(8_000_000L).ownsCentralRivals(f.bow, List.of()));
    }

    @Test
    public void paperInsideAClaimedRivalIntervalIsNotIgnored() {
        var f = new Field(true, 1, 0);
        var rivals =
                List.of(
                        new PrintedSlurContours.ForeignRun(
                                208, List.of(new PrintedSlurContours.RawBand(130, 143))));
        assertFalse(f.evidence(8_000_000L).ownsCentralRivals(f.bow, rivals));
    }

    @Test
    public void zeroBudgetCannotOwnAnyMarkPixels() {
        assertFalse(new Field(true, 1, 0).owned(0));
    }

    @Test
    public void admissionFailureCannotBeReopenedByACheapProof() {
        var f = new Field(true, 1, 0);
        var e = f.evidence(100_000);
        assertFalse(e.admitRivalOwnership(200_000));
        assertFalse(e.admitRivalOwnership(1));
        assertFalse(e.ownsCentralRivals(f.bow, f.rivals()));
    }

    @Test
    public void shuffledWitnessesRetainTheSameCompleteRole() {
        var f = new Field(true, 1, 0);
        var rivals = new ArrayList<>(f.rivals());
        Collections.reverse(rivals);
        assertTrue(f.evidence(8_000_000L).ownsCentralRivals(f.bow, rivals));
    }

    @Test
    public void actualCurvePixelsCannotBeAssignedToTheMark() {
        var f = new Field(true, 1, 0);
        var points =
                f.markPixels.stream()
                        .sorted()
                        .map(p -> new PrintedSlurContours.Point(p % f.w, p / f.w))
                        .toList();
        f.bow = new PrintedSlurContours.Bow(points, true, .4f, 0);
        assertFalse(f.owned());
    }
}
