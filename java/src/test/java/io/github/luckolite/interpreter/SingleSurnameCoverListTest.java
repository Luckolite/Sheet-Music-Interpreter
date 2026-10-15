// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SingleSurnameCoverListTest {
    static ScoreCreditsDetector.Page p(String caption, boolean capitals, int pairs) {
        var ls = new ArrayList<ScoreCreditsDetector.Line>();
        ls.add(new ScoreCreditsDetector.Line("HARBOR CLASSICS", 100, 30, 940, 130));
        ls.add(new ScoreCreditsDetector.Line(caption, 200, 240, 950, 270));
        String[] names = {"ASHFORD", "VALE", "ROWAN", "MERRICK"};
        for (int i = 0; i < pairs; i++) {
            ls.add(
                    new ScoreCreditsDetector.Line(
                            capitals ? "MORNING SONG" : "Morning Song",
                            650,
                            400 + i * 150,
                            950,
                            420 + i * 150));
            ls.add(new ScoreCreditsDetector.Line(names[i], 700, 422 + i * 150, 950, 442 + i * 150));
        }
        return new ScoreCreditsDetector.Page(1000, 1400, -1, ls, false, true);
    }

    @Test
    public void numberedAudioAnthologyRetainsSurnames() {
        var p = p("8 Violin Solos with Authentic CD Tracks", false, 4);
        assertEquals(4, ScoreCreditsDetector.coverWorkCreditCandidates(p).size());
        var r = ScoreCreditsDetector.detect("Harbor Classics", List.of(p), Set.of());
        assertTrue(
                r.unclassifiedCredits()
                        .containsAll(List.of("ASHFORD", "VALE", "ROWAN", "MERRICK")));
        assertEquals("", r.artist());
        assertEquals("", r.composer());
    }

    @Test
    public void damagedSolosWordDoesNotHideRepeatedCreditGeometry() {
        assertEquals(
                4,
                ScoreCreditsDetector.coverWorkCreditCandidates(
                                p("8Violin Solbs with Authentic CD Tracks", false, 4))
                        .size());
    }

    @Test
    public void numberedArrangementsAlsoWork() {
        assertEquals(
                4,
                ScoreCreditsDetector.coverWorkCreditCandidates(
                                p(
                                        "Play 8 Rock Arrangements with Sound-Alike Audio Tracks",
                                        false,
                                        4))
                        .size());
    }

    @Test
    public void productCaptionWithoutNumberIsNotEnough() {
        assertTrue(
                ScoreCreditsDetector.coverWorkCreditCandidates(p("Authentic CD Tracks", false, 4))
                        .isEmpty());
    }

    @Test
    public void numberedWorkListWithoutAudioIsNotEnough() {
        assertTrue(
                ScoreCreditsDetector.coverWorkCreditCandidates(p("8 Classical Studies", false, 4))
                        .isEmpty());
    }

    @Test
    public void uppercaseWorkListsRemainExcluded() {
        assertTrue(
                ScoreCreditsDetector.coverWorkCreditCandidates(
                                p("8 Violin Solos with Authentic CD Tracks", true, 4))
                        .isEmpty());
    }

    @Test
    public void twoPairsAreNotEnough() {
        assertTrue(
                ScoreCreditsDetector.coverWorkCreditCandidates(
                                p("8 Violin Solos with Authentic CD Tracks", false, 2))
                        .isEmpty());
    }

    static List<ScoreHeaderReadConsensus.Reading> reads(String text) {
        var r = new ArrayList<ScoreHeaderReadConsensus.Reading>();
        for (int scale : List.of(1, 2, 4))
            for (String mode : List.of("tight", "horizontal"))
                for (double width : List.of(1.0, 1.5))
                    r.add(new ScoreHeaderReadConsensus.Reading(scale, mode, width, text, .99));
        return r;
    }

    @Test
    public void surnameRereadIsScopedToCoverPairs() {
        var p = p("8 Violin Solos with Authentic CD Tracks", false, 4);
        var l = ScoreCreditsDetector.coverWorkCreditCandidates(p).get(0);
        var result =
                ReviewNameReadConsensus.selectDocument(
                        "Harbor Classics",
                        List.of(p),
                        List.of(new ReviewNameReadConsensus.Proposal(0, p, l, reads("Ashferd"))));
        assertEquals(1, result.accepted().size());
        assertTrue(result.after().unclassifiedCredits().contains("ASHFERD"));
        assertNull(ReviewNameReadConsensus.select(reads("Ashferd")));
    }

    @Test
    public void movementContinuationKeepsName() {
        var p = p("8 Violin Solos with Authentic CD Tracks", false, 4);
        var ls = new ArrayList<>(p.lines());
        ls.add(new ScoreCreditsDetector.Line("Evening Sonata", 650, 1000, 950, 1020));
        ls.add(new ScoreCreditsDetector.Line("(Second Movement)", 650, 1022, 950, 1042));
        ls.add(new ScoreCreditsDetector.Line("LINDEN", 700, 1044, 950, 1064));
        assertTrue(
                ScoreCreditsDetector.coverWorkCreditCandidates(
                                new ScoreCreditsDetector.Page(1000, 1400, -1, ls, false, true))
                        .stream()
                        .anyMatch(l -> l.text().equals("LINDEN")));
    }

    @Test
    public void isolatedParenthesisIsNotAWork() {
        var p = p("8 Violin Solos with Authentic CD Tracks", false, 4);
        var ls = new ArrayList<>(p.lines());
        ls.add(new ScoreCreditsDetector.Line("(Printed Abroad)", 650, 1050, 950, 1070));
        ls.add(new ScoreCreditsDetector.Line("LINDEN", 700, 1072, 950, 1092));
        assertFalse(
                ScoreCreditsDetector.coverWorkCreditCandidates(
                                new ScoreCreditsDetector.Page(1000, 1400, -1, ls, false, true))
                        .stream()
                        .anyMatch(l -> l.text().equals("LINDEN")));
    }
}
