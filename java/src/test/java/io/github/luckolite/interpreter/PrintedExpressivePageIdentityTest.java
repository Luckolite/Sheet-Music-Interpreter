// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.Assert.*;

public final class PrintedExpressivePageIdentityTest {
    @Test
    public void identicalPageLocalFalloffsKeepBothIdsAndShiftTheirOwners() {
        check(
                ScoreExpressiveEvent.Kind.FALL_OFF,
                ScoreFallOffNotation.SOURCE,
                "fall-off-note-v1:0:0:1:3f000000:2:3f000000",
                "fall-off-note-v1:4:0:1:3f000000:2:3f000000");
    }

    @Test
    public void identicalPageLocalHarmonicsKeepBothIdsAndShiftTheirOwners() {
        check(
                ScoreExpressiveEvent.Kind.NATURAL_HARMONIC,
                ScoreNaturalHarmonicNotation.SOURCE,
                "natural-harmonic-note-v1:0:0:1:3f000000:2:3f000000",
                "natural-harmonic-note-v1:4:0:1:3f000000:2:3f000000");
    }

    private static void check(
            ScoreExpressiveEvent.Kind kind, String source, String target, String shiftedTarget) {
        var local =
                new ScoreExpressiveEvent(
                        "printed-local:" + target,
                        kind,
                        Optional.empty(),
                        Optional.empty(),
                        ScoreExpressiveEvent.Scope.UNRESOLVED,
                        0,
                        1,
                        Optional.of(target),
                        ScoreExpressiveEvent.Strength.UNSPECIFIED,
                        kind == ScoreExpressiveEvent.Kind.FALL_OFF
                                ? ScoreFallOffNotation.CURVED
                                : "",
                        List.of(new ScoreExpressiveEvent.Evidence(source, 0, .5f, 0, 1, "mark")));
        var first = ScoreDynamicContinuation.offsetEvidence(local, 0, 0);
        var second = ScoreDynamicContinuation.offsetEvidence(local, 4, 1);
        assertNotEquals(first.eventId(), second.eventId());
        assertEquals(target, first.targetEventId().orElseThrow());
        assertEquals(shiftedTarget, second.targetEventId().orElseThrow());
        assertEquals(1, second.evidence().get(0).pageIndex());
        var repeated = ScoreDynamicContinuation.offsetEvidence(second, 0, 1);
        assertEquals(second.eventId(), repeated.eventId());
        assertEquals(second.targetEventId(), repeated.targetEventId());
    }
}
