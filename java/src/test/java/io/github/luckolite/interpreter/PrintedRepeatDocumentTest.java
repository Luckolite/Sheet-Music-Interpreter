// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedRepeatDocumentTest {
    private ScoreExpressiveEvent sign(String type, int page, int start, int end, String text) {
        String id =
                (type.startsWith("printed-ending")
                                ? "unresolved-printed-ending:"
                                : "unresolved-printed-repeat:")
                        + page
                        + ":"
                        + start;
        return new ScoreExpressiveEvent(
                id,
                ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                Optional.of(new ScoreAnchor(start, 0)),
                end < 0 ? Optional.empty() : Optional.of(new ScoreAnchor(end, 0)),
                ScoreExpressiveEvent.Scope.UNRESOLVED,
                0,
                2,
                Optional.empty(),
                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                "unresolved",
                List.of(new ScoreExpressiveEvent.Evidence(type + id, page, .5f, 0, 2, text)));
    }

    private ScorePageInterpretation page(int first, ScoreExpressiveEvent... signs) {
        return new ScorePageInterpretation(
                Collections.nCopies(4, new MeasureRegion(.1f, .9f, .1f, .9f)),
                List.of(),
                first,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(signs));
    }

    private List<ScorePageInterpretation> pages(
            int secondFirst, boolean continuation, boolean closing) {
        var a =
                page(
                        1,
                        sign("printed-repeat-start:v1:", 0, 1, -1, ""),
                        sign("printed-ending-span:v1:true:false:", 0, 3, 4, "1."));
        var signs = new ArrayList<ScoreExpressiveEvent>();
        if (closing) signs.add(sign("printed-repeat-end:v1:", 1, 2, -1, ""));
        signs.add(sign("printed-ending-span:v1:true:" + continuation + ":", 1, 2, 3, "2."));
        return List.of(a, page(secondFirst, signs.toArray(ScoreExpressiveEvent[]::new)));
    }

    @Test
    public void crossPageEndingTraversesBothPasses() {
        var result = PrintedRepeatDocument.assemble(pages(5, true, true));
        assertEquals(4, result.directions().size());
        assertEquals(4, result.resolvedIds().size());
        var plan = ScoreNavigationPlan.create(8, result.directions());
        var route = new ArrayList<Integer>();
        for (int i = 0; i < plan.measureCount(); i++) route.add(plan.sourceMeasure(i));
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 1, 2, 6, 7), route);
        assertEquals(
                1,
                result.directions().stream()
                        .filter(d -> d.kind() == ScorePlaybackDirection.Kind.REPEAT_END)
                        .findFirst()
                        .orElseThrow()
                        .details()
                        .evidence()
                        .get(0)
                        .pageIndex());
    }

    @Test
    public void discontinuousPartsDoNotJoin() {
        assertTrue(PrintedRepeatDocument.assemble(pages(1, true, true)).directions().isEmpty());
    }

    @Test
    public void absentContinuationDoesNotGuessSpan() {
        assertTrue(PrintedRepeatDocument.assemble(pages(5, false, true)).directions().isEmpty());
    }

    @Test
    public void missingClosingRepeatStaysUnresolved() {
        assertTrue(PrintedRepeatDocument.assemble(pages(5, true, false)).directions().isEmpty());
    }

    @Test
    public void contradictoryStaffOwnershipStaysUnresolved() {
        var original = pages(5, true, true);
        var second = original.get(1);
        var events = new ArrayList<ScoreExpressiveEvent>(second.expressiveEvents());
        var e = events.get(0);
        events.set(
                0,
                new ScoreExpressiveEvent(
                        e.eventId(),
                        e.kind(),
                        e.start(),
                        e.end(),
                        e.scope(),
                        0,
                        1,
                        e.targetEventId(),
                        e.strength(),
                        e.qualifierText(),
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        e.evidence().get(0).sourceId(), 1, .5f, 0, 1, ""))));
        assertTrue(
                PrintedRepeatDocument.assemble(
                                List.of(original.get(0), second.withExpressiveEvents(events)))
                        .directions()
                        .isEmpty());
    }

    @Test
    public void legacyEvidenceWithoutAnchorsStaysUnresolved() {
        var original = pages(5, true, true);
        var first = original.get(0);
        var events = new ArrayList<ScoreExpressiveEvent>(first.expressiveEvents());
        var e = events.get(0);
        events.set(
                0,
                new ScoreExpressiveEvent(
                        e.eventId(),
                        e.kind(),
                        Optional.empty(),
                        Optional.empty(),
                        e.scope(),
                        0,
                        2,
                        e.targetEventId(),
                        e.strength(),
                        e.qualifierText(),
                        e.evidence()));
        assertTrue(
                PrintedRepeatDocument.assemble(
                                List.of(first.withExpressiveEvents(events), original.get(1)))
                        .directions()
                        .isEmpty());
    }

    @Test
    public void existingSameRepeatIsNotDuplicated() {
        var pages = pages(5, true, true);
        var existing = PrintedRepeatDocument.assemble(pages).directions();
        var result =
                PrintedRepeatDocument.validated(
                        pages, existing, 8, new ScoreMeterMap(4, List.of()));
        assertTrue(result.directions().isEmpty());
        assertTrue(result.resolvedIds().isEmpty());
    }

    @Test
    public void independentLocalRepeatSurvives() {
        var local =
                PrintedRepeatNavigation.assemble(
                                List.of(
                                        new PrintedRepeatGlyphs.Mark(0, false, .2f, 0, 2),
                                        new PrintedRepeatGlyphs.Mark(1, true, .4f, 0, 2)),
                                List.of(),
                                99)
                        .directions();
        var added =
                PrintedRepeatDocument.validated(
                        pages(5, true, true), local, 8, new ScoreMeterMap(4, List.of()));
        assertEquals(4, added.directions().size());
        var all = new ArrayList<>(local);
        all.addAll(added.directions());
        var plan = ScoreNavigationPlan.create(8, all);
        var route = new ArrayList<Integer>();
        for (int i = 0; i < plan.measureCount(); i++) route.add(plan.sourceMeasure(i));
        assertEquals(List.of(0, 0, 1, 2, 3, 4, 5, 1, 2, 6, 7), route);
    }

    @Test
    public void semanticWirePreservesSpansAndContinuationProof() throws Exception {
        var original = pages(5, true, true);
        var decoded = new ArrayList<ScorePageInterpretation>();
        for (var page : original) {
            var bytes = new java.io.ByteArrayOutputStream();
            ScoreSemanticWire.writeExpressions(
                    new java.io.DataOutputStream(bytes), page.expressiveEvents(), 4);
            var expressions =
                    ScoreSemanticWire.readExpressions(
                            new java.io.DataInputStream(
                                    new java.io.ByteArrayInputStream(bytes.toByteArray())),
                            4);
            assertEquals(page.expressiveEvents(), expressions);
            decoded.add(page.withExpressiveEvents(expressions));
        }
        var expected = PrintedRepeatDocument.assemble(original);
        var result = PrintedRepeatDocument.assemble(decoded);
        assertEquals(expected, result);
        var bytes = new java.io.ByteArrayOutputStream();
        ScoreSemanticWire.writeDirections(
                new java.io.DataOutputStream(bytes), result.directions(), 8);
        assertEquals(
                result.directions(),
                ScoreSemanticWire.readDirections(
                        new java.io.DataInputStream(
                                new java.io.ByteArrayInputStream(bytes.toByteArray())),
                        8));
    }
}
