// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic caption/tempo identities; no private score text or geometry. */
public final class SourceTempoClockOriginalTest {
    static final float TEMPO = .374f, OWNER = .46f;

    static ScoreNoteEvent note(float p, int step) {
        return new ScoreNoteEvent(0, p, step, 0, 1, .4f, false, 0, 0, 0, 1f).withLeadingRest(1.5f);
    }

    static ScoreExpressiveEvent event(float x, String target, String text) {
        return new ScoreExpressiveEvent(
                "original-caption:" + text,
                ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                Optional.of(new ScoreAnchor(0, 1.5)),
                Optional.empty(),
                ScoreExpressiveEvent.Scope.SCORE,
                0,
                1,
                Optional.of(target),
                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                "",
                List.of(
                        new ScoreExpressiveEvent.Evidence(
                                "printed-expression-word", 0, x, 0, 1, text)));
    }

    static String target() {
        return "expression-column:0:0:1:" + Float.floatToIntBits(OWNER);
    }

    static ScorePageInterpretation score(
            List<ScoreNoteEvent> notes,
            List<ScoreExpressiveEvent> events,
            List<ScoreTempoChange> tempos) {
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.2f, .8f, .2f, .5f)),
                notes,
                1,
                List.of(),
                tempos,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                events);
    }

    static ScoreTempoChange tempo() {
        return new ScoreTempoChange(0, TEMPO, 96, .5);
    }

    static ScorePageInterpretation score() {
        return score(
                List.of(note(OWNER, 0)),
                List.of(event(.2f + TEMPO * .6f, target(), "Quietly flowing")),
                List.of(tempo()));
    }

    static ScoreNoteTiming.WrittenPlacement p(double at) {
        return new ScoreNoteTiming.WrittenPlacement(at, 1, -1, -1, false, 0);
    }

    @Test
    public void identicalSourceVisualIdentityUsesWrittenOwner() {
        assertEquals(1.5, SourceTempoClock.onset(score(), tempo(), Map.of(), 4).orElseThrow(), 0);
    }

    @Test
    public void editedRetainedClockOverridesStaleExpressionStart() {
        assertEquals(
                2, SourceTempoClock.onset(score(), tempo(), Map.of(0, p(2)), 4).orElseThrow(), 0);
    }

    @Test
    public void nearbyButDifferentVisualCaptionCannotSupplyClock() {
        var s =
                score(
                        List.of(note(OWNER, 0)),
                        List.of(event(.2f + TEMPO * .6f + .0001f, target(), "Quietly flowing")),
                        List.of(tempo()));
        assertTrue(SourceTempoClock.onset(s, tempo(), Map.of(), 4).isEmpty());
    }

    @Test
    public void unownedCaptionCannotSupplyClock() {
        var s =
                score(
                        List.of(note(OWNER, 0)),
                        List.of(event(.2f + TEMPO * .6f, "unowned", "Quietly flowing")),
                        List.of(tempo()));
        assertTrue(SourceTempoClock.onset(s, tempo(), Map.of(), 4).isEmpty());
    }

    @Test
    public void multipleMatchingCaptionsAreAmbiguous() {
        var e = event(.2f + TEMPO * .6f, target(), "Quietly flowing");
        var s =
                score(
                        List.of(note(OWNER, 0)),
                        List.of(e, event(.2f + TEMPO * .6f, target(), "Another flowing caption")),
                        List.of(tempo()));
        assertTrue(SourceTempoClock.onset(s, tempo(), Map.of(), 4).isEmpty());
    }

    @Test
    public void multipleNumericMarksAtSameVisualIdentityAreAmbiguous() {
        var s =
                score(
                        List.of(note(OWNER, 0)),
                        score().expressiveEvents(),
                        List.of(tempo(), new ScoreTempoChange(0, TEMPO, 104)));
        assertTrue(SourceTempoClock.onset(s, tempo(), Map.of(), 4).isEmpty());
    }

    @Test
    public void conflictingColumnOwnersCannotInventClock() {
        var s =
                score(
                        List.of(note(OWNER, 0), note(OWNER, 2)),
                        score().expressiveEvents(),
                        List.of(tempo()));
        assertTrue(SourceTempoClock.onset(s, tempo(), Map.of(0, p(1.5), 1, p(2)), 4).isEmpty());
    }

    @Test
    public void missingSourceOwnerCannotBorrowAnotherColumn() {
        var s = score(List.of(note(.8f, 0)), score().expressiveEvents(), List.of(tempo()));
        assertTrue(SourceTempoClock.onset(s, tempo(), Map.of(), 4).isEmpty());
    }

    @Test
    public void rhythmicProjectionPreservesRatePulseAndSource() {
        var s = score();
        var projected =
                SourceTempoClock.rhythmicChanges(s, Map.of(), new ScoreMeterMap(4, List.of()));
        assertEquals(.375f, projected.get(0).positionInMeasure(), 0);
        assertEquals(96, projected.get(0).bpm(), 0);
        assertEquals(.5, projected.get(0).beatUnit(), 0);
        assertEquals(TEMPO, s.tempoChanges().get(0).positionInMeasure(), 0);
    }

    @Test
    public void unknownRhythmicRecordsAreNotRemapped() {
        var t = new ScoreTempoChange(0, .5f, 120);
        var s = score(List.of(note(OWNER, 0)), List.of(), List.of(t));
        assertSame(
                t,
                SourceTempoClock.rhythmicChanges(s, Map.of(), new ScoreMeterMap(4, List.of()))
                        .get(0));
    }

    @Test
    public void invalidMeterDoesNotCreateClock() {
        assertTrue(SourceTempoClock.onset(score(), tempo(), Map.of(), Float.NaN).isEmpty());
    }

    @Test
    public void anotherNumericRecordAtSamePositionCannotBorrowCaption() {
        assertTrue(
                SourceTempoClock.onset(
                                score(), new ScoreTempoChange(0, TEMPO, 104, .5), Map.of(), 4)
                        .isEmpty());
    }

    @Test
    public void rawUnresolvedCaptionCanUseItsProvedOriginalColumn() {
        var s = score();
        var e = s.expressiveEvents().get(0);
        var raw =
                new ScoreExpressiveEvent(
                        e.eventId(),
                        e.kind(),
                        Optional.empty(),
                        Optional.empty(),
                        ScoreExpressiveEvent.Scope.UNRESOLVED,
                        e.staffIndex(),
                        e.staffCount(),
                        e.targetEventId(),
                        e.strength(),
                        e.qualifierText(),
                        e.evidence());
        assertEquals(
                1.5,
                SourceTempoClock.onset(s.withExpressiveEvents(List.of(raw)), tempo(), Map.of(), 4)
                        .orElseThrow(),
                0);
    }

    @Test
    public void unrelatedDistantWrittenCaptionDoesNotMoveTempo() {
        var s = score();
        var e = s.expressiveEvents().get(0);
        var distant =
                new ScoreExpressiveEvent(
                        e.eventId(),
                        e.kind(),
                        Optional.of(new ScoreAnchor(0, 0)),
                        e.end(),
                        e.scope(),
                        e.staffIndex(),
                        e.staffCount(),
                        e.targetEventId(),
                        e.strength(),
                        e.qualifierText(),
                        e.evidence());
        assertTrue(
                SourceTempoClock.onset(
                                s.withExpressiveEvents(List.of(distant)), tempo(), Map.of(), 4)
                        .isEmpty());
    }
}
