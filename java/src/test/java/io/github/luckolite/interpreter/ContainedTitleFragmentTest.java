// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ContainedTitleFragmentTest {
    @Test
    public void innerOcrBoxDoesNotRepeatPartOfWrappedCoverHeading() {
        var page =
                new ScoreCreditsDetector.Page(
                        1800,
                        2545,
                        -1,
                        List.of(
                                line("Exploring", 147, 375, 1354, 611),
                                line("plor", 609, 466, 966, 602),
                                line("The", 248, 654, 792, 940),
                                line("Landscapes...", 0, 922, 1712, 1288)));
        assertEquals(
                "Exploring The Landscapes...",
                ScoreCreditsDetector.detect(
                                "Exploring The Landscapes Vol-2", List.of(page), Set.of())
                        .title());
    }

    @Test
    public void adjacentHeadingWordsRemainIndependentInk() {
        var page =
                new ScoreCreditsDetector.Page(
                        1800,
                        2545,
                        500,
                        List.of(
                                line("Northern", 350, 100, 850, 180),
                                line("Lights", 875, 100, 1300, 180)));
        assertEquals("Northern Lights", detect(page).title());
    }

    @Test
    public void titleFilteringDoesNotRemoveAnExplicitCredit() {
        var page =
                new ScoreCreditsDetector.Page(
                        1800,
                        2545,
                        500,
                        List.of(
                                line("Northern Lights", 300, 100, 1500, 200),
                                line("Arranged by Ada Stone", 600, 145, 1100, 180)));
        assertEquals("Ada Stone", detect(page).arranger());
    }

    private static ScoreCreditsDetector.Result detect(ScoreCreditsDetector.Page page) {
        return ScoreCreditsDetector.detect("Imported work", List.of(page), Set.of());
    }

    private static ScoreCreditsDetector.Line line(
            String text, float left, float top, float right, float bottom) {
        return new ScoreCreditsDetector.Line(text, left, top, right, bottom);
    }
}
