// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Original synthetic timing evidence, independent of private score material. */
public final class NumericTempoEndpointTest {
    static ScoreExpressiveEvent event(Kind kind, Double end) {
        return new ScoreExpressiveEvent(
                "ramp",
                kind,
                Optional.of(new ScoreAnchor(0, 0)),
                end == null
                        ? Optional.empty()
                        : Optional.of(new ScoreAnchor((int) (end / 4), end % 4)),
                Scope.SCORE,
                0,
                1,
                Optional.empty(),
                Strength.UNSPECIFIED,
                "",
                List.of(new Evidence("original-synthetic", 0, .5f, 0, 1, kind.name())));
    }

    static void check(
            String name, Kind kind, Double end, List<ScoreTempoChange> numeric, double expected) {
        var result =
                ScoreExpressivePerformance.resolve(
                        100,
                        new ScoreMeterMap(4, List.of()),
                        4,
                        numeric,
                        List.of(event(kind, end)),
                        List.of(),
                        Map.of(),
                        ScoreExpressivePerformance.Policy.preview());
        var first = result.timeline().tempoSegments().get(0);
        if (Math.abs(first.endBpm() - expected) > 1e-8)
            throw new AssertionError(name + ": expected " + expected + " got " + first);
        System.out.println("PASS " + name);
    }

    @org.junit.Test
    public void writtenTargetsAndFallbackBoundaries() {
        check(
                "accelerando explicit endpoint",
                Kind.ACCELERANDO,
                8.,
                List.of(new ScoreTempoChange(2, 0, 110)),
                110);
        check(
                "ritardando explicit endpoint",
                Kind.RITARDANDO,
                8.,
                List.of(new ScoreTempoChange(2, 0, 90)),
                90);
        check(
                "rallentando explicit endpoint",
                Kind.RALLENTANDO,
                8.,
                List.of(new ScoreTempoChange(2, 0, 85)),
                85);
        check(
                "missing span keeps policy",
                Kind.ACCELERANDO,
                null,
                List.of(new ScoreTempoChange(2, 0, 110)),
                125);
        check(
                "later marking is not endpoint",
                Kind.ACCELERANDO,
                8.,
                List.of(new ScoreTempoChange(3, 0, 110)),
                125);
        check(
                "earlier marking interrupts",
                Kind.ACCELERANDO,
                8.,
                List.of(new ScoreTempoChange(1, 0, 105), new ScoreTempoChange(2, 0, 110)),
                112.5);
        check(
                "opposing target not inferred",
                Kind.ACCELERANDO,
                8.,
                List.of(new ScoreTempoChange(2, 0, 90)),
                125);
        check(
                "fractional endpoint",
                Kind.ACCELERANDO,
                6.,
                List.of(new ScoreTempoChange(1, .5f, 115)),
                115);
    }
}
