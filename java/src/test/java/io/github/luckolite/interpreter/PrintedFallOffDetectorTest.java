// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedFallOffDetectorTest {
    private static final int W = 320, H = 220;

    private record Scene(
            byte[] raw,
            ScorePageInterpretation score,
            List<PlayingTechniqueDetector.Staff> staffs) {}

    private Scene scene() {
        byte[] raw = new byte[W * H];
        Arrays.fill(raw, (byte) 255);
        for (int x = 1; x < W - 1; x++)
            for (int k = 0; k < 5; k++)
                for (int dy = -1; dy <= 1; dy++)
                    raw[(32 + k * 14 + dy) * W + x] = (byte) (dy == 0 ? 40 : 90);
        for (int y = 39; y <= 53; y++)
            for (int x = 74; x <= 94; x++)
                if (Math.pow((x - 84) / 9., 2) + Math.pow((y - 46) / 5., 2) <= 1)
                    raw[y * W + x] = 0;
        for (int x = 100; x <= 123; x++) {
            double before = (x - 100) / 24., after = (x - 99) / 24.;
            double first = 77.5 - 24 * Math.sqrt(1 - before * before),
                    last = 77.5 - 24 * Math.sqrt(1 - after * after);
            for (int y = (int) Math.floor(first - .6); y <= Math.ceil(last + .6); y++)
                raw[y * W + x] = 0;
        }
        var n =
                new ScoreNoteEvent(
                        0,
                        84f / W,
                        6,
                        0,
                        1,
                        46f / H,
                        false,
                        0,
                        0,
                        0,
                        2f,
                        1,
                        0,
                        0,
                        30,
                        false,
                        0,
                        false,
                        0,
                        0,
                        1,
                        -1,
                        ScoreNoteEvent.Kind.PITCHED);
        var old =
                new ScoreExpressiveEvent(
                        "original-caption",
                        ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                        Optional.empty(),
                        Optional.empty(),
                        ScoreExpressiveEvent.Scope.UNRESOLVED,
                        0,
                        1,
                        Optional.empty(),
                        ScoreExpressiveEvent.Strength.UNSPECIFIED,
                        "",
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "original-synthetic", 0, .2f, 0, 1, "unrelated")));
        var score =
                new ScorePageInterpretation(
                        List.of(new MeasureRegion(0, 1, 0, 1)),
                        List.of(n),
                        1,
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 4, 4)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(old),
                        ScoreSlurSource.empty(1));
        return new Scene(raw, score, List.of(new PlayingTechniqueDetector.Staff(32, 88, 14, 0, 1)));
    }

    private ScorePageInterpretation apply(Scene s, List<PlayingTechniqueDetector.Word> words) {
        return PrintedFallOffDetector.apply(s.score, s.raw, W, H, s.staffs, words, 0);
    }

    @Test
    public void completeSourceStageOwnsOneAuthoredFall() {
        var s = scene();
        var result = apply(s, List.of());
        assertEquals(2, result.expressiveEvents().size());
        var mark = result.expressiveEvents().get(1);
        assertEquals(ScoreExpressiveEvent.Kind.FALL_OFF, mark.kind());
        assertEquals(0, ScoreFallOffNotation.owner(mark, result.notes()));
        assertTrue(mark.start().isEmpty());
    }

    @Test
    public void completeStagePreservesNotesOldExpressionsAndRaw() {
        var s = scene();
        byte[] raw = s.raw.clone();
        var result = apply(s, List.of());
        assertEquals(s.score.notes(), result.notes());
        assertEquals(s.score.slurSource(), result.slurSource());
        assertEquals(s.score.expressiveEvents().get(0), result.expressiveEvents().get(0));
        assertArrayEquals(raw, s.raw);
    }

    @Test
    public void captureIsIdempotent() {
        var s = scene();
        var result = apply(s, List.of());
        assertEquals(2, result.expressiveEvents().size());
        assertSame(
                result, PrintedFallOffDetector.apply(result, s.raw, W, H, s.staffs, List.of(), 0));
    }

    @Test
    public void zeroOrdinaryAllowancePreservesExactOriginal() {
        var s = scene();
        assertSame(
                s.score,
                PrintedFallOffDetector.apply(
                        s.score, s.raw, W, H, s.staffs, List.of(), 0, 0, 128000000, 128000000));
    }

    @Test
    public void zeroReferenceAllowanceCannotLeaveOrdinaryPrefix() {
        var s = scene();
        assertSame(
                s.score,
                PrintedFallOffDetector.apply(
                        s.score, s.raw, W, H, s.staffs, List.of(), 0, 128000000, 0, 128000000));
    }

    @Test
    public void zeroCalibratedAllowanceCannotLeaveOrdinaryPrefix() {
        var s = scene();
        assertSame(
                s.score,
                PrintedFallOffDetector.apply(
                        s.score, s.raw, W, H, s.staffs, List.of(), 0, 128000000, 128000000, 0));
    }

    @Test
    public void textRivalCannotBecomeFalloff() {
        var s = scene();
        assertSame(
                s.score,
                apply(s, List.of(new PlayingTechniqueDetector.Word("foreign", 0, 0, 1, 1))));
    }

    @Test
    public void unrelatedTextDoesNotSuppressProvedGlyph() {
        var s = scene();
        assertEquals(
                2,
                apply(s, List.of(new PlayingTechniqueDetector.Word("title", 0, 0, .1f, .1f)))
                        .expressiveEvents()
                        .size());
    }

    @Test
    public void invalidTextContextCannotFabricateAMark() {
        var s = scene();
        assertSame(
                s.score,
                apply(
                        s,
                        List.of(new PlayingTechniqueDetector.Word("unknown", Float.NaN, 0, 1, 1))));
    }

    @Test
    public void missingPhysicalPageCannotCreateNewIdentity() {
        var s = scene();
        assertSame(
                s.score,
                PrintedFallOffDetector.apply(s.score, s.raw, W, H, s.staffs, List.of(), -1));
    }

    @Test
    public void invalidOpticalPositionCannotCrashAnOtherwiseRetainedScore() {
        var s = scene();
        var n =
                new ScoreNoteEvent(
                        0,
                        Float.NaN,
                        6,
                        0,
                        1,
                        46f / H,
                        false,
                        0,
                        0,
                        0,
                        2f,
                        1,
                        0,
                        0,
                        30,
                        false,
                        0,
                        false,
                        0,
                        0,
                        1,
                        -1,
                        ScoreNoteEvent.Kind.PITCHED);
        var bad = s.score.withNotes(List.of(n), List.of(0));
        assertSame(bad, PrintedFallOffDetector.apply(bad, s.raw, W, H, s.staffs, List.of(), 0));
    }
}
