// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

public class PageEdgeTempoSpanTest extends TempoSpanResolutionTest {
    ScorePageInterpretation first(List<ScoreTempoChange> tempos) {
        var base = score();
        var e = event(false, 0);
        var proof =
                List.of(
                        new Evidence("printed-expression-word", 0, .2f, 0, 1, "faster"),
                        new Evidence(PrintedTempoSpans.OPEN_HINT + "4", 0, .9f, 0, 1, "faster"));
        e =
                new ScoreExpressiveEvent(
                        "faster",
                        Kind.UNRESOLVED_DIRECTION,
                        e.start(),
                        Optional.empty(),
                        Scope.UNRESOLVED,
                        0,
                        1,
                        e.targetEventId(),
                        e.strength(),
                        "",
                        proof);
        return new ScorePageInterpretation(
                        base.measures(),
                        base.notes(),
                        1,
                        List.of(),
                        tempos,
                        List.of(new ScoreMeterChange(0, 4, 4)),
                        List.of(),
                        List.of(),
                        List.of())
                .withExpressiveEvents(List.of(e));
    }

    ScorePageInterpretation next(int number, List<ScoreTempoChange> tempos) {
        var b = score();
        return new ScorePageInterpretation(
                b.measures(),
                b.notes(),
                number,
                List.of(),
                tempos,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    ScorePageInterpretation first() {
        return first(List.of(new ScoreTempoChange(0, 0, 100)));
    }

    ScoreExpressiveEvent resolve(ScorePageInterpretation a, ScorePageInterpretation b) {
        return PrintedTempoSpanPages.resolve(Arrays.asList(a, b)).get(0).expressiveEvents().get(0);
    }

    @Test
    public void adjacentOpeningTempoClosesFasterSpan() {
        var a = first();
        var e = resolve(a, next(5, List.of(new ScoreTempoChange(0, 0, 150))));
        assertEquals(Kind.ACCELERANDO, e.kind());
        assertEquals(Optional.of(new ScoreAnchor(4, 0)), e.end());
        assertEquals(Kind.UNRESOLVED_DIRECTION, a.expressiveEvents().get(0).kind());
    }

    @Test
    public void missingPageDoesNotBridge() {
        assertTrue(resolve(first(), null).end().isEmpty());
    }

    @Test
    public void numberingGapDoesNotBridge() {
        assertTrue(
                resolve(first(), next(6, List.of(new ScoreTempoChange(0, 0, 150))))
                        .end()
                        .isEmpty());
    }

    @Test
    public void wrongTempoDirectionDoesNotResolve() {
        assertTrue(
                resolve(first(), next(5, List.of(new ScoreTempoChange(0, 0, 80)))).end().isEmpty());
    }

    @Test
    public void midbarTempoIsNotOpeningEndpoint() {
        assertTrue(
                resolve(first(), next(5, List.of(new ScoreTempoChange(0, .5f, 150))))
                        .end()
                        .isEmpty());
    }

    @Test
    public void conflictingOpeningTemposDoNotResolve() {
        assertTrue(
                resolve(
                                first(),
                                next(
                                        5,
                                        List.of(
                                                new ScoreTempoChange(0, 0, 150),
                                                new ScoreTempoChange(0, 0, 140))))
                        .end()
                        .isEmpty());
    }

    @Test
    public void interveningNumericMarkDoesNotResolve() {
        assertTrue(
                resolve(
                                first(
                                        List.of(
                                                new ScoreTempoChange(0, 0, 100),
                                                new ScoreTempoChange(2, 0, 120))),
                                next(5, List.of(new ScoreTempoChange(0, 0, 150))))
                        .end()
                        .isEmpty());
    }

    @Test
    public void conflictingCurrentTemposDoNotResolve() {
        assertTrue(
                resolve(
                                first(
                                        List.of(
                                                new ScoreTempoChange(0, 0, 100),
                                                new ScoreTempoChange(0, 0, 110))),
                                next(5, List.of(new ScoreTempoChange(0, 0, 150))))
                        .end()
                        .isEmpty());
    }

    @Test
    public void identicalCurrentTemposCanResolve() {
        assertTrue(
                resolve(
                                first(
                                        List.of(
                                                new ScoreTempoChange(0, 0, 100),
                                                new ScoreTempoChange(0, 0, 100))),
                                next(5, List.of(new ScoreTempoChange(0, 0, 150))))
                        .end()
                        .isPresent());
    }
}
