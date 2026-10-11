// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original text controls; preserving character marks does not invent a tempo. */
public final class EnglishCharacterDirectionTest {
    private void mark(String printed) {
        var result = ExpressiveDirectionText.parse(printed);
        assertEquals(1, result.size());
        assertEquals(ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION, result.get(0).kind());
        assertEquals(ScoreExpressiveEvent.Strength.UNSPECIFIED, result.get(0).strength());
        assertEquals(printed, result.get(0).printedText());
    }

    @Test
    public void freelyIsRetained() {
        mark("Freely");
    }

    @Test
    public void gentlyIsRetained() {
        mark("gently");
    }

    @Test
    public void compoundKeepsWholePrintedText() {
        mark("Freely, gently");
    }

    @Test
    public void conjunctionKeepsWholePrintedText() {
        mark("Gently and freely");
    }

    @Test
    public void caseAndOuterWhitespaceArePreserved() {
        mark("  GENTLY.  ");
    }

    @Test
    public void terminalPunctuationIsRetained() {
        mark("freely!");
    }

    @Test
    public void proseAndEmbeddedWordsDoNotBecomeDirections() {
        for (String text :
                List.of(
                        "freely available edition",
                        "play gently with the cover",
                        "ungently",
                        "freelyness",
                        "gentlyrical",
                        "Freely, copyright publisher",
                        "The singer moved freely",
                        "",
                        " ")) assertTrue(text, ExpressiveDirectionText.parse(text).isEmpty());
    }

    @Test
    public void existingTempoMeaningRemainsDistinct() {
        assertEquals(
                ScoreExpressiveEvent.Kind.RITARDANDO,
                ExpressiveDirectionText.parse("rit.").get(0).kind());
    }
}
