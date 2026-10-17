// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Original procedural rails/dashes and semantic words; no private pixels or fonts. */
public class AnchoredTempoDashSideTest {
    static class Fixture {
        final int w, h;
        final float scale, gap;
        final byte[] gray;
        final List<MeasureRegion> measures = new ArrayList<>();
        final List<ScoreNoteEvent> notes = new ArrayList<>();
        final List<PlayingTechniqueDetector.Staff> staffs = new ArrayList<>();
        final List<PlayingTechniqueDetector.Word> words = new ArrayList<>();
        final List<ScoreExpressiveEvent> events = new ArrayList<>();

        Fixture(float scale) {
            this.scale = scale;
            w = Math.round(1000 * scale);
            h = Math.round(800 * scale);
            gap = 16 * scale;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            for (int row = 0; row < 3; row++) {
                int first = 120 + row * 200;
                measures.add(
                        new MeasureRegion(
                                .1f, .95f, (80 + row * 200) / 800f, (224 + row * 200) / 800f));
                staffs.add(
                        new PlayingTechniqueDetector.Staff(
                                first * scale, (first + 64) * scale, gap, 0, 1));
                for (int n = 0; n < 5; n++) line(100, first + n * 16, 950, first + n * 16, 20);
                for (float x : new float[] {.15f, .4f, .65f, .9f})
                    notes.add(
                            new ScoreNoteEvent(
                                    row, x, 0, 0, 1, (first + 64) / 800f, false, 0, 0, 2, 1, 1, 0));
            }
            dashes(350, 930, 232);
            dashes(110, 930, 456);
            word("gradually faster", 200, 345, 220, 240, 0, Kind.ACCELERANDO);
        }

        void line(int x0, int y0, int x1, int y1, int tone) {
            for (int x = Math.round(x0 * scale); x <= Math.round(x1 * scale); x++)
                gray[Math.round(y0 * scale) * w + x] = (byte) tone;
        }

        void dashes(int x0, int x1, int y) {
            for (int x = x0; x < x1; x += 15)
                for (int yy = Math.round(y * scale); yy < Math.round((y + 2) * scale); yy++)
                    for (int xx = Math.round(x * scale); xx < Math.round((x + 6) * scale); xx++)
                        gray[yy * w + xx] = 20;
        }

        void eraseDashes(int first, int last) {
            for (int y = Math.round(first * scale); y < Math.round(last * scale); y++)
                Arrays.fill(gray, y * w, (y + 1) * w, (byte) 255);
        }

        void word(String text, int left, int right, int top, int bottom, int row, Kind kind) {
            words.add(
                    new PlayingTechniqueDetector.Word(
                            text, left / 1000f, top / 800f, right / 1000f, bottom / 800f));
            events.add(
                    new ScoreExpressiveEvent(
                            "event:" + row + ":" + text,
                            kind,
                            Optional.of(new ScoreAnchor(row, 0)),
                            Optional.empty(),
                            Scope.SCORE,
                            0,
                            1,
                            Optional.of(
                                    "expression-column:"
                                            + row
                                            + ":0:1:"
                                            + Float.floatToIntBits(.15f)),
                            Strength.UNSPECIFIED,
                            "",
                            List.of(
                                    new Evidence(
                                            "printed-expression-word",
                                            0,
                                            left / 1000f,
                                            0,
                                            1,
                                            text))));
        }

        ScorePageInterpretation score() {
            return new ScorePageInterpretation(
                            measures,
                            notes,
                            1,
                            List.of(),
                            List.of(
                                    new ScoreTempoChange(0, 0, 100),
                                    new ScoreTempoChange(2, 0, 110)),
                            List.of(new ScoreMeterChange(0, 4, 4)),
                            List.of(),
                            List.of(),
                            List.of())
                    .withExpressiveEvents(events);
        }

        ScorePageInterpretation run() {
            return PrintedTempoSpans.apply(score(), words, staffs, gray, w, h);
        }

        ScoreExpressiveEvent first() {
            return run().expressiveEvents().get(0);
        }
    }

    @Test
    public void anchoredBelowSideClosesAcrossBroadGutters() {
        var f = new Fixture(1);
        assertEquals(Optional.of(new ScoreAnchor(2, 0)), f.first().end());
    }

