// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural staff, words and sequential attacks; no private score geometry. */
public final class PrintedPedalWordTest {
    private static final int W = 1000, H = 650;

    private ScorePageInterpretation page(int rails, boolean head, boolean cross) {
        var notes = new ArrayList<ScoreNoteEvent>();
        if (head)
            for (float x : new float[] {340, 430, 600, 700}) {
                var n =
                        new ScoreNoteEvent(
                                0, (x - 200) / 600, 4, 0, 1, 328f / H, false, 0, 0, 2, 1, 1, 0, 0);
                notes.add(cross ? n.withCrossStaffBeam() : n);
            }
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.2f, .8f, .43f, .64f)),
                notes,
                1,
                List.of(),
                List.of(),
                List.of(new ScoreMeterChange(0, 4, 4)));
    }

    private ScorePageInterpretation detect(
            List<PlayingTechniqueDetector.Word> words, int rails, boolean head, boolean cross) {
        byte[] gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < rails; line++)
            for (int x = 180; x < 820; x++) gray[(300 + line * 14) * W + x] = 20;
        var score = page(rails, head, cross);
        byte[] before = gray.clone();
        var result =
                ScoreExpressionDetector.resolve(
                        ScoreExpressionDetector.apply(
                                score,
                                words,
                                List.of(new PlayingTechniqueDetector.Staff(300, 356, 14, 0, 1)),
                                gray,
                                W,
                                H),
                        4);
        assertArrayEquals(before, gray);
        assertEquals(score.notes(), result.notes());
        assertEquals(score.tempoChanges(), result.tempoChanges());
        return result;
    }

    private PlayingTechniqueDetector.Word word(String text, float left, float right) {
        return new PlayingTechniqueDetector.Word(text, left, .59f, right, .615f);
    }

    @Test
    public void fullPedalPhraseOwnsItsOverlappingTokenRatherThanTheNextAttack() {
        var result =
                detect(
                        List.of(word("Con pedale", .33f, .49f), word("pedale", .41f, .48f)),
                        5,
                        true,
                        false);
        assertEquals(1, result.expressiveEvents().size());
        var event = result.expressiveEvents().get(0);
        assertEquals(ScoreExpressiveEvent.Kind.PEDAL_DOWN, event.kind());
        assertEquals(ScoreExpressiveEvent.Scope.PART, event.scope());
        assertEquals("Con pedale", event.evidence().get(0).printedText());
        assertTrue(event.start().isPresent());
        assertTrue(event.end().isEmpty());
        assertTrue(
                PedalPerformance.resolve(
                                result.expressiveEvents(), new ScoreMeterMap(4, List.of()), 1)
                        .spans()
                        .isEmpty());
    }

    @Test
    public void completeReleasePhraseSuppressesItsContradictoryDownToken() {
        var result =
                detect(
                        List.of(word("Senza pedale", .33f, .49f), word("pedale", .41f, .48f)),
                        5,
                        true,
                        false);
        assertEquals(1, result.expressiveEvents().size());
        assertEquals(ScoreExpressiveEvent.Kind.PEDAL_UP, result.expressiveEvents().get(0).kind());
    }

    @Test
    public void tokenBeforePhraseInputStillKeepsTheCompleteLiteralPhrase() {
        var result =
                detect(
                        List.of(word("pedale", .41f, .48f), word("Con pedale", .33f, .49f)),
                        5,
                        true,
                        false);
        assertEquals(1, result.expressiveEvents().size());
        assertEquals(
                "Con pedale", result.expressiveEvents().get(0).evidence().get(0).printedText());
    }

    @Test
    public void separatePrintedPedalWordsRemainIndependent() {
        var result =
                detect(
                        List.of(word("Con pedale", .33f, .49f), word("pedale", .59f, .65f)),
                        5,
                        true,
                        false);
        assertEquals(2, result.expressiveEvents().size());
    }

    @Test
    public void completeLiteralDownAndReleaseRetainFinitePlayback() {
        var result =
                detect(
                        List.of(word("Ped.", .33f, .37f), word("senza pedale", .59f, .69f)),
                        5,
                        true,
                        false);
        assertEquals(2, result.expressiveEvents().size());
        var performance =
                PedalPerformance.resolve(
                        result.expressiveEvents(), new ScoreMeterMap(4, List.of()), 1);
        assertEquals(
                result.expressiveEvents() + " / " + performance, 1, performance.spans().size());
        assertTrue(performance.spans().get(0).endBeat() > performance.spans().get(0).startBeat());
    }

    @Test
    public void incompleteRailsDoNotOwnPedalWords() {
        assertTrue(
                detect(List.of(word("Con pedale", .33f, .49f)), 4, true, false)
                        .expressiveEvents()
                        .isEmpty());
    }

    @Test
    public void missingWrittenHeadDoesNotOwnPedalWords() {
        assertTrue(
                detect(List.of(word("Con pedale", .33f, .49f)), 5, false, false)
                        .expressiveEvents()
                        .isEmpty());
    }

    @Test
    public void crossStaffWrittenHeadDoesNotOwnPedalWords() {
        assertTrue(
                detect(List.of(word("Con pedale", .33f, .49f)), 5, true, true)
                        .expressiveEvents()
                        .isEmpty());
    }

    @Test
    public void unrelatedTextDoesNotBecomePedal() {
        assertTrue(
                detect(List.of(word("pedestrian", .33f, .49f)), 5, true, false)
                        .expressiveEvents()
                        .isEmpty());
    }
}
