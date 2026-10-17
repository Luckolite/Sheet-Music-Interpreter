// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ArpeggioOffsetOverflowTest {
    static final String VALID = "arpeggio-column-v1:0:0:2:0x1.666666p-2:0,3,7";

    static ScoreExpressiveEvent event(String target, boolean anchored) {
        return new ScoreExpressiveEvent(
                "original-optical",
                ScoreExpressiveEvent.Kind.ARPEGGIO,
                anchored ? Optional.of(new ScoreAnchor(Integer.MAX_VALUE, 0)) : Optional.empty(),
                anchored ? Optional.of(new ScoreAnchor(Integer.MAX_VALUE, 1)) : Optional.empty(),
                anchored ? ScoreExpressiveEvent.Scope.NOTE : ScoreExpressiveEvent.Scope.UNRESOLVED,
                0,
                2,
                Optional.of(target),
                ScoreExpressiveEvent.Strength.POCO,
                "retained qualifier",
                List.of(
                        new ScoreExpressiveEvent.Evidence(
                                "printed-arpeggio-wave", 3, .24f, 0, 2, "arpeggio")));
    }

    static void invalid(ScoreExpressiveEvent before, ScoreExpressiveEvent after) {
        assertEquals("page:9/" + before.eventId(), after.eventId());
        assertTrue(ScoreArpeggioNotation.owns(after));
        assertTrue(after.start().isEmpty());
        assertTrue(after.end().isEmpty());
        assertEquals(ScoreExpressiveEvent.Scope.UNRESOLVED, after.scope());
        assertEquals(
                "arpeggio-column-v1:invalid-offset:" + before.targetEventId().orElseThrow(),
                after.targetEventId().orElseThrow());
        assertEquals(before.kind(), after.kind());
        assertEquals(before.strength(), after.strength());
        assertEquals(before.qualifierText(), after.qualifierText());
        assertEquals(9, after.evidence().get(0).pageIndex());
        assertEquals(before.evidence().get(0).sourceId(), after.evidence().get(0).sourceId());
        assertEquals(before.evidence().get(0).printedText(), after.evidence().get(0).printedText());
    }

    @Test
    public void targetMaxPlusPositiveOffsetRetainsUnresolvedInvalidEvidence() {
        var e = event(VALID.replace(":0:0:2:", ":" + Integer.MAX_VALUE + ":0:2:"), false);
        invalid(e, ScoreArpeggioNotation.offsetEvidence(e, 1, 9));
    }

    @Test
    public void validTargetButAnchoredOverflowCannotFallbackToEventOffset() {
        var e = event(VALID, true);
        invalid(e, ScoreArpeggioNotation.offsetEvidence(e, 1, 9));
    }

    @Test
    public void targetAndBothAnchorOverflowRetainNoGuessedAnchor() {
        var e = event(VALID.replace(":0:0:2:", ":" + Integer.MAX_VALUE + ":0:2:"), true);
        invalid(e, ScoreArpeggioNotation.offsetEvidence(e, 1, 9));
    }

    @Test
    public void malformedFieldsAndNonnumericTargetAnchoredOverflowAlsoFailClosed() {
        for (String t :
                List.of("arpeggio-column-v1:bad", "arpeggio-column-v1:nope:0:2:0x1.0p-2:0,3,7")) {
            var e = event(t, true);
            invalid(e, ScoreArpeggioNotation.offsetEvidence(e, 1, 9));
        }
    }

    @Test
    public void validAnchoredOffsetKeepsExactExistingConstruction() {
        var e =
                new ScoreExpressiveEvent(
                        "valid",
                        ScoreExpressiveEvent.Kind.ARPEGGIO,
                        Optional.of(new ScoreAnchor(0, .5)),
                        Optional.of(new ScoreAnchor(0, 2)),
                        ScoreExpressiveEvent.Scope.NOTE,
                        0,
                        2,
                        Optional.of(VALID),
                        ScoreExpressiveEvent.Strength.POCO,
                        "q",
                        event(VALID, false).evidence());
        var actual = ScoreArpeggioNotation.offsetEvidence(e, 2, 9);
        var expected =
                new ScoreExpressiveEvent(
                        "page:9/valid",
                        e.kind(),
                        Optional.of(new ScoreAnchor(2, .5)),
                        Optional.of(new ScoreAnchor(2, 2)),
                        e.scope(),
                        0,
                        2,
                        Optional.of(VALID.replace(":0:0:2:", ":2:0:2:")),
                        e.strength(),
                        e.qualifierText(),
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "printed-arpeggio-wave", 9, .24f, 0, 2, "arpeggio")));
        assertEquals(expected, actual);
    }

    @Test
    public void existingMalformedWithoutOverflowRemainsExactGenericOffset() {
        for (String t :
                List.of(
                        "arpeggio-column-v1:bad",
                        "arpeggio-column-v1:nope:0:2:0x1.0p-2:0,3,7",
                        "arpeggio-column-v1:2147483648:0:2:0x1.0p-2:0,3,7")) {
            var e = event(t, false);
            assertEquals(e.offset(2), ScoreArpeggioNotation.offsetEvidence(e, 2, 9));
        }
    }

    @Test
    public void validUnanchoredOffsetKeepsOriginalIdentityFields() {
        var e = event(VALID, false);
        var q = ScoreArpeggioNotation.offsetEvidence(e, 5, 9);
        assertEquals("page:9/original-optical", q.eventId());
        assertEquals(VALID.replace(":0:0:2:", ":5:0:2:"), q.targetEventId().orElseThrow());
        assertTrue(q.start().isEmpty());
        assertEquals(e.scope(), q.scope());
    }

    @Test
    public void sentinelCannotResolveOrPlanAnyChordMembers() {
        var e = event(VALID, true);
        var invalid = ScoreArpeggioNotation.offsetEvidence(e, 1, 9);
        var page = new ScoreArpeggioTest().page().withExpressiveEvents(List.of(invalid));
        assertTrue(ScoreArpeggioNotation.targetIndices(page, invalid).isEmpty());
        assertTrue(ScoreArpeggioNotation.plan(page, List.of(0, 1, 2, 3), 4).isEmpty());
        var q = ScoreArpeggioNotation.resolve(page, 4);
        assertEquals(List.of(invalid), q.expressiveEvents());
    }
}
