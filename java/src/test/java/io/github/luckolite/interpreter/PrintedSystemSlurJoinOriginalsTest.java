// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent complete row-end bows, literal heads and bounded printed staff edges. */
public class PrintedSystemSlurJoinOriginalsTest {
    static final class Field {
        final int scale, w, h;
        final float gap, phase;
        final boolean above;
        final byte[] gray;
        final ArrayList<ScoreNoteEvent> notes = new ArrayList<>();
        final ArrayList<PrintedSlurPageCapture.Frame<PrintedSlurOwners.GeometryHead>> frames =
                new ArrayList<>();
        final ArrayList<PrintedSystemSlurJoins.Observed> pieces = new ArrayList<>();

        Field(boolean above, int scale, float phase) {
            this.above = above;
            this.scale = scale;
            this.phase = phase;
            gap = 16 * scale;
            w = 800 * scale;
            h = 650 * scale;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            for (int row = 0; row < 2; row++) {
                int bottom = (row == 0 ? 220 : 500) * scale;
                for (int line = 0; line < 5; line++)
                    for (int x = 40 * scale; x <= 740 * scale; x++)
                        for (int y = bottom - line * 16 * scale - scale;
                                y <= bottom - line * 16 * scale + scale;
                                y++) gray[y * w + x] = 60;
                for (int x : new int[] {40 * scale, 40 * scale + 1, 740 * scale - 1, 740 * scale})
                    for (int y = bottom - 64 * scale; y <= bottom; y++) gray[y * w + x] = 0;
                var heads = new ArrayList<PrintedSlurOwners.GeometryHead>();
                int[] xs = row == 0 ? new int[] {680} : new int[] {150, 210, 270};
                for (int i = 0; i < xs.length; i++) {
                    int owner = notes.size();
                    float x = xs[i] * scale,
                            y = map((row == 0 ? 180 : i == 1 ? 452 : 460) * scale + phase, row);
                    heads.add(new PrintedSlurOwners.GeometryHead(owner, x, y, 0, 1));
                    int step = Math.round((bottom - y) / gap * 2);
                    notes.add(
                            new ScoreNoteEvent(
                                    row,
                                    (x - 40 * scale) / (700 * scale),
                                    step,
                                    0,
                                    1,
                                    y / h,
                                    false,
                                    0,
                                    0,
                                    ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                    1));
                    for (int yy = Math.round(y - 6 * scale); yy <= y + 6 * scale; yy++)
                        for (int xx = Math.round(x - 9 * scale); xx <= x + 9 * scale; xx++)
                            if (Math.pow((xx - x) / (8. * scale), 2)
                                            + Math.pow((yy - y) / (5. * scale), 2)
                                    <= 1) gray[yy * w + xx] = 0;
                }
                var track =
                        StaffPitchTrack.fromVerifiedSamples(
                                List.of(
                                        new float[] {0, bottom, gap},
                                        new float[] {w - 1, bottom, gap}));
                var frame =
                        new PrintedSlurPageCapture.Frame<>(
                                0,
                                (row == 0 ? 80 : 360) * scale,
                                w - 1,
                                (row == 0 ? 260 : 580) * scale,
                                track,
                                heads);
                frames.add(frame);
                var bow =
                        curve(
                                row,
                                row == 0 ? 678 : 102,
                                row == 0 ? 149 : 431,
                                row == 0 ? 728 : 266,
                                row == 0 ? 151 : 429,
                                row == 0 ? 16 : 24);
                pieces.add(new PrintedSystemSlurJoins.Observed(frame, bow));
            }
        }

        float map(float y, int row) {
            return above ? y : 2 * (row == 0 ? 188 : 468) * scale - y;
        }

        PrintedSlurContours.Bow curve(
                int row, int left, int start, int right, int end, float bend) {
            var points = new ArrayList<PrintedSlurContours.Point>();
            for (int x = left * scale; x <= right * scale; x++) {
                double t = (x - left * (double) scale) / ((right - left) * scale);
                float y =
                        map(
                                (float)
                                        ((start + (end - start) * t - 4 * bend * t * (1 - t))
                                                        * scale
                                                + phase),
                                row);
                for (int yy = (int) Math.floor(y - 2 * scale); yy <= y + 2 * scale; yy++) {
                    double ink = Math.max(0, 1 - Math.abs(yy - y) / (1.2 * scale));
                    int value = (int) Math.round(255 * (1 - ink));
                    gray[yy * w + x] = (byte) Math.min(gray[yy * w + x] & 255, value);
                }
                points.add(new PrintedSlurContours.Point(x, y));
            }
            return new PrintedSlurContours.Bow(points, above, bend / 16, 0);
        }

