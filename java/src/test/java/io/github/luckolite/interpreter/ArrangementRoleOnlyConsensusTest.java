// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArrangementRoleOnlyConsensusTest {
    private static ScoreCreditsDetector.Line line(String text, float x, float y, float w, float h) {
        return new ScoreCreditsDetector.Line(text, x, y, x + w, y + h);
    }

    private static ScoreCreditsDetector.Page page(ScoreCreditsDetector.Line original) {
        return new ScoreCreditsDetector.Page(
                1000, 1400, 230, List.of(line("Morning Harbour", 250, 30, 500, 55), original));
    }

    private static void unchanged(String original, String... samples) {
        var source = line(original, 650, 110, 300, 20);
        var input = page(source);
        assertEquals(
                input.lines(),
                ScoreCreditsDetector.mergeBibliographicLineConsensus(
                        input, source, List.of(samples)));
    }

    private static void corrected(String name, String... labels) {
        var source = line("Arrungel by " + name, 650, 110, 300, 20);
        var input = page(source);
        var samples = java.util.Arrays.stream(labels).map(s -> s + " by " + name).toList();
        var merged = ScoreCreditsDetector.mergeBibliographicLineConsensus(input, source, samples);
        assertEquals("Arranged by " + name, merged.get(1).text());
        assertEquals(source.left(), merged.get(1).left(), 0);
        assertEquals(source.bottom(), merged.get(1).bottom(), 0);
        assertEquals(
                name,
                ScoreCreditsDetector.detect(
                                "Morning Harbour",
                                List.of(new ScoreCreditsDetector.Page(1000, 1400, 230, merged)),
                                Set.of())
                        .arranger());
    }

    @Test
    public void observedExactRoleWithTerminalInsertedLetter() {
        corrected("Kira Wren", "Arranged", "Arrangedl", "Arrangedl");
    }

    @Test
    public void observedExactRoleWithSubstitutedRoleVowel() {
        corrected("Kira Wren", "Arrunged", "Arranged", "Arranged");
    }

    @Test
    public void multipleNamesKeepLiteralSeparators() {
        corrected("Kira Wren; Ada Stone", "Arranged", "Arrangedl", "Arrunged");
    }

    @Test
    public void accentsStayLiteral() {
        corrected("Zoë Márin", "Arranged", "Arrangedl", "Arrunged");
    }

    @Test
    public void exactRoleMustBeActuallyObserved() {
        unchanged(
                "Arrungel by Kira Wren",
                "Arrangedl by Kira Wren",
                "Arrunged by Kira Wren",
                "Arrungel by Kira Wren");
    }

    @Test
    public void agreeingCorrectedNamesDoNotEnterLabelOnlyException() {
        unchanged(
                "Arrungel by Kira Wern",
                "Arranged by Kira Wren",
                "Arrangedl by Kira Wren",
                "Arrunged by Kira Wren");
    }

    @Test
    public void oneDifferentNameRejectsException() {
        unchanged(
                "Arrungel by Kira Wren",
                "Arranged by Kira Wren",
                "Arrangedl by Kira Wren",
                "Arrunged by Mira Brook");
    }

    @Test
    public void newContributorRejectsException() {
        unchanged(
                "Arrungel by Kira Wren",
                "Arranged by Kira Wren",
                "Arrangedl by Kira Wren; Ada Stone",
                "Arrunged by Kira Wren");
    }

    @Test
    public void clippedNamesRejectException() {
        unchanged(
                "Arrungel by Kira Wren",
                "Arranged by Kira Wre",
                "Arrangedl by Kira Wre",
                "Arrunged by Kira Wre");
    }

    @Test
    public void mixedRolesRejectException() {
        unchanged(
                "Arrungel by Kira Wren",
                "Arranged by Kira Wren",
                "Composed by Kira Wren",
                "Arrangedl by Kira Wren");
    }

    @Test
    public void otherArrangeVerbIsNotAnOcrVariation() {
        unchanged(
                "Arrungel by Kira Wren",
                "Arranged by Kira Wren",
                "Arranges by Kira Wren",
                "Arrunged by Kira Wren");
    }

    @Test
    public void otherArrangeNounIsNotAnOcrVariation() {
        unchanged(
                "Arrungel by Kira Wren",
                "Arranged by Kira Wren",
                "Arranger by Kira Wren",
                "Arrangedl by Kira Wren");
    }

    @Test
    public void originalRealVerbIsNotAnOcrVariation() {
        unchanged(
                "Arranges by Kira Wren",
                "Arranged by Kira Wren",
                "Arrangedl by Kira Wren",
                "Arrunged by Kira Wren");
    }

    @Test
    public void recognizedOriginalRoleDoesNotEnterException() {
        unchanged(
                "Arranger by Kira Wren",
                "Arranged by Kira Wren",
                "Arrangedl by Kira Wren",
                "Arrunged by Kira Wren");
    }

    @Test
    public void explicitByIsRequired() {
        unchanged(
                "Arrungel Kira Wren",
                "Arranged Kira Wren",
                "Arrangedl Kira Wren",
                "Arrunged Kira Wren");
    }

    @Test
    public void multilineReadingRejectsException() {
        unchanged(
                "Arrungel by Kira Wren",
                "Arranged by Kira Wren",
                "Arrangedl by Kira\nWren",
                "Arrunged by Kira Wren");
    }

    @Test
    public void romanNumeralCannotTurnIntoDigits() {
        unchanged(
                "Arrungel by Kira Wren II",
                "Arranged by Kira Wren II",
                "Arrangedl by Kira Wren 11",
                "Arrunged by Kira Wren II");
    }
}
