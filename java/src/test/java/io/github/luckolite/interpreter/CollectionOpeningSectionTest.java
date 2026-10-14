// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original collection and contributor layouts; no private score text. */
public class CollectionOpeningSectionTest {
    static ScoreCreditsDetector.Result read(
            String heading, String caption, String tempo, float x, Set<String> known) {
        var lines =
                List.of(
                        new ScoreCreditsDetector.Line(heading, 500, 90, 1300, 150),
                        new ScoreCreditsDetector.Line(
                                "Composed by Morgan Vale", 1050, 210, 1700, 240),
                        new ScoreCreditsDetector.Line(tempo, 180, 350, 600, 385),
                        new ScoreCreditsDetector.Line(caption, x, 395, x + 400, 420));
        return ScoreCreditsDetector.detect(
                heading,
                List.of(new ScoreCreditsDetector.Page(1800, 2400, 450, lines, false, false)),
                known);
    }

    @Test
    public void medleySectionNotCredit() {
        assertFalse(
                read("Harbor Medley", "Lanterns at Dawn", "Slowly = 70", 230, Set.of())
                        .unclassifiedCredits()
                        .contains("Lanterns at Dawn"));
    }

    @Test
    public void suiteSectionNotCredit() {
        assertFalse(
                read("Harbor Suite", "Lanterns at Dawn", "Andante = 70", 230, Set.of())
                        .unclassifiedCredits()
                        .contains("Lanterns at Dawn"));
    }

    @Test
    public void ordinaryWorkPreservesAmbiguousName() {
        assertTrue(
                read("Harbor Waltz", "Casey Rowan", "Slowly = 70", 230, Set.of())
                        .unclassifiedCredits()
                        .contains("Casey Rowan"));
    }

    @Test
    public void knownNamePreserved() {
        assertTrue(
                read("Harbor Medley", "Casey Rowan", "Slowly = 70", 230, Set.of("Casey Rowan"))
                        .unclassifiedCredits()
                        .contains("Casey Rowan"));
    }

    @Test
    public void rightContributorPreserved() {
        assertTrue(
                read("Harbor Suite", "Casey Rowan", "Slowly = 70", 1200, Set.of())
                        .unclassifiedCredits()
                        .contains("Casey Rowan"));
    }

    @Test
    public void missingTempoPreservesName() {
        assertTrue(
                read("Harbor Medley", "Casey Rowan", "", 230, Set.of())
                        .unclassifiedCredits()
                        .contains("Casey Rowan"));
    }

    @Test
    public void explicitComposerPreserved() {
        assertEquals(
                "Morgan Vale",
                read("Harbor Medley", "Lanterns at Dawn", "Slowly = 70", 230, Set.of()).composer());
    }

    @Test
    public void explicitArtistPreserved() {
        assertEquals(
                "Casey Rowan",
                read("Harbor Suite", "Performed by Casey Rowan", "Slowly = 70", 230, Set.of())
                        .artist());
    }
}
