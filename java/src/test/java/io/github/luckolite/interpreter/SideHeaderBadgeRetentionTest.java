// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class SideHeaderBadgeRetentionTest {
    @Test
    public void falseStaffCannotTurnSingleWordSideBadgeIntoReviewNames() {
        var page =
                page(
                        List.of(
                                line("AUDIO", 120, 144),
                                line("ACCESS", 151, 175),
                                line("INCLUDED", 181, 205)));
        var result = ScoreCreditsDetector.detect("Evening Concerts", List.of(page), Set.of());
        assertFalse(result.unclassifiedCredits().contains("AUDIO"));
        assertFalse(result.unclassifiedCredits().contains("ACCESS"));
        assertFalse(result.unclassifiedCredits().contains("INCLUDED"));
    }

    @Test
    public void properSideHeaderNameStillSurvivesFalseStaff() {
        var result =
                ScoreCreditsDetector.detect(
                        "Evening Concerts",
                        List.of(page(List.of(line("Elena Stone", 150, 180)))),
                        Set.of());
        assertTrue(result.unclassifiedCredits().contains("Elena Stone"));
        assertEquals("", result.artist());
    }

    @Test
    public void singleWordSideNameRemainsAvailableForReview() {
        var result =
                ScoreCreditsDetector.detect(
                        "Evening Concerts",
                        List.of(page(List.of(line("Aria", 150, 180)))),
                        Set.of());
        assertTrue(result.unclassifiedCredits().contains("Aria"));
    }

    @Test
    public void singleWordGroupNameRemainsAvailableForReview() {
        var result =
                ScoreCreditsDetector.detect(
                        "Evening Concerts",
                        List.of(page(List.of(line("NORTHSTAR", 150, 180)))),
                        Set.of());
        assertTrue(result.unclassifiedCredits().contains("NORTHSTAR"));
    }

    @Test
    public void leftAlignedBadgeWithDamagedCaptionIsNotAReviewName() {
        var result =
                ScoreCreditsDetector.detect(
                        "Evening Concerts",
                        List.of(
                                page(
                                        List.of(
                                                new ScoreCreditsDetector.Line(
                                                        "AUDIO", 84, 120, 185, 144),
                                                new ScoreCreditsDetector.Line(
                                                        "ACCESS", 84, 151, 220, 175),
                                                new ScoreCreditsDetector.Line(
                                                        "INGLUDED", 84, 181, 255, 205)))),
                        Set.of());
        assertTrue(result.unclassifiedCredits().isEmpty());
    }

    @Test
    public void twoDamagedCaptionLettersStillRequireTheWholeBadge() {
        var result =
                ScoreCreditsDetector.detect(
                        "Evening Concerts",
                        List.of(
                                page(
                                        List.of(
                                                line("AUDIO", 120, 144),
                                                line("ACCESS", 151, 175),
                                                line("INGUDED", 181, 205)))),
                        Set.of());
        assertTrue(result.unclassifiedCredits().isEmpty());
    }

    @Test
    public void isolatedBadgeLikeWordDoesNotDiscardAContributor() {
        var result =
                ScoreCreditsDetector.detect(
                        "Evening Concerts",
                        List.of(page(List.of(line("Included", 150, 180)))),
                        Set.of());
        assertTrue(result.unclassifiedCredits().contains("Included"));
    }

    @Test
    public void playbackCaptionBelowCompleteBadgeIsNotAContributor() {
        var page =
                new ScoreCreditsDetector.Page(
                        1800,
                        2285,
                        -1,
                        List.of(
                                new ScoreCreditsDetector.Line(
                                        "Evening Concerts", 467, 176, 1754, 383),
                                new ScoreCreditsDetector.Line("AUDIO", 185, 250, 294, 268),
                                new ScoreCreditsDetector.Line("ACCESS", 185, 276, 323, 295),
                                new ScoreCreditsDetector.Line("INCLUDED", 188, 302, 362, 321),
                                new ScoreCreditsDetector.Line("PLAYBACK", 89, 363, 370, 415)));
        var result = ScoreCreditsDetector.detect("Evening Concerts", List.of(page), Set.of());
        assertFalse(result.unclassifiedCredits().contains("PLAYBACK"));
    }

    @Test
    public void explicitArtistRoleIsNotDiscardedByBadgeVocabulary() {
        var result =
                ScoreCreditsDetector.detect(
                        "Evening Concerts",
                        List.of(page(List.of(line("Performed by Playback", 150, 180)))),
                        Set.of());
        assertEquals("Playback", result.artist());
    }

    private static ScoreCreditsDetector.Page page(List<ScoreCreditsDetector.Line> names) {
        var lines = new java.util.ArrayList<>(names);
        lines.add(new ScoreCreditsDetector.Line("Evening Concerts", 646, 95, 1159, 226));
        return new ScoreCreditsDetector.Page(1800, 2545, 250, lines);
    }

    private static ScoreCreditsDetector.Line line(String text, float top, float bottom) {
        return new ScoreCreditsDetector.Line(text, 84, top, 288, bottom);
    }
}
