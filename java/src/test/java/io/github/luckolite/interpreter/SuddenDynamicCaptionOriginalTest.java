// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SuddenDynamicCaptionOriginalTest {
    static ScoreExpressiveEvent event(
            String id, String text, float position, int staff, int count, String source) {
        return new ScoreExpressiveEvent(
                id,
                ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                Optional.of(new ScoreAnchor(0, 0)),
                Optional.empty(),
                ScoreExpressiveEvent.Scope.PART,
                staff,
                count,
                Optional.of(
                        "expression-column:0:"
                                + staff
                                + ":"
                                + count
                                + ":"
                                + Float.floatToIntBits(position)),
                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                "",
                List.of(
                        new ScoreExpressiveEvent.Evidence(
                                source, 0, position, staff, count, text)));
    }

    static ScorePageInterpretation score(List<ScoreExpressiveEvent> es) {
        var ns =
                List.of(
                        new ScoreNoteEvent(0, .17f, 0, 0, 1, .4f, false, 0, 0, 0, 3, 1, 0),
                        new ScoreNoteEvent(0, .86f, 2, 0, 1, .4f, false, 0, 0, 0, 1, 1, 0));
        return new ScorePageInterpretation(
                        List.of(new MeasureRegion(.1f, .9f, .2f, .6f)),
                        ns,
                        1,
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 4, 4)),
                        List.of(),
                        List.of(),
                        List.of(new ScoreDynamicChange(0, .86f, 0, 1, 0, .86f, -8, 0)))
                .withExpressiveEvents(es);
    }

    static ScoreExpressiveEvent suffix() {
        return event("original-suffix", "p sub.", .86f, 0, 1, "printed-expression-word");
    }

    @Test
    public void exactOwnedSuffixBindsToItsDynamic() {
        var plan = ScoreDynamicCaption.plan(score(List.of(suffix())), 0, Map.of(), 4);
        assertEquals(1, plan.size());
        assertEquals("sub.", plan.get(0).text().qualifier());
        assertFalse(plan.get(0).text().before());
    }

    @Test
    public void matchingBeatWithoutMatchingSourceColumnCannotBind() {
        var e = event("other-column", "p sub.", .17f, 0, 1, "printed-expression-word");
        assertTrue(ScoreDynamicCaption.plan(score(List.of(e)), 0, Map.of(), 4).isEmpty());
    }

    @Test
    public void foreignPartCannotSupplyTheCaption() {
        var e = event("other-part", "p sub.", .86f, 1, 2, "printed-expression-word");
        assertTrue(ScoreDynamicCaption.plan(score(List.of(e)), 0, Map.of(), 4).isEmpty());
    }

    @Test
    public void bareTextWithoutPrintedOwnershipCannotBind() {
        var e = event("foreign-source", "p sub.", .86f, 0, 1, "manual-note");
        assertTrue(ScoreDynamicCaption.plan(score(List.of(e)), 0, Map.of(), 4).isEmpty());
    }

    @Test
    public void conflictingCaptionsAtOneColumnRemainSeparate() {
        var other = event("other-label", "subito p", .86f, 0, 1, "printed-expression-word");
        assertTrue(
                ScoreDynamicCaption.plan(score(List.of(suffix(), other)), 0, Map.of(), 4)
                        .isEmpty());
    }

    @Test
    public void wrongNumericLevelCannotBorrowModifier() {
        var e = event("different-level", "f sub.", .86f, 0, 1, "printed-expression-word");
        assertTrue(ScoreDynamicCaption.plan(score(List.of(e)), 0, Map.of(), 4).isEmpty());
    }

    @Test
    public void editedClockUsesOwnershipRatherThanStaleEventAnchor() {
        var ps =
                Map.of(
                        0,
                        new ScoreNoteTiming.WrittenPlacement(0, 2, -1, -1, false, 0),
                        1,
                        new ScoreNoteTiming.WrittenPlacement(2, 2, -1, -1, false, 0));
        assertEquals(1, ScoreDynamicCaption.plan(score(List.of(suffix())), 0, ps, 4).size());
    }

    @Test
    public void ordinaryCharacterTextDoesNotBecomeADynamicModifier() {
        var e = event("character", "sweetly", .86f, 0, 1, "printed-expression-word");
        assertTrue(ScoreDynamicCaption.plan(score(List.of(e)), 0, Map.of(), 4).isEmpty());
    }

    @Test
    public void resolvedSuddenCaptionRemainsPartLocal() {
        var p = ScoreExpressionDetector.resolve(score(List.of(suffix())), 4);
        assertEquals(ScoreExpressiveEvent.Scope.PART, p.expressiveEvents().get(0).scope());
    }

    @Test
    public void ordinaryCharacterScopeStillRemainsScoreWide() {
        var e = event("character", "sweetly", .86f, 0, 1, "printed-expression-word");
        var p = ScoreExpressionDetector.resolve(score(List.of(e)), 4);
        assertEquals(ScoreExpressiveEvent.Scope.SCORE, p.expressiveEvents().get(0).scope());
    }

    static ScorePageInterpretation terminal() {
        var ns =
                List.of(
                        new ScoreNoteEvent(0, .17f, 0, 0, 1, .4f, false, 0, 0, 0, 4, 1, 0),
                        new ScoreNoteEvent(0, .86f, 2, 0, 1, .4f, false, 0, 0, 0, .125f, 1, 0)
                                .withArticulations(NoteOrnament.GRACE));
        return new ScorePageInterpretation(
                        List.of(new MeasureRegion(.1f, .9f, .2f, .6f)),
                        ns,
                        1,
                        List.of(),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 4, 4)),
                        List.of(),
                        List.of(),
                        List.of(new ScoreDynamicChange(0, .86f, 0, 1, 0, .86f, -8, 0)))
                .withExpressiveEvents(List.of(suffix()));
    }

    static Map<Integer, ScoreNoteTiming.WrittenPlacement> terminalWritten() {
        return Map.of(
                0,
                new ScoreNoteTiming.WrittenPlacement(0, 4, -1, -1, false, 0),
                1,
                new ScoreNoteTiming.WrittenPlacement(4, .125, 0, 0, true, 3.125));
    }

    @Test
    public void ownedAfterGraceCaptionCanUseItsWrittenBarRelease() {
        assertEquals(1, ScoreDynamicCaption.plan(terminal(), 0, terminalWritten(), 4).size());
    }
}
