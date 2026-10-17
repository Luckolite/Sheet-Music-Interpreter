// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independently authored arithmetic and opaque ownership cases. No score data. */
public final class OverflowIndependentTest {
    static ScorePageInterpretation score(int measure) {
        var n = new ArrayList<ScoreNoteEvent>();
        for (int step : new int[] {0, 2, 4})
            n.add(new ScoreNoteEvent(measure, .2f, step, 0, 1, .3f, false, 0, 0, 2, 4));
        return new ScorePageInterpretation(
                Collections.nCopies(measure + 1, new MeasureRegion(.1f, .9f, .2f, .5f)), n, 1);
    }

    static ScoreExpressiveEvent source(
            String measure, Optional<ScoreAnchor> start, Optional<ScoreAnchor> end) {
        return new ScoreExpressiveEvent(
                "owned-original",
                ScoreExpressiveEvent.Kind.ARPEGGIO,
                start,
                end,
                start.isPresent()
                        ? ScoreExpressiveEvent.Scope.NOTE
                        : ScoreExpressiveEvent.Scope.UNRESOLVED,
                0,
                1,
                Optional.of(
                        "arpeggio-column-v1:"
                                + measure
                                + ":0:1:"
                                + Float.toHexString(.2f)
                                + ":0,2,4"),
                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                "",
                List.of(
                        new ScoreExpressiveEvent.Evidence(
                                "printed-arpeggio-wave", 0, .15f, 0, 1, "arpeggio"),
                        new ScoreExpressiveEvent.Evidence(
                                "second-proof", 0, .16f, 0, 1, "authored")));
    }

    static void unresolved(ScoreExpressiveEvent e) {
        assertEquals(ScoreExpressiveEvent.Kind.ARPEGGIO, e.kind());
        assertEquals(ScoreExpressiveEvent.Scope.UNRESOLVED, e.scope());
        assertTrue(e.start().isEmpty());
        assertTrue(e.end().isEmpty());
        assertTrue(ScoreArpeggioNotation.owns(e));
        assertEquals(
                List.of(7, 7),
                e.evidence().stream().map(ScoreExpressiveEvent.Evidence::pageIndex).toList());
        assertEquals(
                List.of("printed-arpeggio-wave", "second-proof"),
                e.evidence().stream().map(ScoreExpressiveEvent.Evidence::sourceId).toList());
        assertFalse(e.targetEventId().orElse("").contains("-2147483648"));
        for (int m : new int[] {0, 1}) {
            var s = score(m).withExpressiveEvents(List.of(e));
            assertTrue(ScoreArpeggioNotation.targetIndices(s, e).isEmpty());
            assertTrue(ScoreArpeggioNotation.plan(s, List.of(0, 1, 2), 4).isEmpty());
            assertTrue(
                    ScoreArpeggioNotation.resolve(s, 4)
                            .expressiveEvents()
                            .get(0)
                            .start()
                            .isEmpty());
        }
    }

    @Test
    public void maximumTargetPlusOneCannotWrapOrResolve() {
        var e =
                ScoreDynamicContinuation.offsetEvidence(
                        source("2147483647", Optional.empty(), Optional.empty()), 1, 7);
        unresolved(e);
        assertEquals("page:7/owned-original", e.eventId());
        assertTrue(
                e.targetEventId().orElseThrow().startsWith("arpeggio-column-v1:invalid-offset:"));
        assertEquals(ScoreExpressiveEvent.Strength.UNSPECIFIED, e.strength());
        assertEquals("", e.qualifierText());
        assertEquals(
                List.of("arpeggio", "authored"),
                e.evidence().stream().map(ScoreExpressiveEvent.Evidence::printedText).toList());
        assertEquals(
                List.of(.15f, .16f),
                e.evidence().stream().map(ScoreExpressiveEvent.Evidence::visualX).toList());
    }

    @Test
    public void maximumStartAnchorCannotEscapeGuard() {
        unresolved(
                ScoreDynamicContinuation.offsetEvidence(
                        source(
                                "0",
                                Optional.of(new ScoreAnchor(Integer.MAX_VALUE, 0)),
                                Optional.empty()),
                        1,
                        7));
    }

    @Test
    public void maximumEndAnchorCannotEscapeGuard() {
        unresolved(
                ScoreDynamicContinuation.offsetEvidence(
                        source(
                                "0",
                                Optional.of(new ScoreAnchor(0, 0)),
                                Optional.of(new ScoreAnchor(Integer.MAX_VALUE, 1))),
                        1,
                        7));
    }

    @Test
    public void malformedFallbackWithMaximumAnchorCannotEscapeGuard() {
        unresolved(
                ScoreDynamicContinuation.offsetEvidence(
                        source(
                                "not-an-integer",
                                Optional.of(new ScoreAnchor(Integer.MAX_VALUE, 0)),
                                Optional.empty()),
                        1,
                        7));
    }

    @Test
    public void nonOverflowingMalformedFallbackKeepsExistingBehaviorAndCannotResolve() {
        var e =
                ScoreDynamicContinuation.offsetEvidence(
                        source(
                                "not-an-integer",
                                Optional.of(new ScoreAnchor(0, 0)),
                                Optional.empty()),
                        1,
                        7);
        assertEquals(1, e.start().orElseThrow().measureIndex());
        assertEquals("owned-original", e.eventId());
        assertEquals(
                List.of(0, 0),
                e.evidence().stream().map(ScoreExpressiveEvent.Evidence::pageIndex).toList());
        var s = score(1).withExpressiveEvents(List.of(e));
        assertTrue(ScoreArpeggioNotation.targetIndices(s, e).isEmpty());
        assertTrue(ScoreArpeggioNotation.plan(s, List.of(0, 1, 2), 4).isEmpty());
        assertTrue(ScoreArpeggioNotation.resolve(s, 4).expressiveEvents().get(0).start().isEmpty());
    }

    @Test
    public void ordinaryValidOffsetRetainsCompleteTargetAndAllEvidence() {
        var old = source("0", Optional.of(new ScoreAnchor(0, 0)), Optional.empty());
        var e = ScoreDynamicContinuation.offsetEvidence(old, 1, 7);
        assertEquals(1, e.start().orElseThrow().measureIndex());
        assertEquals(
                List.of(7, 7),
                e.evidence().stream().map(ScoreExpressiveEvent.Evidence::pageIndex).toList());
        assertTrue(e.targetEventId().orElseThrow().startsWith("arpeggio-column-v1:1:"));
        assertEquals(List.of(0, 1, 2), ScoreArpeggioNotation.targetIndices(score(1), e));
        assertEquals(
                3,
                ScoreArpeggioNotation.plan(
                                score(1).withExpressiveEvents(List.of(e)), List.of(0, 1, 2), 4)
                        .size());
        assertEquals(
                List.of(0, 0),
                old.evidence().stream().map(ScoreExpressiveEvent.Evidence::pageIndex).toList());
    }
}
