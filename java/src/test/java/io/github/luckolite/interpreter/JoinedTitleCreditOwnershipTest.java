// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original title fragments and independent contributor controls. */
public class JoinedTitleCreditOwnershipTest {
    static ScoreCreditsDetector.Result read(
            String caption, float x, Set<String> known, String extra) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line("Lantern Waltz", 550, 100, 1250, 150));
        lines.add(new ScoreCreditsDetector.Line(caption, x, 190, x + 400, 226));
        if (!extra.isEmpty()) lines.add(new ScoreCreditsDetector.Line(extra, 1280, 245, 1720, 275));
        return ScoreCreditsDetector.detect(
                "Lantern Waltz",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, 400, lines, false, false)),
                known);
    }

    static String caption = "Harbor's Quiet Evening";

    @Test
    public void selectedSourceCaptionIsNotContributor() {
        var r = read(caption, 700, Set.of(), "");
        assertEquals("Lantern Waltz " + caption, r.title());
        assertTrue(r.unclassifiedCredits().isEmpty());
    }

    @Test
    public void separateColumnIsNotClaimed() {
        assertTrue(read(caption, 1320, Set.of(), "").unclassifiedCredits().contains(caption));
    }

    @Test
    public void knownNameRetainsReview() {
        assertTrue(read(caption, 700, Set.of(caption), "").unclassifiedCredits().contains(caption));
    }

    @Test
    public void independentNameRetainsReview() {
        assertTrue(
                read(caption, 700, Set.of(), "Morgan Vale")
                        .unclassifiedCredits()
                        .contains("Morgan Vale"));
    }

    @Test
    public void explicitComposerPreserved() {
        assertEquals(
                "Morgan Vale", read(caption, 700, Set.of(), "Music by Morgan Vale").composer());
    }

    @Test
    public void explicitArrangerPreserved() {
        assertEquals(
                "Morgan Vale", read(caption, 700, Set.of(), "Arranged by Morgan Vale").arranger());
    }

    @Test
    public void explicitArtistPreserved() {
        assertEquals(
                "Morgan Vale", read(caption, 700, Set.of(), "Performed by Morgan Vale").artist());
    }

    @Test
    public void ordinaryUnjoinedNamePreserved() {
        assertTrue(
                read("Morgan Vale", 700, Set.of(), "")
                        .unclassifiedCredits()
                        .contains("Morgan Vale"));
    }
}
