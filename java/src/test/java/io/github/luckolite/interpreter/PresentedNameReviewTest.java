// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresentedNameReviewTest {
    static ScoreCreditsDetector.Page p(String context) {
        return new ScoreCreditsDetector.Page(
                1800,
                2400,
                420,
                List.of(
                        new ScoreCreditsDetector.Line("Harbor Waltz", 400, 40, 1400, 130),
                        new ScoreCreditsDetector.Line("present", 800, 200, 1000, 240),
                        new ScoreCreditsDetector.Line("Marin", 700, 250, 1100, 350),
                        new ScoreCreditsDetector.Line("VALE", 650, 360, 1150, 450),
                        new ScoreCreditsDetector.Line(context, 700, 460, 1100, 500)),
                false,
                false);
    }

    static String context = "in a NewMusical Play";
    static List<Float> high = List.of(.99f, .995f, .999f);

    static List<ScoreCreditsDetector.Line> merge(String name, List<Float> confidence) {
        var p = p(context);
        return ScoreCreditsDetector.mergeTightCreditRepairConsensus(
                p, p.lines().get(2), List.of(name, name, name), confidence);
    }

    @Test
    public void largePresentedNameEligible() {
        assertTrue(
                ScoreCreditsDetector.tightCreditRepairCandidates(p(context))
                        .contains(p(context).lines().get(2)));
    }

    @Test
    public void contextRequired() {
        assertFalse(
                ScoreCreditsDetector.tightCreditRepairCandidates(p("Songbook"))
                        .contains(p("Songbook").lines().get(2)));
    }

    @Test
    public void agreeingRereadsRepairName() {
        assertEquals("Marion", merge("Marion", high).get(2).text());
    }

    @Test
    public void unrelatedNameRejected() {
        assertEquals("Marin", merge("Josephine", high).get(2).text());
    }

    @Test
    public void extraNameRejected() {
        assertEquals("Marin", merge("Marion Casey", high).get(2).text());
    }

    @Test
    public void lowConfidenceRejected() {
        assertEquals("Marin", merge("Marion", List.of(.99f, .97f, .99f)).get(2).text());
    }

    @Test
    public void joinedNameStaysForReview() {
        var lines = merge("Marion", high);
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Waltz",
                        List.of(
                                new ScoreCreditsDetector.Page(
                                        1800, 2400, 420, lines, false, false)),
                        Set.of());
        assertTrue(r.unclassifiedCredits().contains("Marion VALE"));
        assertFalse(r.unclassifiedCredits().contains("Marion"));
        assertEquals("", r.composer());
        assertEquals("", r.arranger());
    }
}
