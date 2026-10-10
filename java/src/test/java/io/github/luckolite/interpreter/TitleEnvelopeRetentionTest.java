// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class TitleEnvelopeRetentionTest {
    @Test
    public void broadOcrEnvelopeCannotDiscardTwoIndependentTitleRows() {
        var page =
                new ScoreCreditsDetector.Page(
                        1800,
                        2400,
                        -1,
                        List.of(
                                line("PXRMORNINGVONVERS", 10, 100, 1670, 430),
                                line("EVENING", 530, 115, 1625, 255),
                                line("CONCERTS", 525, 255, 1630, 415)));
        assertEquals(
                "Evening Concerts",
                ScoreCreditsDetector.detect("Evening Concerts", List.of(page), Set.of()).title());
    }

    @Test
    public void nestedDuplicateFragmentsStillCannotSplitOneHeadingRow() {
        var page =
                new ScoreCreditsDetector.Page(
                        1800,
                        2500,
                        -1,
                        List.of(
                                line("Exploring", 150, 375, 1350, 615),
                                line("plo", 610, 465, 965, 600),
                                line("Landscapes", 250, 655, 1550, 940)));
        assertEquals(
                "Exploring Landscapes",
                ScoreCreditsDetector.detect("Exploring Landscapes", List.of(page), Set.of())
                        .title());
    }

    private static ScoreCreditsDetector.Line line(String text, float l, float t, float r, float b) {
        return new ScoreCreditsDetector.Line(text, l, t, r, b);
    }
}
