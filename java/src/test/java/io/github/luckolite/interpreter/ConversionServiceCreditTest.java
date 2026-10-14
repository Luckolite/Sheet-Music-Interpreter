// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversionServiceCreditTest {
    static ScoreCreditsDetector.Page page(String text) {
        return new ScoreCreditsDetector.Page(
                1800,
                2400,
                500,
                List.of(
                        new ScoreCreditsDetector.Line("Harbor Waltz", 500, 80, 1300, 150),
                        new ScoreCreditsDetector.Line(text, 100, 220, 650, 250)),
                false,
                false);
    }

    static List<ScoreCreditsDetector.Line> repair(String old, String next, List<Float> conf) {
        var p = page(old);
        return ScoreCreditsDetector.mergeTightCreditRepairConsensus(
                p, p.lines().get(1), List.of(next, next, next), conf);
    }

    static List<Float> high = List.of(.99f, .995f, .999f);

    @Test
    public void agreedServiceRepair() {
        assertEquals(
                "Converted by HarborStudio",
                repair("Converled by HarborStvdio", "Converted by HarborStudio", high)
                        .get(1)
                        .text());
    }

    @Test
    public void unrelatedCreditRejected() {
        assertEquals(
                "Composed by HarborStvdio",
                repair("Composed by HarborStvdio", "Converted by HarborStudio", high)
                        .get(1)
                        .text());
    }

    @Test
    public void newNameRejected() {
        assertEquals(
                "Converled by HarborStvdio",
                repair("Converled by HarborStvdio", "Converted by MorganVale", high).get(1).text());
    }

    @Test
    public void extraContributorRejected() {
        assertEquals(
                "Converled by HarborStvdio",
                repair(
                                "Converled by HarborStvdio",
                                "Converted by HarborStudio and MorganVale",
                                high)
                        .get(1)
                        .text());
    }

    @Test
    public void lowConfidenceRejected() {
        assertEquals(
                "Converled by HarborStvdio",
                repair(
                                "Converled by HarborStvdio",
                                "Converted by HarborStudio",
                                List.of(.99f, .97f, .99f))
                        .get(1)
                        .text());
    }

    @Test
    public void serviceStaysForReview() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Waltz", List.of(page("Converted by HarborStudio")), Set.of());
        assertTrue(r.unclassifiedCredits().contains("HarborStudio"));
        assertEquals("", r.artist());
        assertEquals("", r.composer());
        assertEquals("", r.arranger());
    }

    @Test
    public void identifiersRejected() {
        assertEquals(
                "Converled by HarborStvdio",
                repair("Converled by HarborStvdio", "Converted by HarborStudio2", high)
                        .get(1)
                        .text());
    }
}