        List<ScoreSlurPages.Span> joined() {
            return joined(8_000_000L);
        }

        List<ScoreSlurPages.Span> joined(long budget) {
            byte[] before = gray.clone();
            var n = List.copyOf(notes);
            var result = PrintedSystemSlurJoins.join(9, pieces, frames, notes, gray, w, h, budget);
            assertArrayEquals(before, gray);
            assertEquals(n, notes);
            return result;
        }

        void erase(int left, int top, int right, int bottom) {
            for (int y = top * scale; y <= bottom * scale; y++)
                Arrays.fill(gray, y * w + left * scale, y * w + right * scale + 1, (byte) 255);
        }
    }

    static void ownsOne(Field f) {
        var joined = f.joined();
        assertEquals(1, joined.size());
        var span = joined.get(0);
        assertEquals(new ScoreSlurPages.Anchor(9, 0), span.start());
        assertEquals(new ScoreSlurPages.Anchor(9, 3), span.end());
        assertEquals(
                f.above ? ScoreSlurSpan.Placement.ABOVE : ScoreSlurSpan.Placement.BELOW,
                span.placement());
        assertEquals(2, span.evidence().size());
        assertEquals(9, span.evidence().get(0).pageIndex());
    }

    @Test
    public void upperTwoPiecePhraseKeepsBothLiteralPieces() {
        ownsOne(new Field(true, 1, 0));
    }

    @Test
    public void lowerTwoPiecePhraseUsesTheSameContinuationAuthority() {
        ownsOne(new Field(false, 1, 0));
    }

    @Test
    public void doubleDensityRetainsPhysicalOwners() {
        ownsOne(new Field(true, 2, 0));
    }

    @Test
    public void tripleDensityRemainsInsideOneBoundedJoin() {
        ownsOne(new Field(false, 3, 0));
    }

    @Test
    public void quarterPixelPhaseDoesNotMoveOwners() {
        ownsOne(new Field(true, 1, .25f));
    }

    @Test
    public void halfPixelPhaseDoesNotMoveOwners() {
        ownsOne(new Field(false, 1, .5f));
    }

    @Test
    public void frameListOrderDoesNotInventMusicalOrder() {
        var f = new Field(true, 1, 0);
        var before = f.joined();
        Collections.reverse(f.frames);
        assertEquals(before, f.joined());
    }

    @Test
    public void fragmentListOrderDoesNotChangePhysicalIdentity() {
        var f = new Field(true, 1, 0);
        var before = f.joined();
        Collections.reverse(f.pieces);
        assertEquals(before, f.joined());
    }

