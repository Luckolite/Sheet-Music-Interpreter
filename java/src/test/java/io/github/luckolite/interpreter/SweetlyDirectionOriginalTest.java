// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

/** Original literal character-direction contracts; no inferred sound or tempo. */
public final class SweetlyDirectionOriginalTest {
    private static final int W = 500, H = 300;

    private static byte[] rails() {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 460; x++) g[y * W + x] = 0;
        return g;
    }

    private static ScorePageInterpretation page() {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (float p : new float[] {.15f, .27f, .65f, .8f})
            notes.add(new ScoreNoteEvent(0, p, 0, 0, 1, 164f / H, false, 0, 0, 0, 1, 1, 0));
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.04f, .92f, .25f, .65f)),
                notes,
                1,
                List.of(),
                List.of(),
                List.of(new ScoreMeterChange(0, 4, 4)),
                List.of(),
                List.of(),
                List.of());
    }

    private static ScorePageInterpretation detect(String text, byte[] g, float x) {
        return ScoreExpressionDetector.apply(
                page(),
                List.of(new PlayingTechniqueDetector.Word(text, x, 195f / H, x + .08f, 215f / H)),
                List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                g,
                W,
                H);
    }

    private static void literal(String text) {
        var ds = ExpressiveDirectionText.parse(text);
        assertEquals(1, ds.size());
        assertEquals(Kind.UNRESOLVED_DIRECTION, ds.get(0).kind());
        assertEquals(Strength.UNSPECIFIED, ds.get(0).strength());
        assertEquals(text, ds.get(0).printedText());
        assertEquals("", ds.get(0).qualifierText());
    }

    @Test
    public void printedSweetlyIsKeptVerbatim() {
        literal("Sweetly");
    }

    @Test
    public void caseWhitespaceAndTerminalPunctuationRemainLiteral() {
        for (String s : List.of("  SWEETLY.  ", "sweetly!", " Sweetly ")) literal(s);
    }

    @Test
    public void proseTitlesAndEmbeddedWordsAreNotDirections() {
        for (String s :
                List.of(
                        "sings sweetly",
                        "Sweetly printed edition",
                        "Music by Sweetly",
                        "sweetlyrical",
                        "unsweetly",
                        "sweetly copyright publisher",
                        "sweet ly")) assertTrue(s, ExpressiveDirectionText.parse(s).isEmpty());
    }

    @Test
    public void unevenEngravingUsesWrittenColumnWithoutTempoOrDynamic() {
        var p = page();
        var g = rails();
        var before = g.clone();
        var out = detect("Sweetly", g, .04f + .27f * (.92f - .04f));
        assertEquals(1, out.expressiveEvents().size());
        var e = out.expressiveEvents().get(0);
        assertEquals(Kind.UNRESOLVED_DIRECTION, e.kind());
        assertEquals(new ScoreAnchor(0, 1), e.start().orElseThrow());
        assertTrue(e.end().isEmpty());
        assertEquals("Sweetly", e.evidence().get(0).printedText());
        assertEquals(List.of(1), ScoreExpressionDetector.targetIndices(out, e));
        assertEquals(p.notes(), out.notes());
        assertEquals(p.rests(), out.rests());
        assertEquals(p.tempoChanges(), out.tempoChanges());
        assertEquals(p.dynamicChanges(), out.dynamicChanges());
        assertEquals(p.techniqueChanges(), out.techniqueChanges());
        assertEquals(p.playbackDirections(), out.playbackDirections());
        assertArrayEquals(before, g);
    }

    @Test
    public void repeatedOcrAcquisitionDoesNotDuplicateTheDirection() {
        var first = detect("Sweetly", rails(), .04f + .27f * (.92f - .04f));
        var again =
                ScoreExpressionDetector.apply(
                        first,
                        List.of(
                                new PlayingTechniqueDetector.Word(
                                        "Sweetly", .2776f, 195f / H, .3576f, 215f / H)),
                        List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1)),
                        rails(),
                        W,
                        H);
        assertEquals(1, first.expressiveEvents().size());
        assertEquals(first.expressiveEvents(), again.expressiveEvents());
    }

    @Test
    public void unregisteredPaperAndDistantTextRemainUnowned() {
        var blank = new byte[W * H];
        Arrays.fill(blank, (byte) 255);
        assertTrue(detect("Sweetly", blank, .2776f).expressiveEvents().isEmpty());
        assertTrue(detect("Sweetly", rails(), .98f).expressiveEvents().isEmpty());
    }

    @Test
    public void nearbyProseDoesNotAcquireAColumn() {
        assertTrue(detect("The singer sang sweetly", rails(), .2776f).expressiveEvents().isEmpty());
    }

    @Test
    public void establishedCharacterAndTempoKindsRemainSeparate() {
        literal("Freely, gently");
        assertEquals(Kind.RITARDANDO, ExpressiveDirectionText.parse("rit.").get(0).kind());
        assertEquals(Kind.SFORZATO, ExpressiveDirectionText.parse("sfz").get(0).kind());
    }
}
