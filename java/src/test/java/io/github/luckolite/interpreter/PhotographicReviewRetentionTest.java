// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhotographicReviewRetentionTest {
    @Test
    public void distantCoverNameSurvivesMissingStaffDetectionForReview() {
        var result = cover(-1, false, "COASTAL ECHOES", 462, 96, 1335, 157);
        assertEquals("Harbor Lights", result.title());
        assertTrue(result.unclassifiedCredits().contains("COASTAL ECHOES"));
        assertEquals("", result.artist());
        assertEquals("", result.composer());
        assertEquals("", result.arranger());
    }

    @Test
    public void falseStaffInsidePhotographCannotDiscardCoverName() {
        assertTrue(
                cover(148, false, "COASTAL ECHOES", 462, 96, 1335, 157)
                        .unclassifiedCredits()
                        .contains("COASTAL ECHOES"));
    }

    @Test
    public void laterCoverDoesNotIntroduceUnlabelledContributors() {
        assertFalse(
                cover(-1, true, "COASTAL ECHOES", 462, 96, 1335, 157)
                        .unclassifiedCredits()
                        .contains("COASTAL ECHOES"));
    }

    @Test
    public void smallCornerLogoDoesNotBecomeAContributor() {
        assertFalse(
                cover(-1, false, "COASTAL ECHOES", 45, 96, 265, 119)
                        .unclassifiedCredits()
                        .contains("COASTAL ECHOES"));
    }

    @Test
    public void nearbyTitleContinuationIsNotAReviewName() {
        assertFalse(
                cover(-1, false, "COASTAL ECHOES", 462, 750, 1335, 811)
                        .unclassifiedCredits()
                        .contains("COASTAL ECHOES"));
    }

    @Test
    public void explicitCoverRoleStillHasItsPrintedMeaning() {
        var result = cover(-1, false, "Performed by Elena Stone", 462, 96, 1335, 157);
        assertEquals("Elena Stone", result.artist());
        assertFalse(result.unclassifiedCredits().contains("Performed by Elena Stone"));
    }

    private static ScoreCreditsDetector.Result cover(
            float staff,
            boolean later,
            String text,
            float left,
            float top,
            float right,
            float bottom) {
        var page =
                new ScoreCreditsDetector.Page(
                        1800,
                        1013,
                        staff,
                        List.of(
                                new ScoreCreditsDetector.Line(text, left, top, right, bottom),
                                new ScoreCreditsDetector.Line(
                                        "HARBOR LIGHTS", 460, 830, 1340, 942)),
                        later,
                        true);
        return ScoreCreditsDetector.detect("Harbor Lights", List.of(page), Set.of());
    }
}
