// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural ink, with no retained score pixels or song positions. */
public class RawBeamedTwoBulbRestTest {
    static final class Scene {
        final int scale, w, h, dy;
        byte[] g;
        final List<ScoreNoteEvent> notes = new ArrayList<>();
        final List<MeasureRegion> bars = List.of(new MeasureRegion(0, 1, 0, 1));
        final List<SixteenthRestDetector.Staff> staffs;

        Scene() {
            this(1, 0, false);
        }

        Scene(int scale, int dy, boolean down) {
            this.scale = scale;
            this.dy = dy;
            w = 440 * scale;
            h = 300 * scale;
            g = new byte[w * h];
            Arrays.fill(g, (byte) 255);
            staffs =
                    List.of(
                            new SixteenthRestDetector.Staff(
                                    80 * scale, 136 * scale, 14 * scale, 0, 1));
            for (int y : new int[] {80, 94, 108, 122, 136}) box(12, y, 427, y);
            for (int y = 117 + dy; y <= 154 + dy; y++) {
                int x = (int) Math.round(213 - (y - 120 - dy) * .23);
                box(x, y, x + 2, y);
            }
            oval(205, 120 + dy, 5, 4);
            oval(202, 134 + dy, 5, 4);
            for (int x : new int[] {165, 250}) {
                int y = 145, stem = x + (down ? -8 : 8), tip = down ? 210 : 84;
                oval(x, y, 9, 7);
                box(stem, Math.min(y, tip), stem + (down ? -1 : 1), Math.max(y, tip));
                notes.add(
                        new ScoreNoteEvent(0, x / 440f, -1, 0, 1, y / 300f, false, 0, 2)
                                .withStemDirection(down ? -1 : 1));
            }
            box(down ? 156 : 173, down ? 210 : 84, down ? 242 : 259, down ? 214 : 88);
        }

        void box(int x1, int y1, int x2, int y2) {
            for (int y = Math.min(y1, y2) * scale; y < (Math.max(y1, y2) + 1) * scale; y++)
                for (int x = Math.min(x1, x2) * scale; x < (Math.max(x1, x2) + 1) * scale; x++)
                    if (x >= 0 && x < w && y >= 0 && y < h) g[y * w + x] = 0;
        }

        void erase(int x1, int y1, int x2, int y2) {
            for (int y = y1 * scale; y < (y2 + 1) * scale; y++)
                for (int x = x1 * scale; x < (x2 + 1) * scale; x++) g[y * w + x] = (byte) 255;
        }

        void oval(int cx, int cy, int rx, int ry) {
            for (int y = (cy - ry) * scale; y <= (cy + ry) * scale; y++)
                for (int x = (cx - rx) * scale; x <= (cx + rx) * scale; x++)
                    if (Math.pow((x - cx * scale) / (double) (rx * scale), 2)
                                    + Math.pow((y - cy * scale) / (double) (ry * scale), 2)
                            <= 1) g[y * w + x] = 0;
        }

        SixteenthRestDetector.Detection empty() {
            return new SixteenthRestDetector.Detection(List.of(), List.of());
        }

        SixteenthRestDetector.Detection add(SixteenthRestDetector.Detection old, long budget) {
            byte[] copy = g.clone();
            var result = RawBeamedTwoBulbRests.add(old, g, w, h, bars, staffs, notes, budget);
            assertArrayEquals(copy, g);
            return result;
        }

        List<ScoreRestEvent> rests() {
            return add(empty(), 8_000_000).rests();
        }

        void positive() {
            var r = rests();
            assertEquals(r.toString(), 1, r.size());
            assertEquals(.25, r.get(0).durationBeats(), 0);
        }

        void negative() {
            assertTrue(rests().toString(), rests().isEmpty());
        }
    }

    @Test
    public void raisedCompleteRawGlyphIsRecovered() {
        new Scene().positive();
    }

    @Test
    public void loweredCompleteRawGlyphIsRecovered() {
        new Scene(1, 28, false).positive();
    }

    @Test
    public void doubledRasterDensityRetainsTheSameDuration() {
        new Scene(2, 0, false).positive();
    }

    @Test
    public void downwardAttachedBeamCanOwnTheGlyph() {
        new Scene(1, 0, true).positive();
    }

    @Test
    public void croppedQuarterIsReplacedOnlyAfterBothBulbsAreProved() {
        Scene s = new Scene();
        var q = new ScoreRestEvent(0, 207f / 440, 136f / 300, 34f / 300, 0, 1, 1);
        var old = new SixteenthRestDetector.Detection(List.of(q), List.of());
        var result = s.add(old, 8_000_000);
        assertEquals(1, result.rests().size());
        assertEquals(.25, result.rests().get(0).durationBeats(), 0);
    }

    @Test
    public void anAlreadyAcceptedSixteenthKeepsItsObjectAndDots() {
        Scene s = new Scene();
        var r = new ScoreRestEvent(0, 207f / 440, 136f / 300, 34f / 300, 0, 1, .25);
        var d = new SixteenthRestDetector.RestDot(.51f, .45f, r);
        var old = new SixteenthRestDetector.Detection(List.of(r), List.of(d));
        assertSame(old, s.add(old, 8_000_000));
    }

