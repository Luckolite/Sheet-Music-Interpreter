// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConvertedSourceSubtitleTest {
    static ScoreCreditsDetector.Result read(String left, String right, Set<String> known) {
        var p =
                new ScoreCreditsDetector.Page(
                        1800,
                        2330,
                        500,
                        List.of(
                                new ScoreCreditsDetector.Line("Decision", 775, 102, 1025, 154),
                                new ScoreCreditsDetector.Line(
                                        "Northern Journey", 748, 182, 1055, 221),
                                new ScoreCreditsDetector.Line(left, 86, 202, 471, 237),
                                new ScoreCreditsDetector.Line(right, 1428, 206, 1717, 233)),
                        false,
                        false);
        return ScoreCreditsDetector.detect("Decision", List.of(p), known);
    }

    @Test
    public void convertedSourceSubtitleExcluded() {
        assertFalse(
                read("Converted by Score Service", "Mara Ellis", Set.of())
                        .unclassifiedCredits()
                        .contains("Northern Journey"));
    }

    @Test
    public void knownNameRetained() {
        assertTrue(
                read("Converted by Score Service", "Mara Ellis", Set.of("Northern Journey"))
                        .unclassifiedCredits()
                        .contains("Northern Journey"));
    }

    @Test
    public void noConversionRetained() {
        assertTrue(
                read("Edited by Score Service", "Mara Ellis", Set.of())
                        .unclassifiedCredits()
                        .contains("Northern Journey"));
    }

    @Test
    public void explicitArrangerDoesNotDisplaceCenteredName() {
        assertTrue(
                read("Converted by Score Service", "Arr. Mara Ellis", Set.of())
                        .unclassifiedCredits()
                        .contains("Northern Journey"));
    }

    @Test
    public void noSeparatePersonRetained() {
        assertTrue(
                read("Converted by Score Service", "Flute", Set.of())
                        .unclassifiedCredits()
                        .contains("Northern Journey"));
    }
}
