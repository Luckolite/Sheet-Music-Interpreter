// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Printed cue text owns columns independently of optical width, tempo, and performance. */
public final class CueCaptionOwnershipOriginalTest {
    static ScorePageInterpretation acquire() {
        var e = PrintedCueSizeOriginalTest.example(true);
        var ps = PrintedCueSizeOriginalTest.detect(e);
        return PrintedCueSize.apply(e.score(), ps);
    }

    @Test
    public void twoPrintedWordsRetainSourceTextAndExactColumnOwners() {
        var s = acquire();
        assertEquals(2, s.expressiveEvents().size());
        assertEquals("Guitar", s.expressiveEvents().get(0).evidence().get(0).printedText());
        assertEquals("Play", s.expressiveEvents().get(1).evidence().get(0).printedText());
        assertEquals(
                List.of(0), ScoreExpressionDetector.targetIndices(s, s.expressiveEvents().get(0)));
        assertEquals(
                List.of(3), ScoreExpressionDetector.targetIndices(s, s.expressiveEvents().get(1)));
    }

    @Test
    public void onlyProvedWrittenClockMakesTheCaptionsPartScoped() {
        var s = acquire();
        assertTrue(s.expressiveEvents().stream().allMatch(e -> e.start().isEmpty()));
        var resolved = ScoreExpressionDetector.resolve(s, 4);
        assertEquals(
                0, resolved.expressiveEvents().get(0).start().orElseThrow().quarterBeatOffset(), 0);
        assertEquals(
                1.5,
                resolved.expressiveEvents().get(1).start().orElseThrow().quarterBeatOffset(),
                0);
        for (var e : resolved.expressiveEvents())
            assertEquals(ScoreExpressiveEvent.Scope.PART, e.scope());
    }

    @Test
    public void repeatedAcquisitionDoesNotDuplicateCaptionEvidence() {
        var e = PrintedCueSizeOriginalTest.example(true);
        var ps = PrintedCueSizeOriginalTest.detect(e);
        var once = PrintedCueSize.apply(e.score(), ps);
        assertEquals(once, PrintedCueSize.apply(once, ps));
    }

    @Test
    public void sharedWordsWithoutCueProvenanceDoNotGainCueOwnership() {
        var e = acquire().expressiveEvents().get(0);
        var old = e.evidence().get(0);
        var generic =
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
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "printed-expression-word",
                                        0,
                                        old.visualX(),
                                        old.staffIndex(),
                                        old.staffCount(),
                                        old.printedText())));
        assertFalse(PrintedCueSize.owns(generic));
        assertTrue(PrintedCueSize.owns(e));
    }
}
