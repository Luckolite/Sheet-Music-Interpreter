// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** Original synthetic quotation/layout examples; no private score material. */
public class NarrativeArtworkTitleTest {
    private static final String FIRST = "SHE OPENED THE WINDOW AND WHISPERED";
    private static final String SECOND = "MEET ME AT SUNRISE";

    private static ScoreCreditsDetector.Page poster() {
        return new ScoreCreditsDetector.Page(
                1000,
                800,
                -1,
                List.of(
                        new ScoreCreditsDetector.Line(FIRST, 150, 300, 850, 360),
                        new ScoreCreditsDetector.Line(SECOND, 280, 372, 720, 432)));
    }

    private static ScoreCreditsDetector.Result detect(
            String hint, List<ScoreCreditsDetector.Page> pages) {
        return ScoreCreditsDetector.detect(hint, pages, Set.of());
    }

    @Test
    public void narrativeArtworkKeepsImportedTitleWithoutInventingContributor() {
        var result = detect("Evening Keepsake", List.of(poster()));
        assertEquals("Evening Keepsake", result.title());
        // A narrative quotation is not an unidentified contributor.
        assertEquals(List.of(), result.unclassifiedCredits());
        assertEquals("", result.artist());
        assertEquals("", result.composer());
        assertEquals("", result.arranger());
    }

    @Test
    public void filenameDoesNotSelectTheRule() {
        for (String hint : List.of("Night Sketch", "Morning Notes", "Page 42"))
            assertEquals(hint, detect(hint, List.of(poster())).title());
    }

    @Test
    public void aNotationPageDoesNotBecomeArtwork() {
        var p = poster();
        var score = new ScoreCreditsDetector.Page(p.width(), p.height(), 500, p.lines());
        assertNotEquals("Evening Keepsake", detect("Evening Keepsake", List.of(score)).title());
    }

    @Test
    public void laterNotationKeepsNormalDocumentSelection() {
        var later = new ScoreCreditsDetector.Page(1000, 800, 200, List.of());
        assertNotEquals(
                "Evening Keepsake", detect("Evening Keepsake", List.of(poster(), later)).title());
    }

    @Test
    public void anExplicitTitleOrCreditPreventsNarrativeShortcut() {
        var p = poster();
        var lines = new java.util.ArrayList<>(p.lines());
        lines.add(new ScoreCreditsDetector.Line("Music by Mira Vale", 700, 150, 950, 175));
        var result =
                detect(
                        "Evening Keepsake",
                        List.of(new ScoreCreditsDetector.Page(1000, 800, -1, lines)));
        assertEquals("Mira Vale", result.composer());
    }

    @Test
    public void ordinaryCoverTitleIsStillRead() {
        var p =
                new ScoreCreditsDetector.Page(
                        1000,
                        800,
                        -1,
                        List.of(
                                new ScoreCreditsDetector.Line("SILVER RIVER", 200, 300, 800, 360),
                                new ScoreCreditsDetector.Line(
                                        "Music by Mira Vale", 350, 380, 650, 400)));
        var result = detect("Import 12", List.of(p));
        assertEquals("SILVER RIVER", result.title());
        assertEquals("Mira Vale", result.composer());
    }

    @Test
    public void aShortSpeechTitleIsNotNarrativeEvidence() {
        var p =
                new ScoreCreditsDetector.Page(
                        1000,
                        800,
                        -1,
                        List.of(
                                new ScoreCreditsDetector.Line("SHE SAID", 150, 300, 850, 360),
                                new ScoreCreditsDetector.Line(SECOND, 280, 372, 720, 432)));
        assertNotEquals("Evening Keepsake", detect("Evening Keepsake", List.of(p)).title());
    }

    @Test
    public void aMissingImportNameCannotProduceAnInventedTitle() {
        assertFalse(detect("", List.of(poster())).title().isEmpty());
    }

    @Test
    public void aScatteredSmallTextPairIsNotPosterEvidence() {
        var p =
                new ScoreCreditsDetector.Page(
                        1000,
                        800,
                        -1,
                        List.of(
                                new ScoreCreditsDetector.Line(FIRST, 150, 300, 850, 320),
                                new ScoreCreditsDetector.Line(SECOND, 280, 500, 720, 520)));
        assertNotEquals("Evening Keepsake", detect("Evening Keepsake", List.of(p)).title());
    }

    @Test
    public void malformedSourceBoundsCannotEnableFallbackShortcut() {
        var p =
                new ScoreCreditsDetector.Page(
                        1000,
                        800,
                        -1,
                        List.of(
                                new ScoreCreditsDetector.Line(FIRST, Float.NaN, 300, 850, 360),
                                new ScoreCreditsDetector.Line(SECOND, 280, 372, 720, 432)));
        assertNotEquals(
                List.of(FIRST + " " + SECOND),
                detect("Evening Keepsake", List.of(p)).unclassifiedCredits());
    }
}
