// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

import java.util.*;
import org.junit.Test;

/** Original synthetic notes only; complete realization and ordered column membership. */
public final class ScoreExpressionLaneIndexTest {
    private static ScoreNoteEvent note(int measure, float position, int staff, int count) {
        return new ScoreNoteEvent(measure, position, 0, staff, count, .5f, false, 0, 0, 0, 1, 1, 0);
    }

    private static ScorePageInterpretation score(List<ScoreNoteEvent> notes) {
        return new ScorePageInterpretation(
                List.of(
                        new MeasureRegion(.04f, .48f, .25f, .65f),
                        new MeasureRegion(.5f, .96f, .25f, .65f)),
                notes,
                1,
                List.of(),
                List.of(),
                List.of(new ScoreMeterChange(0, 4, 4)),
                List.of(),
                List.of(),
                List.of());
    }

    private static ScoreExpressiveEvent event(
            String id, int measure, int staff, int count, float position, Kind kind) {
        return event(
                id,
                staff,
                count,
                kind,
                "printed-expression-word",
                "expression-column:"
                        + measure
                        + ":"
                        + staff
                        + ":"
                        + count
                        + ":"
                        + Float.floatToIntBits(position));
    }

    private static ScoreExpressiveEvent event(
            String id, int staff, int count, Kind kind, String source, String target) {
        return new ScoreExpressiveEvent(
                id,
                kind,
                Optional.empty(),
                Optional.empty(),
                Scope.UNRESOLVED,
                staff,
                count,
                Optional.of(target),
                Strength.UNSPECIFIED,
                "",
                List.of(new Evidence(source, 0, .27f, staff, count, "synthetic direction")));
    }

    @Test
    public void manyColumnsMatchIndependentPreservedSingleColumnRealization() {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (float position : new float[] {.15f, .27f, .65f, .8f})
            for (int measure = 0; measure < 2; measure++) {
                notes.add(note(measure, position, 0, 2));
                notes.add(note(measure, position, 1, 2));
                notes.add(note(measure, position, 0, 1));
            }
        var base = score(notes);
        var columns =
                List.of(
                        event("c0", 0, 0, 2, .27f, Kind.SFORZATO),
                        event("c1", 1, 1, 2, .65f, Kind.BREATH),
                        event("c2", 0, 0, 1, .8f, Kind.RITARDANDO),
                        event("c3", 1, 0, 1, .15f, Kind.CAESURA),
                        event("c4", 0, 1, 2, .27f, Kind.ACCELERANDO),
                        event("c5", 1, 0, 2, .65f, Kind.SFORZANDO_PIANO),
                        event("c6", 0, 0, 3, .27f, Kind.RALLENTANDO),
                        event("c7", 0, 0, 2, .27f, Kind.SFORZANDO));
        var foreign =
                event(
                        "foreign",
                        0,
                        2,
                        Kind.SFORZATO,
                        "synthetic-manual",
                        "expression-column:0:0:2:" + Float.floatToIntBits(.27f));
        var malformed =
                event(
                        "malformed",
                        0,
                        2,
                        Kind.SFORZATO,
                        "printed-expression-word",
                        "expression-column:0:0:2:not-an-int");
        for (int count : new int[] {0, 1, 2, 4, 5, 8}) {
            var incoming = new ArrayList<ScoreExpressiveEvent>();
            incoming.add(foreign);
            incoming.addAll(columns.subList(0, Math.min(2, count)));
            incoming.add(malformed);
            incoming.addAll(columns.subList(Math.min(2, count), count));
            var input = base.withExpressiveEvents(incoming);
            var expected = new ArrayList<ScoreExpressiveEvent>();
            // One-column calls take the unchanged full-scan path and an independent timing session.
            for (var column : incoming)
                expected.add(
                        ScoreExpressionDetector.resolve(
                                        input.withExpressiveEvents(List.of(column)), 4)
                                .expressiveEvents()
                                .get(0));
            var actual = ScoreExpressionDetector.resolve(input, 4);
            assertEquals(input.withExpressiveEvents(expected), actual);
            assertSame(foreign, actual.expressiveEvents().get(0));
            assertSame(malformed, actual.expressiveEvents().get(incoming.indexOf(malformed)));
            assertEquals(incoming, input.expressiveEvents());
            for (int i = 0; i < notes.size(); i++) assertSame(notes.get(i), actual.notes().get(i));
            assertSame(input, ScoreExpressionDetector.resolve(input, 0));
        }
    }

    @Test
    public void indexedMembershipKeepsOrderRawFloatEdgesAndAllLaneFields() throws Exception {
        var notes =
                List.of(
                        note(0, -0f, 0, 2),
                        note(0, 0, 1, 2),
                        note(0, 0f, 0, 2),
                        note(0, Math.nextUp(.018f), 0, 2),
                        note(0, .018f, 0, 2),
                        note(1, 0, 0, 2),
                        note(0, -.018f, 0, 2),
                        note(0, Float.intBitsToFloat(0x7fc12345), 0, 2),
                        note(0, -Float.MIN_VALUE, 0, 2),
                        note(0, Float.POSITIVE_INFINITY, 0, 2),
                        note(0, Float.NEGATIVE_INFINITY, 0, 2),
                        note(0, 0, 0, 1),
                        note(-1, 0, 0, 2),
                        note(0, 0, -1, 2),
                        note(0, 0, 0, 0),
                        note(0, Float.MIN_VALUE, 0, 2));
        int[] incomingBits =
                notes.stream()
                        .mapToInt(n -> Float.floatToRawIntBits(n.positionInMeasure()))
                        .toArray();
        var input = score(notes);
        var indexMethod =
                ScoreExpressionDetector.class.getDeclaredMethod(
                        "indexNoteLanes", ScorePageInterpretation.class);
        indexMethod.setAccessible(true);
        var lanes = indexMethod.invoke(null, input);
        var columnMethod =
                ScoreExpressionDetector.class.getDeclaredMethod(
                        "column", ScoreExpressiveEvent.class);
        columnMethod.setAccessible(true);
        var queries =
                List.of(
                        event("zero", 0, 0, 2, 0f, Kind.SFORZATO),
                        event("edge", 0, 0, 2, .018f, Kind.SFORZATO),
                        event("other-staff", 0, 1, 2, 0f, Kind.SFORZATO),
                        event("other-measure", 1, 0, 2, 0f, Kind.SFORZATO),
                        event("other-count", 0, 0, 1, 0f, Kind.SFORZATO),
                        event("missing-lane", 1, 1, 3, 0f, Kind.SFORZATO));
        assertEquals(
                List.of(0, 2, 4, 6, 8, 15),
                ScoreExpressionDetector.targetIndices(input, queries.get(0)));
        for (var query : queries) {
            var column = ((Optional<?>) columnMethod.invoke(null, query)).orElseThrow();
            var indexedMethod =
                    ScoreExpressionDetector.class.getDeclaredMethod(
                            "targetIndicesForColumn",
                            ScorePageInterpretation.class,
                            column.getClass(),
                            Map.class);
            indexedMethod.setAccessible(true);
            assertEquals(
                    ScoreExpressionDetector.targetIndices(input, query),
                    indexedMethod.invoke(null, input, column, lanes));
        }
        for (int i = 0; i < notes.size(); i++) {
            assertSame(notes.get(i), input.notes().get(i));
            assertEquals(
                    incomingBits[i],
                    Float.floatToRawIntBits(input.notes().get(i).positionInMeasure()));
        }
    }
}
