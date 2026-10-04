// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

public final class ScoreExpressionDetectorTest {
    private static final int W = 500, H = 300;

    private static byte[] rails() {
        var gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 460; x++) gray[y * W + x] = 0;
        return gray;
    }

    private static ScorePageInterpretation score(boolean meter) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (float position : new float[] {.15f, .27f, .65f, .8f})
            notes.add(new ScoreNoteEvent(0, position, 0, 0, 1, 164f / H, false, 0, 0, 0, 1, 1, 0));
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.04f, .92f, .25f, .65f)),
                notes,
                1,
                List.of(),
                List.of(),
                meter ? List.of(new ScoreMeterChange(0, 4, 4)) : List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static ScorePageInterpretation detect(String text, boolean meter, byte[] gray) {
        float x = .04f + .27f * (.92f - .04f);
        return ScoreExpressionDetector.apply(
                score(meter),
                List.of(new PlayingTechniqueDetector.Word(text, x, 195f / H, x + .08f, 215f / H)),
                List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                gray,
                W,
                H);
    }

    @Test
    public void unevenEngravingAttachesToWrittenBeat() {
        var result = detect("rit.", true, rails());
        assertEquals(1, result.expressiveEvents().size());
        var event = result.expressiveEvents().get(0);
        assertEquals(Kind.RITARDANDO, event.kind());
        assertEquals(new ScoreAnchor(0, 1), event.start().orElseThrow());
        assertEquals(score(true).notes(), result.notes());
    }

    @Test
    public void continuationKeepsUnresolvedEvidenceUntilMeterKnown() {
        var unresolved = detect("rall.", false, rails());
        assertEquals(Scope.UNRESOLVED, unresolved.expressiveEvents().get(0).scope());
        var resolved = ScoreExpressionDetector.resolve(unresolved, 4);
        assertEquals(
                new ScoreAnchor(0, 1), resolved.expressiveEvents().get(0).start().orElseThrow());
        var offset =
                ScoreDynamicContinuation.offsetEvidence(unresolved.expressiveEvents().get(0), 3, 2);
        assertTrue(offset.targetEventId().orElseThrow().startsWith("expression-column:3:"));
        assertEquals(2, offset.evidence().get(0).pageIndex());
    }

    @Test
    public void breathOwnsReleaseAndSforzandoOwnsAttack() {
        var breath = detect("breath", true, rails()).expressiveEvents().get(0);
        assertEquals(new ScoreAnchor(0, 2), breath.start().orElseThrow());
        var sfz = detect("sfz", true, rails()).expressiveEvents().get(0);
        assertEquals(Kind.SFORZATO, sfz.kind());
        assertEquals(Scope.NOTE, sfz.scope());
        assertEquals(new ScoreAnchor(0, 1), sfz.start().orElseThrow());
    }

    @Test
    public void textWithoutPrintedRailsCannotChangePlayback() {
        var empty = new byte[W * H];
        Arrays.fill(empty, (byte) 255);
        assertTrue(detect("rit.", true, empty).expressiveEvents().isEmpty());
        assertTrue(detect("written prose", true, rails()).expressiveEvents().isEmpty());
    }

    @Test
    public void aliasesAreTokenBoundedAndKeepRitenutoDistinct() {
        for (String text : List.of("rite", "rite.", "riten.", "ritenuto"))
            assertEquals(Kind.RITENUTO, ExpressiveDirectionText.parse(text).get(0).kind());
        assertEquals(Kind.RITARDANDO, ExpressiveDirectionText.parse("rit.").get(0).kind());
        assertTrue(ExpressiveDirectionText.parse("writer and ritual").isEmpty());
    }

    @Test
    public void resolvedColumnReuseKeepsPublicLookupAndUnownedEvidence() {
        var detected = detect("sfz", true, rails());
        var event = detected.expressiveEvents().get(0);
        assertEquals(List.of(1), ScoreExpressionDetector.targetIndices(detected, event));
        var foreign =
                new ScoreExpressiveEvent(
                        "foreign-expression",
                        event.kind(),
                        Optional.empty(),
                        Optional.empty(),
                        Scope.UNRESOLVED,
                        event.staffIndex(),
                        event.staffCount(),
                        event.targetEventId(),
                        event.strength(),
                        event.qualifierText(),
                        List.of(new Evidence("synthetic-manual", 0, .27f, 0, 1, "sfz")));
        var malformed =
                new ScoreExpressiveEvent(
                        "malformed-expression",
                        event.kind(),
                        Optional.empty(),
                        Optional.empty(),
                        Scope.UNRESOLVED,
                        event.staffIndex(),
                        event.staffCount(),
                        Optional.of("expression-column:0:0:1:not-an-int"),
                        event.strength(),
                        event.qualifierText(),
                        event.evidence());
        var inputEvents = List.of(event, foreign, malformed);
        var input = detected.withExpressiveEvents(inputEvents);
        var resolved = ScoreExpressionDetector.resolve(input, 4);
        assertEquals(event, resolved.expressiveEvents().get(0));
        assertSame(foreign, resolved.expressiveEvents().get(1));
        assertSame(malformed, resolved.expressiveEvents().get(2));
        assertEquals(List.of(1), ScoreExpressionDetector.targetIndices(resolved, event));
        assertTrue(ScoreExpressionDetector.targetIndices(resolved, foreign).isEmpty());
        assertTrue(ScoreExpressionDetector.targetIndices(resolved, malformed).isEmpty());
        assertEquals(inputEvents, input.expressiveEvents());
        assertEquals(input.notes(), resolved.notes());
    }
}
