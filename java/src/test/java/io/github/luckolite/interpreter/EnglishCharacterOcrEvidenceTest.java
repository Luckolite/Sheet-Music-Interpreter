// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnglishCharacterOcrEvidenceTest {
    private List<PlayingTechniqueDetector.Word> collect(String value) {
        var box = new OcrText.Box(50, 30, 250, 50);
        var line = new OcrText.Line(value, box, List.of());
        var text = new OcrText(value, List.of(new OcrText.Block(value, box, List.of(line))));
        var words = new ArrayList<PlayingTechniqueDetector.Word>();
        MusicalOcrEvidence.addDirectionWords(text, words, 1, 0, 500, 300);
        return words;
    }

    @Test
    public void completeCharacterLinesSurviveOcrCollectionWithExactText() {
        for (String s :
                List.of(
                        "Flowing",
                        "triumphantly",
                        "ethereal.",
                        "with driving intensity",
                        "like an echo")) {
            var out = collect(s);
            assertEquals(s, 1, out.size());
            assertEquals(s, out.get(0).text());
        }
    }

    @Test
    public void proseLyricsAndFragmentsAreNotAcquiredAsDirections() {
        for (String s :
                List.of(
                        "the flowing river",
                        "sing triumphantly together",
                        "a voice like an echo",
                        "flowing through my heart",
                        "ethereality",
                        "with driving",
                        "flowing lyrics")) assertTrue(s, collect(s).isEmpty());
    }

    @Test
    public void neutralMeaningDoesNotGuessStrengthTempoOrNormalizeEvidence() {
        String s = "  Fl\u00f6wing!  ";
        var d = ExpressiveDirectionText.parse(s);
        assertEquals(1, d.size());
        assertEquals(s, d.get(0).printedText());
        assertEquals(ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION, d.get(0).kind());
        assertEquals(ScoreExpressiveEvent.Strength.UNSPECIFIED, d.get(0).strength());
        assertTrue(ExpressiveDirectionText.parse("molto flowing").isEmpty());
        assertTrue(ExpressiveDirectionText.parse("poco ethereal").isEmpty());
    }
}
