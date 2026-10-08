// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Original printed staff and text controls; character evidence never supplies a BPM. */
public class CharacterDirectionsTest {
    private static final int W = 500, H = 300;

    private static byte[] rails() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 460; x++) g[y * W + x] = 0;
        return g;
    }

    private static ScorePageInterpretation score() {
        var n = new ScoreNoteEvent(0, .27f, 0, 0, 1, 164f / H, false, 0, 0, 0, 1, 1, 0);
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.04f, .92f, .25f, .65f)),
                List.of(n),
                1,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static ScorePageInterpretation detect(List<String> words, byte[] gray) {
        float x = .04f + .27f * (.92f - .04f);
        return ScoreExpressionDetector.apply(
                score(),
                words.stream()
                        .map(
                                t ->
                                        new PlayingTechniqueDetector.Word(
                                                t, x, 195f / H, x + .08f, 215f / H))
                        .toList(),
                List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                gray,
                W,
                H);
    }

    @Test
    public void ordinaryCharacterWordsRetainTheirPrintedEvidence() {
        for (String word :
                List.of(
                        "scherzando",
                        "agitato",
                        "festoso",
                        "espressivo",
                        "dolce e cantabile",
                        "con brio",
                        "tranquillo",
                        "maestoso")) {
            var d = detect(List.of(word), rails());
            assertEquals(word, 1, d.expressiveEvents().size());
            var e = d.expressiveEvents().get(0);
            assertEquals(Kind.UNRESOLVED_DIRECTION, e.kind());
            assertEquals(word, e.evidence().get(0).printedText());
            assertEquals(Scope.UNRESOLVED, e.scope());
            assertTrue(e.start().isEmpty());
        }
    }

    @Test
    public void characterWordsNeverInventTempoOrWrittenTiming() {
        var d = detect(List.of("agitato"), rails());
        assertEquals(score().notes(), d.notes());
        assertEquals(score().rests(), d.rests());
        assertEquals(score().tempoChanges(), d.tempoChanges());
        assertEquals(score().meterChanges(), d.meterChanges());
    }

    @Test
    public void missingRailsCannotGiveAWordNoteOwnership() {
        byte[] blank = new byte[W * H];
        Arrays.fill(blank, (byte) 255);
        assertTrue(detect(List.of("cantabile"), blank).expressiveEvents().isEmpty());
    }

    @Test
    public void tokenBoundsAndUnprovedOcrSpellingStayStrict() {
        for (String s : List.of("festosology", "scherzandos", "fesoso", "written prose"))
            assertTrue(s, ExpressiveDirectionText.parse(s).isEmpty());
    }

    @Test
    public void distinctWordsAtSameColumnRemainAndDuplicatesDoNotMultiply() {
        var d = detect(List.of("dolce", "cantabile", "dolce"), rails());
        assertEquals(2, d.expressiveEvents().size());
        assertNotEquals(
                d.expressiveEvents().get(0).eventId(), d.expressiveEvents().get(1).eventId());
    }

    @Test
    public void tempoDirectionsKeepTheirExistingMeaning() {
        var d = detect(List.of("rit."), rails());
        assertEquals(Kind.RITARDANDO, d.expressiveEvents().get(0).kind());
    }

    @Test
    public void resolvingAColumnKeepsCharacterMeaningAndOriginalInput() {
        var d = detect(List.of("dolce e cantabile"), rails());
        var resolved = ScoreExpressionDetector.resolve(d, 4);
        assertEquals(Kind.UNRESOLVED_DIRECTION, resolved.expressiveEvents().get(0).kind());
        assertEquals(d.notes(), resolved.notes());
        assertEquals(d.tempoChanges(), resolved.tempoChanges());
        assertEquals(Scope.UNRESOLVED, d.expressiveEvents().get(0).scope());
    }
}
