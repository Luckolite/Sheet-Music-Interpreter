// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Original dot/arch ink, decoded rests and explicit musical clocks. */
public final class ScoreRestFermataDetectorTest {
    private ScorePageInterpretation page(double duration, boolean meter) {
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.1f, .9f, .2f, .9f)),
                List.of(),
                1,
                List.of(),
                List.of(),
                meter ? List.of(new ScoreMeterChange(0, 4, 4)) : List.of(),
                List.of(new ScoreRestEvent(0, .5f, .36f, .04f, 0, 2, duration)),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private ScorePageInterpretation detect(ScorePageInterpretation page, boolean roof) {
        var drawing = new ScoreFermataDetectorTest();
        var gray = drawing.paper();
        drawing.mark(gray, 250, 106, false, roof, false);
        return ScoreFermataDetector.withFermatas(
                page, new byte[500 * 400], gray, 500, 400, drawing.staffs());
    }

    @Test
    public void completeSilentBarOwnsRestHoldWithoutInventingSound() {
        var original = page(4, true);
        var score = detect(original, true);
        assertEquals(1, score.expressiveEvents().size());
        var event = score.expressiveEvents().get(0);
        assertEquals(Scope.REST, event.scope());
        assertEquals(new ScoreAnchor(0, 0), event.start().orElseThrow());
        assertEquals(new ScoreAnchor(1, 0), event.end().orElseThrow());
        assertEquals(original.notes(), score.notes());
        assertEquals(original.rests(), score.rests());
        var performance =
                ScoreExpressivePerformance.resolve(
                        120,
                        new ScoreMeterMap(4, List.of()),
                        1,
                        List.of(),
                        score.expressiveEvents(),
                        List.of(),
                        Map.of(),
                        ScoreExpressivePerformance.Policy.preview());
        assertEquals(2, performance.timeline().holds().get(0).seconds(), 0);
        assertTrue(performance.timeline().holds().get(0).sustainedTargets().isEmpty());
    }

    @Test
    public void unaccountedSilentSlotCannotTurnOpticalPositionIntoBeat() {
        var event = detect(page(1, true), true).expressiveEvents().get(0);
        assertEquals(Scope.UNRESOLVED, event.scope());
        assertTrue(event.start().isEmpty());
        assertTrue(event.end().isEmpty());
    }

    @Test
    public void continuationRestWaitsForMeterAndRebasesItsIdentity() {
        var score = detect(page(4, false), true);
        assertEquals(Scope.UNRESOLVED, score.expressiveEvents().get(0).scope());
        score = ScoreFermataDetector.resolve(score, 4);
        assertEquals(Scope.REST, score.expressiveEvents().get(0).scope());
        var event = ScoreDynamicContinuation.offsetEvidence(score.expressiveEvents().get(0), 3, 2);
        assertTrue(event.targetEventId().orElseThrow().startsWith("printed-rest:3:"));
        assertEquals(new ScoreAnchor(3, 0), event.start().orElseThrow());
        assertEquals(2, event.evidence().get(0).pageIndex());
    }

    @Test
    public void bareDotIsNotARestFermata() {
        assertTrue(detect(page(4, true), false).expressiveEvents().isEmpty());
    }

    @Test
    public void repeatedResolutionKeepsRestTargetAndMalformedEventUnchanged() {
        var source = detect(page(4, false), true);
        var owned = source.expressiveEvents().get(0);
        var malformed =
                new ScoreExpressiveEvent(
                        "malformed-rest-target",
                        owned.kind(),
                        Optional.empty(),
                        Optional.empty(),
                        Scope.UNRESOLVED,
                        owned.staffIndex(),
                        owned.staffCount(),
                        Optional.of("printed-rest:0:0:2:NaN"),
                        owned.strength(),
                        owned.qualifierText(),
                        owned.evidence());
        source = source.withExpressiveEvents(List.of(owned, malformed));
        var originalRests = List.copyOf(source.rests());
        var originalEvents = List.copyOf(source.expressiveEvents());
        var resolved = ScoreRestFermataDetector.resolve(source, 4);
        assertEquals(
                List.of(0),
                ScoreRestFermataDetector.targetIndices(
                        resolved, resolved.expressiveEvents().get(0)));
        assertEquals(
                new ScoreAnchor(0, 0), resolved.expressiveEvents().get(0).start().orElseThrow());
        assertEquals(new ScoreAnchor(1, 0), resolved.expressiveEvents().get(0).end().orElseThrow());
        assertSame(malformed, resolved.expressiveEvents().get(1));
        assertTrue(ScoreRestFermataDetector.targetIndices(null, malformed).isEmpty());
        assertEquals(resolved, ScoreRestFermataDetector.resolve(resolved, 4));
        assertEquals(originalRests, source.rests());
        assertEquals(originalEvents, source.expressiveEvents());
    }

    @Test
    public void mixedLaneRestWitnessesKeepFullTimingContextAndOriginalClocks() {
        var fixture = detect(page(4, true), true);
        var rest = fixture.rests().get(0);
        var foreign =
                List.of(
                        new ScoreNoteEvent(0, .2f, 0, 1, 2),
                        new ScoreNoteEvent(1, .8f, 0, 0, 2),
                        new ScoreNoteEvent(0, .5f, 0, 0, 3));
        for (int scenario = 0; scenario < 4; scenario++) {
            var notes = new ArrayList<ScoreNoteEvent>(foreign);
            if (scenario > 0)
                notes.add(
                        new ScoreNoteEvent(
                                0,
                                scenario == 1 ? .5f : scenario == 2 ? .2f : .8f,
                                0,
                                0,
                                2,
                                .5f,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1));
            var source =
                    new ScorePageInterpretation(
                            fixture.measures(),
                            notes,
                            fixture.firstMeasureNumber(),
                            fixture.keyChanges(),
                            fixture.tempoChanges(),
                            fixture.meterChanges(),
                            fixture.rests(),
                            fixture.techniqueChanges(),
                            fixture.dynamicChanges(),
                            fixture.playbackDirections(),
                            fixture.expressiveEvents());
            double reference;
            try (var timing = ScoreNoteTiming.beginTimingSession()) {
                reference =
                        ScoreRestFermataDetector.provedOnset(
                                rest, source.rests(), source.notes(), 4);
            }
            var resolved = ScoreRestFermataDetector.resolve(source, 4);
            var event = resolved.expressiveEvents().get(0);
            if (Double.isFinite(reference)) {
                assertEquals(Scope.REST, event.scope());
                assertEquals(new ScoreAnchor(0, reference), event.start().orElseThrow());
                assertEquals(
                        new ScoreAnchor(0, reference + rest.durationBeats())
                                .canonical(
                                        new ScoreMeterMap(4, List.of()), source.measures().size()),
                        event.end().orElseThrow());
            } else {
                assertEquals(Scope.UNRESOLVED, event.scope());
                assertTrue(event.start().isEmpty());
                assertTrue(event.end().isEmpty());
            }
            if (scenario == 0) assertEquals(0, reference, 0);
            if (scenario == 1) assertTrue(Double.isNaN(reference));
            assertEquals(source.notes(), resolved.notes());
            assertEquals(source.rests(), resolved.rests());
            assertEquals(resolved, ScoreRestFermataDetector.resolve(resolved, 4));
        }
    }
}
