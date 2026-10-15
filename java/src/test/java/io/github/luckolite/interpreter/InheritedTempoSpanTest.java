// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

public class InheritedTempoSpanTest extends TempoSpanResolutionTest {
    ScorePageInterpretation page(List<String> hints, List<ScoreTempoChange> tempos, int offset) {
        var base = score();
        var e = event(true, 0);
        var evidence = new ArrayList<>(e.evidence());
        for (String hint : hints)
            evidence.add(new Evidence("printed-tempo-dash-end-v1:" + hint, 0, .7f, 0, 1, "rit"));
        e =
                new ScoreExpressiveEvent(
                        e.eventId(),
                        e.kind(),
                        Optional.empty(),
                        Optional.empty(),
                        Scope.UNRESOLVED,
                        0,
                        1,
                        e.targetEventId(),
                        e.strength(),
                        e.qualifierText(),
                        evidence);
        if (offset > 0) e = ScoreExpressionDetector.offsetEvidence(e, offset, 1);
        return new ScorePageInterpretation(
                        base.measures(),
                        base.notes(),
                        1,
                        List.of(),
                        tempos,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of())
                .withExpressiveEvents(List.of(e));
    }

    @Test
    public void waitsForMeterThenRecoversEndpoint() {
        var page = page(List.of("1"), List.of(new ScoreTempoChange(1, 0, 100)), 0);
        assertTrue(
                ScoreExpressionDetector.resolve(page, Float.NaN)
                        .expressiveEvents()
                        .get(0)
                        .start()
                        .isEmpty());
        var e = ScoreExpressionDetector.resolve(page, 4).expressiveEvents().get(0);
        assertEquals(Optional.of(new ScoreAnchor(0, 0)), e.start());
        assertEquals(Optional.of(new ScoreAnchor(1, 0)), e.end());
    }

    @Test
    public void relativeEndpointSurvivesPageOffset() {
        var e =
                ScoreExpressionDetector.resolve(
                                page(List.of("1"), List.of(new ScoreTempoChange(3, 0, 100)), 2), 4)
                        .expressiveEvents()
                        .get(0);
        assertEquals(Optional.of(new ScoreAnchor(3, 0)), e.end());
    }

    @Test
    public void missingNumericEvidenceDoesNotResolve() {
        assertTrue(
                ScoreExpressionDetector.resolve(page(List.of("1"), List.of(), 0), 4)
                        .expressiveEvents()
                        .get(0)
                        .end()
                        .isEmpty());
    }

    @Test
    public void conflictingHintsRemainUnresolved() {
        assertTrue(
                ScoreExpressionDetector.resolve(
                                page(
                                        List.of("1", "2"),
                                        List.of(new ScoreTempoChange(1, 0, 100)),
                                        0),
                                4)
                        .expressiveEvents()
                        .get(0)
                        .end()
                        .isEmpty());
    }

    @Test
    public void malformedHintRemainsUnresolved() {
        assertTrue(
                ScoreExpressionDetector.resolve(
                                page(List.of("-1"), List.of(new ScoreTempoChange(1, 0, 100)), 0), 4)
                        .expressiveEvents()
                        .get(0)
                        .end()
                        .isEmpty());
    }
}