    @Test
    public void aQuarterNotCoveringBothBulbsIsPreserved() {
        Scene s = new Scene();
        var q = new ScoreRestEvent(0, 207f / 440, 145f / 300, 20f / 300, 0, 1, 1);
        var old = new SixteenthRestDetector.Detection(List.of(q), List.of());
        assertSame(old, s.add(old, 8_000_000));
    }

    @Test
    public void noBeamInkCannotBorrowSemanticBeamCounts() {
        Scene s = new Scene();
        s.erase(170, 82, 261, 90);
        s.negative();
    }

    @Test
    public void interruptedBeamCannotOwnSilence() {
        Scene s = new Scene();
        s.erase(206, 82, 225, 90);
        s.negative();
    }

    @Test
    public void noRecognizedNotesCannotInventAStaffVoice() {
        Scene s = new Scene();
        s.notes.clear();
        s.negative();
    }

    @Test
    public void nonBeamedFollowingNoteDoesNotAuthorizeRecovery() {
        Scene s = new Scene();
        s.notes.set(1, new ScoreNoteEvent(0, 250f / 440, -1, 0, 1, 145f / 300, false, 0, 0));
        s.negative();
    }

    @Test
    public void recognizedHeadInsideTheBodyVetoesTheRest() {
        Scene s = new Scene();
        s.notes.add(new ScoreNoteEvent(0, 202f / 440, -1, 0, 1, 134f / 300, false, 0, 0));
        s.negative();
    }

    @Test
    public void graceHeadInsideTheBodyAlsoKeepsItsInk() {
        Scene s = new Scene();
        s.notes.add(
                new ScoreNoteEvent(
                        0, 202f / 440, -1, 0, 1, 134f / 300, false, NoteOrnament.GRACE, 0));
        s.negative();
    }

    @Test
    public void opposingVoiceAtTheAttackVetoesReassignment() {
        Scene s = new Scene();
        s.oval(165, 165, 9, 7);
        s.box(156, 165, 157, 225);
        s.notes.add(
                new ScoreNoteEvent(0, 165f / 440, -4, 0, 1, 165f / 300, false, 0, 1)
                        .withStemDirection(-1));
        s.negative();
    }

    @Test
    public void aSingleBulbIsNotASixteenth() {
        Scene s = new Scene();
        s.erase(197, 115, 211, 125);
        s.negative();
    }

    @Test
    public void incompleteTailCannotProveAWholeGlyph() {
        Scene s = new Scene();
        s.erase(196, 142, 217, 155);
        s.negative();
    }

    @Test
    public void detachedBulbsAndTailDoNotFormOneRest() {
        Scene s = new Scene();
        s.erase(207, 125, 215, 129);
        s.negative();
    }

    @Test
    public void verticalStemWithTwoBulbsIsNotADiagonalRest() {
        Scene s = new Scene();
        s.erase(194, 115, 220, 155);
        s.box(212, 117, 214, 154);
        s.oval(205, 120, 5, 4);
        s.oval(205, 134, 5, 4);
        s.negative();
    }

    @Test
    public void rightLeaningStemWithTwoBulbsIsRejected() {
        Scene s = new Scene();
        s.erase(194, 115, 220, 155);
        for (int y = 117; y <= 154; y++) {
            int x = 211 + (y - 117) / 5;
            s.box(x, y, x + 2, y);
        }
        s.oval(205, 120, 5, 4);
        s.oval(208, 134, 5, 4);
        s.negative();
    }

    @Test
    public void unrecognizedAugmentationDotVetoesUndottedRecovery() {
        Scene s = new Scene();
        s.oval(230, 128, 3, 3);
        s.negative();
    }

    @Test
    public void neighboringTextStrokeVetoesTheBody() {
        Scene s = new Scene();
        s.box(197, 126, 199, 132);
        s.negative();
    }

    @Test
    public void emptyBudgetReturnsTheUnmodifiedDetection() {
        Scene s = new Scene();
        var old = s.empty();
        assertSame(old, s.add(old, 0));
    }

    @Test
    public void smallBudgetDiscardsTentativeAdmissions() {
        Scene s = new Scene();
        var old = s.empty();
        assertSame(old, s.add(old, 100));
    }

    @Test
    public void exhaustionAfterOneAdmissionRollsBackTheWholeOptionalPhase() {
        Scene s = new Scene();
        var old = s.empty();
        assertEquals(1, s.add(old, 100_000).rests().size());
        var notes = new ArrayList<>(s.notes);
        for (var n : s.notes)
            notes.add(
                    new ScoreNoteEvent(
                                    1,
                                    n.positionInMeasure(),
                                    n.staffStep(),
                                    0,
                                    1,
                                    n.pageY(),
                                    false,
                                    0,
                                    2)
                            .withStemDirection(1));
        assertSame(
                old,
                RawBeamedTwoBulbRests.add(
                        old,
                        s.g,
                        s.w,
                        s.h,
                        List.of(s.bars.get(0), s.bars.get(0)),
                        s.staffs,
                        notes,
                        100_000));
    }

    @Test
    public void malformedRasterReturnsTheOriginalDetection() {
        Scene s = new Scene();
        var old = s.empty();
        assertSame(
                old,
                RawBeamedTwoBulbRests.add(old, new byte[2], s.w, s.h, s.bars, s.staffs, s.notes));
    }
}
