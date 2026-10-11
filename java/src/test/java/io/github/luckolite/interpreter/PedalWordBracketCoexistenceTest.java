// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original semantic controls for overlapping literal cues and finite bracket evidence. */
public final class PedalWordBracketCoexistenceTest {
    private ScoreExpressiveEvent event(String id, boolean down, double beat, Double end) {
        return new ScoreExpressiveEvent(
                id,
                down ? ScoreExpressiveEvent.Kind.PEDAL_DOWN : ScoreExpressiveEvent.Kind.PEDAL_UP,
                Optional.of(new ScoreAnchor(0, beat)),
                end == null ? Optional.empty() : Optional.of(new ScoreAnchor(0, end)),
                ScoreExpressiveEvent.Scope.PART,
                0,
                1,
                Optional.empty(),
                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                "",
                List.of(
                        new ScoreExpressiveEvent.Evidence(
                                "original-semantic-control", 0, .5f, 0, 1, "")));
    }

    private PedalPerformance.Result resolve(ScoreExpressiveEvent... events) {
        return PedalPerformance.resolve(List.of(events), new ScoreMeterMap(4, List.of()), 1);
    }

    @Test
    public void compatibleLiteralCueCannotCancelFiniteBracketInEitherInputOrder() {
        var bracket = event("bracket", true, 0, 2d);
        var word = event("word", true, 0, null);
        var up = event("up", false, 2, null);
        for (var input :
                List.of(
                        List.of(bracket, word, up),
                        List.of(word, bracket, up),
                        List.of(up, word, bracket))) {
            var result = PedalPerformance.resolve(input, new ScoreMeterMap(4, List.of()), 1);
            assertEquals(result.toString(), 1, result.spans().size());
            assertEquals(0, result.spans().get(0).startBeat(), 0);
            assertEquals(2, result.spans().get(0).endBeat(), 0);
            assertEquals("bracket", result.spans().get(0).sourceId());
        }
    }

    @Test
    public void incompatibleFiniteEndsStayContradictory() {
        assertTrue(
                resolve(
                                event("first", true, 0, 2d),
                                event("second", true, 0, 3d),
                                event("up", false, 2, null))
                        .spans()
                        .isEmpty());
    }

    @Test
    public void wordAndKnownEndWithoutReleaseStillCannotGuessRelease() {
        assertTrue(
                resolve(event("word", true, 0, null), event("bracket", true, 0, 2d))
                        .spans()
                        .isEmpty());
    }

    @Test
    public void aLaterLiteralDownRetainsContradictionInsteadOfExtendingKnownBracket() {
        assertTrue(
                resolve(
                                event("bracket", true, 0, 2d),
                                event("word", true, 1, null),
                                event("up", false, 2, null))
                        .spans()
                        .isEmpty());
    }
}
