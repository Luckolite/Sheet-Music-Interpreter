// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete authored columns; no commercial score material. */
public final class ArpeggioAssemblyTest {
    static ScorePageInterpretation local() {
        var ns = new ArrayList<ScoreNoteEvent>();
        for (int step : new int[] {0, 2, 4})
            ns.add(new ScoreNoteEvent(0, .2f, step, 0, 1, .3f, false, 0, 0, 2, 4));
        var s = new ScorePageInterpretation(List.of(new MeasureRegion(.1f, .9f, .2f, .5f)), ns, 1);
        s =
                ScoreArpeggioNotation.capture(
                        s,
                        List.of(0, 1, 2),
                        new ScoreExpressiveEvent.Evidence(
                                "printed-arpeggio-wave", 0, .15f, 0, 1, "arpeggio"));
        var e = s.expressiveEvents().get(0);
        var es = new ArrayList<>(e.evidence());
        es.add(new ScoreExpressiveEvent.Evidence("independent-support", 0, .16f, 0, 1, "authored"));
        return s.withExpressiveEvents(
                List.of(
                        new ScoreExpressiveEvent(
                                e.eventId(),
                                e.kind(),
                                e.start(),
                                e.end(),
                                e.scope(),
                                e.staffIndex(),
                                e.staffCount(),
                                e.targetEventId(),
                                e.strength(),
                                e.qualifierText(),
                                es)));
    }

    static ScorePageInterpretation joined(ScoreExpressiveEvent e) {
        var ns = new ArrayList<ScoreNoteEvent>();
        for (int step : new int[] {0, 2, 4})
            ns.add(new ScoreNoteEvent(3, .2f, step, 0, 1, .3f, false, 0, 0, 2, 4));
        return new ScorePageInterpretation(
                        Collections.nCopies(4, new MeasureRegion(.1f, .9f, .2f, .5f)), ns, 1)
                .withExpressiveEvents(List.of(e));
    }

    static ScoreExpressiveEvent target(ScoreExpressiveEvent e, String t) {
        return new ScoreExpressiveEvent(
                e.eventId(),
                e.kind(),
                e.start(),
                e.end(),
                e.scope(),
                e.staffIndex(),
                e.staffCount(),
                Optional.of(t),
                e.strength(),
                e.qualifierText(),
                e.evidence());
    }

    @Test
    public void pageOffsetRebasesOpaqueTargetAndEveryEvidenceTogether() {
        var source = local();
        var old = source.expressiveEvents().get(0);
        var e = ScoreDynamicContinuation.offsetEvidence(old, 3, 7);
        assertTrue(e.targetEventId().orElseThrow().startsWith("arpeggio-column-v1:3:"));
        assertTrue(e.eventId().startsWith("page:7/"));
        assertEquals(
                List.of(7, 7),
                e.evidence().stream().map(ScoreExpressiveEvent.Evidence::pageIndex).toList());
        assertEquals(List.of(0, 1, 2), ScoreArpeggioNotation.targetIndices(joined(e), e));
        assertEquals(3, ScoreArpeggioNotation.plan(joined(e), List.of(0, 1, 2), 4).size());
        assertEquals(
                List.of(0, 0),
                old.evidence().stream().map(ScoreExpressiveEvent.Evidence::pageIndex).toList());
    }

    @Test
    public void resolvedAttackRebasesWithoutResolvingAgainstOldList() {
        var old = ScoreArpeggioNotation.resolve(local(), 4).expressiveEvents().get(0);
        var e = ScoreDynamicContinuation.offsetEvidence(old, 3, 7);
        assertEquals(3, e.start().orElseThrow().measureIndex());
        assertEquals(List.of(0, 1, 2), ScoreArpeggioNotation.targetIndices(joined(e), e));
        assertTrue(ScoreArpeggioNotation.targetIndices(local(), e).isEmpty());
    }

    @Test
    public void availableSubsetCannotAcquirePartialSymbol() {
        var e = ScoreDynamicContinuation.offsetEvidence(local().expressiveEvents().get(0), 3, 7);
        assertTrue(ScoreArpeggioNotation.plan(joined(e), List.of(0, 1), 4).isEmpty());
    }

    @Test
    public void malformedAndWrongStaffTargetsStayUnresolved() {
        var e = local().expressiveEvents().get(0);
        for (var t :
                List.of(
                        "arpeggio-column-v1:garbage",
                        "arpeggio-column-v1:bad:0:1:0x1.99999ap-3:0,2,4",
                        "arpeggio-column-v1:0:1:2:0x1.99999ap-3:0,2,4")) {
            var shifted = ScoreDynamicContinuation.offsetEvidence(target(e, t), 3, 7);
            assertTrue(ScoreArpeggioNotation.targetIndices(joined(shifted), shifted).isEmpty());
        }
    }

    @Test
    public void independentAuthoredDirectionKeepsItsIdentity() {
        var e =
                new ScoreExpressiveEvent(
                        "author",
                        ScoreExpressiveEvent.Kind.ARPEGGIO,
                        Optional.of(new ScoreAnchor(0, 0)),
                        Optional.empty(),
                        ScoreExpressiveEvent.Scope.NOTE,
                        0,
                        1,
                        Optional.empty(),
                        ScoreExpressiveEvent.Strength.UNSPECIFIED,
                        "roll freely",
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "author", 0, .2f, 0, 1, "roll freely")));
        var shifted = ScoreDynamicContinuation.offsetEvidence(e, 3, 7);
        assertEquals("author", shifted.eventId());
        assertEquals(3, shifted.start().orElseThrow().measureIndex());
        assertEquals(0, shifted.evidence().get(0).pageIndex());
    }
}
