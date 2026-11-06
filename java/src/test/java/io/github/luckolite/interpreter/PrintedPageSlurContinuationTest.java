// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Optional;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedPageSlurContinuationTest {
    private ScorePageInterpretation page(int physical, int number, boolean outgoing,
            boolean above, float phase, int owner) {
        var arc = new ScoreSlurSpan.ArcEvidence(physical, outgoing ? .6f : .1f, .4f,
                outgoing ? .9f : .4f, .42f);
        var event = PrintedPageSlurContinuation.evidence(
                physical, owner, 0, 1, above, outgoing, phase, arc);
        var notes = List.of(
                new ScoreNoteEvent(0, .2f, 1, 0, 1, .4f, false, 0, 1, 2, 0),
                new ScoreNoteEvent(0, .7f, 3, 0, 1, .4f, false, 0, 1, 2, 0));
        return new ScorePageInterpretation(List.of(new MeasureRegion(.1f, .9f, .3f, .52f)),
                notes, number, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(event))
                .withSlurSource(new ScoreSlurSource(List.of(), List.of(
                        Optional.of(new ScoreSlurPages.Anchor(physical, 0)),
                        Optional.of(new ScoreSlurPages.Anchor(physical, 1)))));
    }

    @Test
    public void joinsAdjacentProvedFragmentsAndRetainsTheirPhysicalAnchors() {
        var a = page(4, 8, true, false, -5, 1);
        var b = page(5, 9, false, false, -2.2f, 0);
        var joined = ScoreSlurSource.joinInterpreted(List.of(a, b));
        assertEquals(1, joined.candidates().size());
        var slur = joined.candidates().get(0);
        assertEquals(new ScoreSlurPages.Anchor(4, 1), slur.start());
        assertEquals(new ScoreSlurPages.Anchor(5, 0), slur.end());
        assertEquals(2, slur.evidence().size());
        assertEquals(4, joined.noteOrigins().size());
    }

    @Test
    public void rejectsOpposedDirectionsAndMissingOrAmbiguousFragments() {
        var a = page(4, 8, true, false, -5, 1);
        var b = page(5, 9, false, false, -3, 0);
        assertTrue(ScoreSlurSource.joinInterpreted(List.of(a,
                page(5, 9, false, true, 3, 0))).candidates().isEmpty());
        assertTrue(ScoreSlurSource.joinInterpreted(List.of(a,
                page(6, 9, false, false, -3, 0))).candidates().isEmpty());
        assertTrue(ScoreSlurSource.joinInterpreted(List.of(a,
                b.withExpressiveEvents(List.of()))).candidates().isEmpty());
        var two = b.withExpressiveEvents(List.of(b.expressiveEvents().get(0),
                page(5, 9, false, false, -3, 1).expressiveEvents().get(0)));
        assertTrue(ScoreSlurSource.joinInterpreted(List.of(a, two)).candidates().isEmpty());
    }
}