    @Test
    public void missingOutgoingPieceRemainsUnknown() {
        var f = new Field(true, 1, 0);
        f.pieces.remove(0);
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void missingIncomingPieceRemainsUnknown() {
        var f = new Field(true, 1, 0);
        f.pieces.remove(1);
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void anInklessMetadataHeadCannotOwnThePhrase() {
        var f = new Field(true, 1, 0);
        f.erase(671, 174, 689, 186);
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void missingOutgoingPrintedBoundaryCannotBeGuessed() {
        var f = new Field(true, 1, 0);
        f.erase(738, 155, 742, 221);
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void missingIncomingPrintedBoundaryCannotBeGuessed() {
        var f = new Field(true, 1, 0);
        f.erase(39, 435, 42, 501);
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void oneMissingObservedColumnCannotBeInterpolated() {
        var f = new Field(true, 1, 0);
        f.erase(700, 100, 700, 153);
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void differentlyPlacedPiecesRemainUnknown() {
        var f = new Field(true, 1, 0);
        var p = f.pieces.get(1);
        var b = p.bow();
        f.pieces.set(
                1,
                new PrintedSystemSlurJoins.Observed(
                        p.frame(),
                        new PrintedSlurContours.Bow(b.points(), false, b.bendInGaps(), 0)));
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void mismatchedFreeCapPhaseCannotBeMatchedByOwnerOrder() {
        var f = new Field(true, 1, 0);
        f.pieces.set(
                1,
                new PrintedSystemSlurJoins.Observed(
                        f.frames.get(1), f.curve(1, 102, 410, 266, 429, 24)));
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void skippedMeasureCannotBecomeAdjacentStaffRows() {
        var f = new Field(true, 1, 0);
        for (int i = 1; i < 4; i++) {
            var n = f.notes.get(i);
            f.notes.set(
                    i,
                    new ScoreNoteEvent(
                            2,
                            n.positionInMeasure(),
                            n.staffStep(),
                            0,
                            1,
                            n.pageY(),
                            false,
                            0,
                            0,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            1));
        }
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void competingCompleteIncomingPiecesStayAmbiguous() {
        var f = new Field(true, 1, 0);
        f.pieces.add(f.pieces.get(1));
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void noInteriorAttackLeavesAnAdjacentTieUnclassified() {
        var f = new Field(true, 1, 0);
        var old = f.frames.get(1);
        var keep = List.of(old.heads().get(2));
        var frame =
                new PrintedSlurPageCapture.Frame<>(
                        old.left(), old.top(), old.right(), old.bottom(), old.track(), keep);
        f.frames.set(1, frame);
        f.pieces.set(1, new PrintedSystemSlurJoins.Observed(frame, f.pieces.get(1).bow()));
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void zeroBudgetCannotIssueAnyJoin() {
        assertTrue(new Field(true, 1, 0).joined(0).isEmpty());
    }

    @Test
    public void invalidBudgetIsRejected() {
        var f = new Field(true, 1, 0);
        assertThrows(IllegalArgumentException.class, () -> f.joined(-1));
        assertThrows(IllegalArgumentException.class, () -> f.joined(8_000_001));
    }

    @Test
    public void sourceNotesWithWrongStaffCannotBorrowTheFrame() {
        var f = new Field(true, 1, 0);
        var n = f.notes.get(3);
        f.notes.set(
                3,
                new ScoreNoteEvent(
                        1,
                        n.positionInMeasure(),
                        n.staffStep(),
                        1,
                        2,
                        n.pageY(),
                        false,
                        0,
                        0,
                        ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                        1));
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void aVerticalStrokeWithoutFiveRuleContactsIsNotASystemEdge() {
        var f = new Field(true, 1, 0);
        for (int line = 0; line < 5; line++)
            f.erase(715, 220 - line * 16 - 3, 738, 220 - line * 16 + 3);
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void aBarInsideContinuingRulesIsNotTheEndOfThePrintedSystem() {
        var f = new Field(true, 1, 0);
        for (int line = 0; line < 5; line++)
            for (int x = 741; x <= 780; x++)
                for (int y = 219 - line * 16; y <= 221 - line * 16; y++) f.gray[y * f.w + x] = 60;
        assertTrue(f.joined().isEmpty());
    }

    @Test
    public void assembledDifferentPitchInteriorSeparatesTheOuterPhraseFromItsInnerTie() {
        var f = new Field(true, 1, 0);
        var joined = f.joined();
        assertEquals(1, joined.size());
        var source =
                ScoreSlurSource.capture(List.of(), joined, List.of(new ScoreSlurPages.Page(9, 4)));
        var notes =
                List.of(
                        new ScoreSlurClassification.Note(0, 0, 0, 60, 100, 0, 1),
                        new ScoreSlurClassification.Note(1, 100, 0, 60, 200, 0, 1),
                        new ScoreSlurClassification.Note(2, 200, 0, 62, 300, 0, 1),
                        new ScoreSlurClassification.Note(3, 300, 0, 60, 400, 0, 1));
        var ties = Set.of(new ScoreSlurClassification.Tie(0, 1));
        var result = ScoreSlurClassification.resolve(source, notes, ties);
        assertEquals(1, result.spans().size());
        assertTrue(result.candidates().isEmpty());
        assertEquals(source.noteOrigins(), result.noteOrigins());
        assertEquals(1, ties.size());
    }
}
