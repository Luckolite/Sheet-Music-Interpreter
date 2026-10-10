// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class SideHeaderReviewRetentionTest {
    @Test
    public void missingStaffDetectionDoesNotLoseNamesBesideOpeningHeading() {
        var result = detect(-1, false);
        assertTrue(result.unclassifiedCredits().contains("Elena Stone"));
        assertTrue(result.unclassifiedCredits().contains("Adrian Reed"));
        assertEquals("", result.artist());
        assertEquals("", result.composer());
        assertEquals("", result.arranger());
    }

    @Test
    public void detectedStaffKeepsExistingNameReview() {
        var result = detect(330, false);
        assertTrue(result.unclassifiedCredits().contains("Elena Stone"));
        assertTrue(result.unclassifiedCredits().contains("Adrian Reed"));
    }

    @Test
    public void laterUnlabelledPartHeadingDoesNotAddNewContributors() {
        var result = detect(-1, true);
        assertFalse(result.unclassifiedCredits().contains("Elena Stone"));
        assertFalse(result.unclassifiedCredits().contains("Adrian Reed"));
    }

    private static ScoreCreditsDetector.Result detect(float staff, boolean creditsOnly) {
        var page =
                new ScoreCreditsDetector.Page(
                        1800,
                        2330,
                        staff,
                        List.of(
                                new ScoreCreditsDetector.Line("Harbor Lights", 646, 95, 1159, 226),
                                new ScoreCreditsDetector.Line("Elena Stone", 84, 193, 288, 231),
                                new ScoreCreditsDetector.Line("Adrian Reed", 1472, 198, 1716, 231),
                                new ScoreCreditsDetector.Line("Violin I", 84, 353, 190, 378)),
                        creditsOnly);
        return ScoreCreditsDetector.detect("Harbor Lights", List.of(page), Set.of());
    }
}
