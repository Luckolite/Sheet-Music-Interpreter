// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original fictional score headers; no private score or historical-name lookup. */
public final class DatedSourceWorkContextTest {
    private ScoreCreditsDetector.Line line(
            String text, float left, float top, float right, float bottom) {
        return new ScoreCreditsDetector.Line(text, left, top, right, bottom);
    }

    private ScoreCreditsDetector.Result detect(
            ScoreCreditsDetector.Line caption, ScoreCreditsDetector.Line author) {
        var title = line("Silver River", 650, 100, 1150, 170);
        var lines = new java.util.ArrayList<>(List.of(title, author));
        if (caption != null) lines.add(caption);
        return ScoreCreditsDetector.detect(
                "Imported score",
                List.of(new ScoreCreditsDetector.Page(1800, 2500, 430, lines)),
                Set.of());
    }

    private ScoreCreditsDetector.Line author() {
        return line("Mira North (1810-1875)", 1300, 290, 1710, 322);
    }

    @Test
    public void centeredSourceWorkCaptionSupportsDatedScoreAuthor() {
        var result = detect(line("from Twilight Sketches", 630, 184, 1170, 222), author());
        assertEquals("Mira North", result.composer());
        assertEquals("", result.artist());
        assertEquals("", result.arranger());
    }

    @Test
    public void sourceCaptionMayStartWithAnArticle() {
        assertEquals(
                "Mira North",
                detect(line("from the Twilight Sketches", 620, 184, 1180, 222), author())
                        .composer());
    }

    @Test
    public void sourceCaptionSupportsNonEnglishWorkWords() {
        assertEquals(
                "Mira North",
                detect(line("from Les Brumes du Matin", 620, 184, 1180, 222), author()).composer());
    }

    @Test
    public void detachedLifespanKeepsItsPrintedNameOwner() {
        var result =
                ScoreCreditsDetector.detect(
                        "Imported score",
                        List.of(
                                new ScoreCreditsDetector.Page(
                                        1800,
                                        2500,
                                        430,
                                        List.of(
                                                line("Silver River", 650, 100, 1150, 170),
                                                line("from Twilight Sketches", 630, 184, 1170, 222),
                                                line("Mira North", 1460, 260, 1710, 290),
                                                line("(1810-1875)", 1450, 300, 1720, 328)))),
                        Set.of());
        assertEquals("Mira North", result.composer());
    }

    @Test
    public void datesAloneDoNotDetermineTheRole() {
        var result = detect(null, author());
        assertEquals("", result.composer());
        assertTrue(result.unclassifiedCredits().contains("Mira North"));
    }

    @Test
    public void undatedNameRemainsUnresolved() {
        assertEquals(
                "",
                detect(
                                line("from Twilight Sketches", 630, 184, 1170, 222),
                                line("Mira North", 1300, 290, 1710, 322))
                        .composer());
    }

    @Test
    public void publisherIsNotSourceWorkEvidence() {
        assertEquals(
                "", detect(line("from Northwind Press", 630, 184, 1170, 222), author()).composer());
    }

    @Test
    public void sourceCaptionMustBeInTheHeader() {
        assertEquals(
                "",
                detect(line("from Twilight Sketches", 630, 460, 1170, 498), author()).composer());
    }

    @Test
    public void distantSideTextDoesNotSupplyCenteredWorkContext() {
        assertEquals(
                "",
                detect(line("from Twilight Sketches", 100, 184, 600, 222), author()).composer());
    }

    @Test
    public void textAboveTheTitleDoesNotSupplyAWorkContinuation() {
        assertEquals(
                "", detect(line("from Twilight Sketches", 630, 40, 1170, 78), author()).composer());
    }

    @Test
    public void equallyProminentHeadingDoesNotSupplyACaption() {
        assertEquals(
                "",
                detect(line("from Twilight Sketches", 630, 184, 1170, 254), author()).composer());
    }

    @Test
    public void explicitlyPrintedArrangerRetainsItsRole() {
        var result =
                detect(
                        line("from Twilight Sketches", 630, 184, 1170, 222),
                        line("Arranged by Mira North (1810-1875)", 1180, 290, 1710, 322));
        assertEquals("", result.composer());
        assertTrue(result.arranger().contains("Mira North"));
    }
}