    @Test
    public void scalesKeepSameOpticalEndpoint() {
        for (float scale : new float[] {.75f, 1.25f})
            assertEquals(
                    "scale=" + scale,
                    Optional.of(new ScoreAnchor(2, 0)),
                    new Fixture(scale).first().end());
    }

    @Test
    public void missingFifthRailDoesNotNarrowOwnership() {
        var f = new Fixture(1);
        f.line(100, 384, 950, 384, 255);
        assertTrue(f.first().end().isEmpty());
    }

    @Test
    public void disagreeingRailWitnessesAbstain() {
        var f = new Fixture(1);
        for (int n = 0; n < 5; n++) {
            f.line(500, 120 + n * 16, 950, 120 + n * 16, 255);
            f.line(500, 126 + n * 16, 950, 126 + n * 16, 20);
        }
        assertTrue(f.first().end().isEmpty());
    }

    @Test
    public void multipleStaffsInOneLogicalRowAbstain() {
        var f = new Fixture(1);
        f.staffs.add(new PlayingTechniqueDetector.Staff(140, 204, 16, 0, 1));
        for (int n = 0; n < 5; n++) f.line(100, 140 + n * 16, 950, 140 + n * 16, 20);
        assertTrue(f.first().end().isEmpty());
    }

    @Test
    public void crossPartStaffDoesNotAcquireSpan() {
        var f = new Fixture(1);
        f.staffs.set(1, new PlayingTechniqueDetector.Staff(320, 384, 16, 1, 2));
        assertEquals(f.score(), f.run());
    }

    @Test
    public void aboveExpressionCannotShareContinuationDash() {
        var f = new Fixture(1);
        f.word("rit", 30, 105, 444, 464, 2, Kind.RITARDANDO);
        var out = f.run();
        assertTrue(
                out.expressiveEvents().stream()
                        .allMatch(
                                e ->
                                        e.end().isEmpty()
                                                && e.evidence().stream()
                                                        .noneMatch(
                                                                x ->
                                                                        x.sourceId()
                                                                                .startsWith(
                                                                                        "printed-tempo-dash"))));
    }

    @Test
    public void sideSwitchIsNotAContinuation() {
        var f = new Fixture(1);
        f.eraseDashes(450, 465);
        f.dashes(110, 930, 296);
        assertTrue(f.first().end().isEmpty());
    }

    @Test
    public void physicalCompetingStaffsRemainUnresolved() {
        var f = new Fixture(1);
        f.staffs.add(new PlayingTechniqueDetector.Staff(170, 234, 16, 0, 1));
        for (int n = 0; n < 5; n++) f.line(100, 170 + n * 16, 950, 170 + n * 16, 20);
        assertTrue(f.first().end().isEmpty());
    }

    @Test
    public void absentMiddleRunDoesNotSkipAStaff() {
        var f = new Fixture(1);
        f.eraseDashes(450, 465);
        assertTrue(f.first().end().isEmpty());
    }

    @Test
    public void distantWordCannotBorrowAChain() {
        var f = new Fixture(1);
        f.words.clear();
        f.words.add(new PlayingTechniqueDetector.Word("gradually faster", .1f, .275f, .2f, .3f));
        assertTrue(f.first().end().isEmpty());
    }

    @Test
    public void notesRestsAndPixelsAreUnchanged() {
        var f = new Fixture(1);
        var bytes = f.gray.clone();
        var before = f.score();
        var after = f.run();
        assertEquals(Optional.of(new ScoreAnchor(2, 0)), after.expressiveEvents().get(0).end());
        assertEquals(before.notes(), after.notes());
        assertEquals(before.rests(), after.rests());
        assertArrayEquals(bytes, f.gray);
    }

    @Test
    public void legacyGenericOwnershipStillRejectsAmbiguousRun() {
        var f = new Fixture(1);
        var systems = new ArrayList<PrintedDashSpan.SystemBand>();
        for (var m : f.measures)
            systems.add(
                    new PrintedDashSpan.SystemBand(m.left(), m.right(), m.top(), m.bottom(), .9f));
        var w = f.words.get(0);
        assertTrue(
                PrintedDashSpan.follow(
                                PrintedDashChains.detect(f.gray, f.w, f.h, f.gap),
                                systems,
                                new PrintedDashSpan.WordBox(
                                        w.left(), w.right(), w.top(), w.bottom()),
                                f.gap / f.w,
                                f.gap / f.h)
                        .isEmpty());
    }
}
