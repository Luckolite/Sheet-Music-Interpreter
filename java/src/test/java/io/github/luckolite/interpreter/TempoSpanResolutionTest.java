// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

public class TempoSpanResolutionTest {
    ScorePageInterpretation score() {
        var notes = new ArrayList<ScoreNoteEvent>();
        var measures = new ArrayList<MeasureRegion>();
        for (int m = 0; m < 4; m++) {
            measures.add(new MeasureRegion(.1f, .9f, .1f + m * .2f, .25f + m * .2f));
            for (float x : new float[] {.15f, .35f, .55f, .75f})
                notes.add(new ScoreNoteEvent(m, x, 0, 0, 1, .2f, false, 0, 0, 0, 1, 1, 0));
        }
        return new ScorePageInterpretation(
                measures,
                notes,
                1,
                List.of(),
                List.of(),
                List.of(new ScoreMeterChange(0, 4, 4)),
                List.of(),
                List.of(),
                List.of());
    }

    ScoreExpressiveEvent event(boolean proof, double onset) {
        var evidence = new ArrayList<Evidence>();
        evidence.add(new Evidence("printed-expression-word", 0, .2f, 0, 1, "rit"));
        if (proof)
            evidence.add(
                    new Evidence(
                            "printed-tempo-dash-span", 0, .7f, 0, 1, "closed printed extension"));
        return new ScoreExpressiveEvent(
                "ramp",
                Kind.RITARDANDO,
                Optional.of(new ScoreAnchor(0, onset)),
                Optional.of(new ScoreAnchor(1, 0)),
                Scope.SCORE,
                0,
                1,
                Optional.of("expression-column:0:0:1:" + Float.floatToIntBits(.15f)),
                Strength.UNSPECIFIED,
                "",
                evidence);
    }

    ScoreExpressiveEvent resolve(ScoreExpressiveEvent event) {
        return ScoreExpressionDetector.resolve(score().withExpressiveEvents(List.of(event)), 4)
                .expressiveEvents()
                .get(0);
    }

    @Test
    public void repeatedResolutionKeepsProvedEndpoint() {
        assertEquals(Optional.of(new ScoreAnchor(1, 0)), resolve(event(true, 0)).end());
    }

    @Test
    public void pageOffsetKeepsBothAnchors() {
        var shifted = ScoreExpressionDetector.offsetEvidence(event(true, 0), 2, 1);
        var result = resolve(shifted);
        assertEquals(Optional.of(new ScoreAnchor(2, 0)), result.start());
        assertEquals(Optional.of(new ScoreAnchor(3, 0)), result.end());
    }

    @Test
    public void changedStartDoesNotRetainStaleEndpoint() {
        assertTrue(resolve(event(true, .5)).end().isEmpty());
    }

    @Test
    public void UnprovedEndpointIsNotPreserved() {
        assertTrue(resolve(event(false, 0)).end().isEmpty());
    }
}
