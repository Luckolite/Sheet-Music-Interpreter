// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollectionNameCropTest {
    static ScoreCreditsDetector.Page p(String caption) {
        return new ScoreCreditsDetector.Page(
                1800,
                2400,
                -1,
                List.of(
                        new ScoreCreditsDetector.Line("Classical Collection", 500, 80, 1750, 200),
                        new ScoreCreditsDetector.Line(caption, 800, 400, 1750, 440),
                        new ScoreCreditsDetector.Line("Harbor Waltz", 1400, 600, 1750, 640),
                        new ScoreCreditsDetector.Line("FALCO", 1600, 640, 1750, 680)),
                false,
                false);
    }

    static String caption = "8Violin Solbs with Authentic CD Tracks";

    static List<ScoreCreditsDetector.Line> merge(String after, List<Float> confidence) {
        var p = p(caption);
        return ScoreCreditsDetector.mergeTightCreditRepairConsensus(
                p, p.lines().get(3), List.of(after, after, after), confidence);
    }

    static List<Float> high = List.of(.99f, .995f, .999f);

    @Test
    public void pairedNameEligible() {
        assertTrue(
                ScoreCreditsDetector.tightCreditRepairCandidates(p(caption))
                        .contains(p(caption).lines().get(3)));
    }

    @Test
    public void captionRequired() {
        assertFalse(
                ScoreCreditsDetector.tightCreditRepairCandidates(p("Harbor Collection"))
                        .contains(p("Harbor Collection").lines().get(3)));
    }

    @Test
    public void agreedMissingLetterRestored() {
        assertEquals("FIALCO", merge("FIALCO", high).get(3).text());
    }

    @Test
    public void letterSubstitutionRejected() {
        assertEquals("FALCO", merge("FALCON", List.of(.99f, .97f, .99f)).get(3).text());
    }

    @Test
    public void unrelatedNameRejected() {
        assertEquals("FALCO", merge("MORGAN", high).get(3).text());
    }

    @Test
    public void extraWordRejected() {
        assertEquals("FALCO", merge("FALCO MORGAN", high).get(3).text());
    }

    @Test
    public void captionIsNotReview() {
        var r = ScoreCreditsDetector.detect("Classical Collection", List.of(p(caption)), Set.of());
        assertFalse(r.unclassifiedCredits().contains(caption));
    }
}
