// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PicturedPublicationReviewTest {
    static ScoreCreditsDetector.Result read(
            boolean caption, boolean inner, boolean footer, float staff, boolean explicit) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line("RIVER PRESS", 10, 20, 220, 45));
        lines.add(new ScoreCreditsDetector.Line("Night Songs", 250, 90, 740, 160));
        if (caption)
            lines.add(
                    new ScoreCreditsDetector.Line(
                            "9 of their greatest hits: First Light", 300, 190, 760, 220));
        lines.add(
                new ScoreCreditsDetector.Line(
                        explicit ? "Composed by Mara Ellis" : "Mara Ellis", 300, 310, 650, 330));
        if (inner) lines.add(new ScoreCreditsDetector.Line("RIVER PRESS", 500, 680, 700, 710));
        if (footer) lines.add(new ScoreCreditsDetector.Line("RIVER PRESS", 300, 940, 550, 970));
        return ScoreCreditsDetector.detect(
                "Night Songs",
                List.of(new ScoreCreditsDetector.Page(800, 1000, staff, lines)),
                Set.of());
    }

    @Test
    public void embeddedNameIsNotCurrentWorkCredit() {
        assertFalse(read(true, true, true, -1, false).unclassifiedCredits().contains("Mara Ellis"));
    }

    @Test
    public void nameWithoutInnerImprintRemainsReview() {
        assertTrue(read(true, false, true, -1, false).unclassifiedCredits().contains("Mara Ellis"));
    }

    @Test
    public void noHitsListRetainsName() {
        assertTrue(read(false, true, true, -1, false).unclassifiedCredits().contains("Mara Ellis"));
    }

    @Test
    public void noFooterRetainsName() {
        assertTrue(read(true, true, false, -1, false).unclassifiedCredits().contains("Mara Ellis"));
    }

    @Test
    public void scorePageRetainsName() {
        assertTrue(read(true, true, true, 800, false).unclassifiedCredits().contains("Mara Ellis"));
    }

    @Test
    public void explicitCreditIsNotDiscarded() {
        assertTrue(read(true, true, true, -1, true).composer().contains("Mara Ellis"));
    }
}
