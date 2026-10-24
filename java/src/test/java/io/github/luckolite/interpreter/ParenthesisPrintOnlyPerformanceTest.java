// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Procedural regression: printed optional brackets do not mute or alter playback. */
public class ParenthesisPrintOnlyPerformanceTest {
    private ScoreExpressiveEvent group(boolean resolved) {
        var members =
                List.of(
                        new ScoreParenthesizedNoteGroup.Member(
                                0,
                                0,
                                1,
                                Float.floatToRawIntBits(.2f),
                                4,
                                Float.floatToRawIntBits(.6f)),
                        new ScoreParenthesizedNoteGroup.Member(
                                0,
                                0,
                                1,
                                Float.floatToRawIntBits(.4f),
                                9,
                                Float.floatToRawIntBits(.5f)),
                        new ScoreParenthesizedNoteGroup.Member(
                                0,
                                0,
                                1,
                                Float.floatToRawIntBits(.7f),
                                8,
                                Float.floatToRawIntBits(.52f)));
        var pair =
                new ScoreParenthesizedNoteGroup.Pair(members, -1.2f, -.1f, 1.1f, 0, 1.75f, 1.75f);
        return new ScoreExpressiveEvent(
                "printed-optional-group",
                Kind.PARENTHESIZED_NOTE_GROUP,
                resolved ? Optional.of(new ScoreAnchor(0, 0)) : Optional.empty(),
                Optional.empty(),
                resolved ? Scope.NOTE : Scope.UNRESOLVED,
                0,
                1,
                Optional.of(pair.target()),
                Strength.UNSPECIFIED,
                pair.qualifier(),
                List.of(
                        new Evidence(
                                ScoreParenthesizedNoteGroup.SOURCE, 0, .2f, 0, 1, "( notes )")));
    }

    private ScoreExpressivePerformance.Result run(List<ScoreExpressiveEvent> marks) {
        return ScoreExpressivePerformance.resolve(
                120,
                new ScoreMeterMap(4, List.of()),
                2,
                List.of(new ScoreTempoChange(1, 0, 128, 1)),
                marks,
                List.of(
                        new ScoreExpressivePerformance.Sound("a", 0, .5, 0, 1),
                        new ScoreExpressivePerformance.Sound("b", .5, 1, 0, 1),
                        new ScoreExpressivePerformance.Sound("c", 1, 2, 0, 1)),
                Map.of("held-note", Set.of("c")),
                ScoreExpressivePerformance.Policy.preview());
    }

    private void equivalent(
            ScoreExpressivePerformance.Result a, ScoreExpressivePerformance.Result b) {
        assertEquals(a.attacks(), b.attacks());
        assertEquals(a.holdOwnership(), b.holdOwnership());
        assertEquals(a.diagnostics(), b.diagnostics());
        assertEquals(a.timeline().holds(), b.timeline().holds());
        assertEquals(a.timeline().tempoSegments(), b.timeline().tempoSegments());
        for (double beat : new double[] {0, .5, 1, 1.75, 2, 4, 6, 8})
            for (var boundary : ScorePerformanceTimeline.Boundary.values())
                assertEquals(
                        a.timeline().secondsAtBeat(beat, boundary),
                        b.timeline().secondsAtBeat(beat, boundary),
                        0);
    }

    @Test
    public void unresolvedPrintedGroupDoesNotChangePerformedNotesOrTempo() {
        equivalent(run(List.of()), run(List.of(group(false))));
    }

    @Test
    public void resolvedPrintedGroupDoesNotChangePerformedNotesOrTempo() {
        equivalent(run(List.of()), run(List.of(group(true))));
    }

    @Test
    public void printedGroupPreservesAnIndependentlyResolvedFermata() {
        var hold =
                new ScoreExpressiveEvent(
                        "held-note",
                        Kind.FERMATA,
                        Optional.of(new ScoreAnchor(0, 1)),
                        Optional.of(new ScoreAnchor(0, 2)),
                        Scope.NOTE,
                        0,
                        1,
                        Optional.of("held-source"),
                        Strength.UNSPECIFIED,
                        "",
                        List.of(new Evidence("original-synthetic", 0, .7f, 0, 1, "fermata")));
        var baseline = run(List.of(hold));
        assertFalse(baseline.timeline().holds().isEmpty());
        equivalent(baseline, run(List.of(hold, group(true))));
    }
}
