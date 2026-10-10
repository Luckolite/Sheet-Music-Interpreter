// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class InlineArrangementCreditTest {
    private static ScoreCreditsDetector.Line line(String text, float x, float y, float w, float h) {
        return new ScoreCreditsDetector.Line(text, x, y, x + w, y + h);
    }

    private static ScoreCreditsDetector.Page page(ScoreCreditsDetector.Line... credits) {
        var lines = new java.util.ArrayList<ScoreCreditsDetector.Line>();
        lines.add(line("Morning Harbour", 250, 30, 500, 55));
        lines.addAll(List.of(credits));
        return new ScoreCreditsDetector.Page(1000, 1400, 230, lines);
    }

    @Test
    public void inlineInstrumentArrangementGetsOriginalSourceVerification() {
        for (String label :
                List.of(
                        "Piano Arrangement",
                        "Guitar Arrangements",
                        "Arrangement",
                        "Arrangements")) {
            var original = line(label + " Kira Wern", 650, 110, 300, 20);
            var input = page(original);
            assertTrue(
                    label,
                    ScoreCreditsDetector.bibliographicLineCandidates(input).contains(original));
            String corrected = label + " Kira Wren";
            var merged =
                    ScoreCreditsDetector.mergeBibliographicLineConsensus(
                            input, original, List.of(corrected, corrected, corrected));
            assertEquals(corrected, merged.get(1).text());
            assertEquals(
                    "Kira Wren",
                    ScoreCreditsDetector.detect(
                                    "Morning Harbour",
                                    List.of(new ScoreCreditsDetector.Page(1000, 1400, 230, merged)),
                                    Set.of())
                            .arranger());
            assertEquals(original.left(), merged.get(1).left(), 0);
            assertEquals(original.bottom(), merged.get(1).bottom(), 0);
        }
    }

    @Test
    public void oneConflictingSourceReadCannotChangeInlineName() {
        var original = line("Piano Arrangement Kira Wern", 650, 110, 300, 20);
        var input = page(original);
        assertEquals(
                input.lines(),
                ScoreCreditsDetector.mergeBibliographicLineConsensus(
                        input,
                        original,
                        List.of(
                                "Piano Arrangement Kira Wren",
                                "Piano Arrangement Kira Wren",
                                "Piano Arrangement Kira Wern")));
    }

    @Test
    public void correctedRoleOrDifferentContributorCountCannotChangeName() {
        var original = line("Piano Arrangement Kira Wern", 650, 110, 300, 20);
        var input = page(original);
        for (String next : List.of("Music by Kira Wren", "Piano Arrangement Kira Wren; Ada Stone"))
            assertEquals(
                    next,
                    input.lines(),
                    ScoreCreditsDetector.mergeBibliographicLineConsensus(
                            input, original, List.of(next, next, next)));
    }

    @Test
    public void standaloneArrangementStillFindsNameAbove() {
        for (String label : List.of("Arrangement", "Arrangements", "Piano Arrangement")) {
            var name = line("Mira Brook", 100, 110, 200, 22);
            var original = line(label, 100, 140, 220, 20);
            var input = page(name, original);
            assertFalse(ScoreCreditsDetector.bibliographicLineCandidates(input).contains(original));
            assertEquals(
                    "Mira Brook",
                    ScoreCreditsDetector.detect("Morning Harbour", List.of(input), Set.of())
                            .arranger());
        }
    }

    @Test
    public void inlineArrangementDoesNotBorrowDifferentNameAbove() {
        var input =
                page(
                        line("Mira Brook", 650, 90, 220, 20),
                        line("Piano Arrangement Kira Wren", 650, 120, 300, 20));
        assertEquals(
                "Kira Wren",
                ScoreCreditsDetector.detect("Morning Harbour", List.of(input), Set.of())
                        .arranger());
    }

    @Test
    public void separateExplicitContributorsRemainDistinct() {
        var input =
                page(
                        line("Piano Arrangement Kira Wren", 100, 110, 300, 20),
                        line("Guitar Arrangement Mira Brook", 650, 110, 330, 20));
        assertEquals(
                "Kira Wren; Mira Brook",
                ScoreCreditsDetector.detect("Morning Harbour", List.of(input), Set.of())
                        .arranger());
    }
}
