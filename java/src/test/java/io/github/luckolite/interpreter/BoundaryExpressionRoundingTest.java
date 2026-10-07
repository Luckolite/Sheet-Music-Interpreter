// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

public final class BoundaryExpressionRoundingTest {
    /** Original contradictory optical values exercise the existing overfull-measure clock. */
    public static List<ScoreNoteEvent> notes() {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 20; i++) {
            float position = i == 0 ? 0 : i == 1 ? .0625f : .125f + (i - 2) * .02f;
            notes.add(
                    new ScoreNoteEvent(
                            0,
                            position,
                            i % 7,
                            0,
                            1,
                            .1f,
                            false,
                            0,
                            i < 2 || i >= 17 ? 0 : 1,
                            2,
                            i == 0 ? 2f : i >= 18 ? 4f : i == 1 || i == 17 ? 1f : 0f,
                            1,
                            0));
        }
        return List.copyOf(notes);
    }

    public static ScoreExpressiveEvent event(Kind kind, int target) {
        float position = notes().get(target).positionInMeasure();
        boolean fermata = kind == Kind.FERMATA;
        return new ScoreExpressiveEvent(
                "original-" + kind,
                kind,
                Optional.empty(),
                Optional.empty(),
                Scope.UNRESOLVED,
                0,
                1,
                Optional.of(
                        (fermata ? "printed-attack:" : "expression-column:")
                                + "0:0:1:"
                                + Float.floatToIntBits(position)),
                Strength.UNSPECIFIED,
                "",
                List.of(
                        new Evidence(
                                fermata ? "fermata-raw-ink" : "printed-expression-word",
                                0,
                                .5f,
                                0,
                                1,
                                fermata ? "fermata" : kind == Kind.BREATH ? "breath" : "rit.")));
    }

    private static ScorePageInterpretation page(Kind kind, int target) {
        return new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes())
                .withExpressiveEvents(List.of(event(kind, target)));
    }

    @Test
    public void originalClockActuallyEndsOneUlpPastFour() {
        var notes = notes();
        var note = notes.get(17);
        try (var session = ScoreNoteTiming.beginTimingSession()) {
            double end =
                    ScoreNoteTiming.beatInMeasure(note, notes, 4)
                            + ScoreNoteTiming.resolvedWrittenDurationBeats(note, notes, 4);
            assertEquals(Math.nextUp(4d), end, 0);
        }
    }

    @Test
    public void breathAtRoundedReleaseResolvesToNextBar() {
        var score = ScoreExpressionDetector.resolve(page(Kind.BREATH, 17), 4);
        var event = score.expressiveEvents().get(0);
        assertEquals(Scope.PART, event.scope());
        assertEquals(new ScoreAnchor(1, 0), event.start().orElseThrow());
    }

    @Test
    public void fermataAtRoundedReleaseRetainsItsOwnedSpan() {
        var score = ScoreFermataDetector.resolve(page(Kind.FERMATA, 17), 4);
        var event = score.expressiveEvents().get(0);
        assertEquals(Scope.NOTE, event.scope());
        assertEquals(new ScoreAnchor(1, 0), event.end().orElseThrow());
        assertEquals(3.5, event.start().orElseThrow().quarterBeatOffset(), 1e-14);
    }

    @Test
    public void genuineFermataOverrunRemainsUnresolved() {
        var event =
                ScoreFermataDetector.resolve(page(Kind.FERMATA, 18), 4).expressiveEvents().get(0);
        assertEquals(Scope.UNRESOLVED, event.scope());
        assertTrue(event.start().isEmpty());
        assertTrue(event.end().isEmpty());
    }

    @Test
    public void genuineBreathOverrunRemainsUnresolved() {
        var event =
                ScoreExpressionDetector.resolve(page(Kind.BREATH, 18), 4).expressiveEvents().get(0);
        assertEquals(Scope.UNRESOLVED, event.scope());
        assertTrue(event.start().isEmpty());
    }

    @Test
    public void normalizationDoesNotRewriteWrittenNotes() {
        var before = page(Kind.BREATH, 17);
        var notes = before.notes();
        var result = ScoreExpressionDetector.resolve(before, 4);
        assertEquals(notes, result.notes());
        assertEquals(notes, before.notes());
        assertEquals(Scope.UNRESOLVED, before.expressiveEvents().get(0).scope());
    }
}
