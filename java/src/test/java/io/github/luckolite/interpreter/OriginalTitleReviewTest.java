// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
import java.util.Set;

public class OriginalTitleReviewTest {
    private static ScoreCreditsDetector.Line line(String text, float l, float t, float r, float b) {
        return new ScoreCreditsDetector.Line(text, l, t, r, b);
    }

    private static final ScoreCreditsDetector.Line ORIGINAL =
            line("C00L RIVER", 200, 160, 1600, 350);

    private static ScoreCreditsDetector.Page page(
            List<ScoreCreditsDetector.Line> lines,
            List<ScoreCreditsDetector.ReviewCredit> credits) {
        return new ScoreCreditsDetector.Page(1800, 1000, -1, lines, false, true, credits);
    }

    private static OriginalTitleCorroboration.Reading reading(int scale, String firstWord) {
        var first = line(firstWord, 25 * scale, 10 * scale, 450 * scale, 290 * scale);
        var second = line("RIVER", 475 * scale, 10 * scale, 975 * scale, 290 * scale);
        var main = line(firstWord + " RIVER", 25 * scale, 10 * scale, 975 * scale, 290 * scale);
        return new OriginalTitleCorroboration.Reading(
                scale,
                "COOL RIVER",
                .99f,
                1000 * scale,
                300 * scale,
                List.of(new OriginalTitleCorroboration.NativeLine(main, List.of(first, second))));
    }

    private static List<OriginalTitleCorroboration.Reading> readings() {
        return List.of(reading(1, "C00L"), reading(2, "CO0L"), reading(4, "COOL"));
    }

    @Test
    public void suggestionRetainsOriginalPageAndExactGeometry() {
        var original = page(List.of(ORIGINAL), List.of());
        var evidence = OriginalTitleReview.qualify(original, ORIGINAL, readings());
        assertNotNull(evidence.proposal());
        assertTrue(evidence.proposal().requiresReview());
        assertSame(original, evidence.originalPage());
        assertEquals("C00L RIVER", original.lines().get(0).text());
        assertEquals(line("COOL RIVER", 200, 160, 1600, 350), evidence.page().lines().get(0));
        assertEquals(original.width(), evidence.page().width(), 0);
        assertEquals(original.height(), evidence.page().height(), 0);
        assertEquals(original.notationTop(), evidence.page().notationTop(), 0);
        assertEquals(original.photographicCover(), evidence.page().photographicCover());
        assertEquals(original.creditsOnly(), evidence.page().creditsOnly());
    }

    @Test
    public void insufficientWitnessesKeepIdenticalPage() {
        var original = page(List.of(ORIGINAL), List.of());
        var evidence = OriginalTitleReview.qualify(original, ORIGINAL, List.of());
        assertNull(evidence.proposal());
        assertSame(original, evidence.page());
    }

    @Test
    public void serviceReviewPayloadAndOtherLinesSurvive() {
        var service = line("Transcribed by Example Studio", 200, 850, 1600, 890);
        var credit = new ScoreCreditsDetector.ReviewCredit(service, "Example Studio");
        var original = page(List.of(ORIGINAL, service), List.of(credit));
        var evidence = OriginalTitleReview.qualify(original, ORIGINAL, readings());
        assertNotNull(evidence.proposal());
        assertEquals(original.reviewCredits(), evidence.page().reviewCredits());
        assertSame(service, evidence.page().lines().get(1));
        assertTrue(
                OriginalTitleReview.sameContributors(
                        ScoreCreditsDetector.detect("", List.of(original), Set.of()),
                        ScoreCreditsDetector.detect("", List.of(evidence.page()), Set.of())));
    }

    @Test
    public void documentGuardRestoresOriginalPagesWhenLaterRoleDependsOnTitle() {
        var original = page(List.of(ORIGINAL), List.of());
        var evidence = OriginalTitleReview.qualify(original, ORIGINAL, readings());
        assertNotNull(evidence.proposal());
        var later =
                new ScoreCreditsDetector.Page(
                        1800,
                        2400,
                        500,
                        List.of(
                                line("OTHER WORK", 200, 20, 1600, 180),
                                line("Composed by", 200, 250, 650, 285),
                                line("COOL RIVER", 200, 300, 850, 345)),
                        true,
                        false);
        var originals = List.of(original, later);
        var candidates = List.of(evidence.page(), later);
        for (String hint : List.of("", "Imported sheet", "C00L RIVER")) {
            var before = ScoreCreditsDetector.detect(hint, originals, Set.of());
            var after = ScoreCreditsDetector.detect(hint, candidates, Set.of());
            assertEquals("COOL RIVER", before.composer());
            assertFalse(OriginalTitleReview.sameContributors(before, after));
            var selected = OriginalTitleReview.select(hint, originals, candidates);
            assertFalse(selected.proposalAccepted());
            assertEquals(before, selected.result());
            assertEquals(originals, selected.pages());
            assertSame(original, selected.pages().get(0));
        }
    }

    @Test
    public void noChangedTitleKeepsOriginalInput() {
        var original = page(List.of(ORIGINAL), List.of());
        var pages = List.of(original);
        var selected = OriginalTitleReview.select("Imported sheet", pages, pages);
        assertFalse(selected.proposalAccepted());
        assertEquals(pages, selected.pages());
        assertEquals(
                ScoreCreditsDetector.detect("Imported sheet", pages, Set.of()), selected.result());
    }

    @Test
    public void contributorComparisonIncludesAllFourFields() {
        var base = new ScoreCreditsDetector.Result("One", "A", "C", "R", List.of("U"));
        assertTrue(
                OriginalTitleReview.sameContributors(
                        base, new ScoreCreditsDetector.Result("Two", "A", "C", "R", List.of("U"))));
        for (var changed :
                List.of(
                        new ScoreCreditsDetector.Result("One", "B", "C", "R", List.of("U")),
                        new ScoreCreditsDetector.Result("One", "A", "D", "R", List.of("U")),
                        new ScoreCreditsDetector.Result("One", "A", "C", "S", List.of("U")),
                        new ScoreCreditsDetector.Result("One", "A", "C", "R", List.of("V"))))
            assertFalse(OriginalTitleReview.sameContributors(base, changed));
    }
}
