// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original glyph/column schema and complete source-indexed literal clocks; no timing inference. */
public final class ArpeggioRetainedClockTest {
    private static ScoreNoteEvent note(int staff, int step) {
        return new ScoreNoteEvent(
                        0,
                        .25f,
                        step,
                        staff,
                        2,
                        .3f,
                        false,
                        0,
                        0,
                        ScoreNoteEvent.ACCIDENTAL_NATURAL,
                        1)
                .withClef(ScoreNoteEvent.CLEF_TREBLE);
    }

    private static ScorePageInterpretation score() {
        var score =
                new ScorePageInterpretation(
                        List.of(new MeasureRegion(.1f, .9f, .2f, .5f)),
                        List.of(note(1, 4), note(0, 0), note(0, 2)),
                        1);
        return ScoreArpeggioNotation.capture(
                score,
                List.of(1, 2),
                new ScoreExpressiveEvent.Evidence(
                        "printed-arpeggio-wave", 0, .2f, 0, 2, "original synthetic arpeggio"));
    }

    private static ScoreNoteTiming.WrittenPlacement clock(double onset, double duration) {
        return new ScoreNoteTiming.WrittenPlacement(onset, duration, -1, -1, false, 0);
    }

    @Test
    public void unequalReleasesUseFullSourceOwnersDespiteReorderedAvailability() {
        var score = score();
        assertEquals(1, score.expressiveEvents().size());
        var written = Map.of(0, clock(3, 1), 1, clock(0, 2), 2, clock(0, 1));
        var result = ScoreArpeggioNotation.plan(score, List.of(2, 1), 4, written);
        assertEquals(
                List.of(1, 2),
                result.stream().map(ScoreArpeggioNotation.Member::sourceIndex).toList());
        for (var member : result) assertEquals(new ScoreAnchor(0, 0), member.attack());
        assertEquals(2d, written.get(1).durationBeats(), 0);
        assertEquals(1d, written.get(2).durationBeats(), 0);
    }

    @Test
    public void retainedDifferentAttacksVetoMatchingPrintedColumns() {
        assertTrue(
                ScoreArpeggioNotation.plan(
                                score(),
                                List.of(1, 2),
                                4,
                                Map.of(0, clock(3, 1), 1, clock(0, 2), 2, clock(.5, 1)))
                        .isEmpty());
    }

    @Test
    public void retainedIncompleteMemberClockCannotFallBackToInference() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScoreArpeggioNotation.plan(
                                score(), List.of(1, 2), 4, Map.of(1, clock(0, 2))));
    }

    @Test
    public void partialSelectedChordNeverReceivesAnArpeggio() {
        assertTrue(
                ScoreArpeggioNotation.plan(
                                score(),
                                List.of(1),
                                4,
                                Map.of(0, clock(3, 1), 1, clock(0, 2), 2, clock(0, 1)))
                        .isEmpty());
    }
}
