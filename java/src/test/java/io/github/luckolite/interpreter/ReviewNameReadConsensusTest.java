// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ReviewNameReadConsensusTest {
    List<ScoreHeaderReadConsensus.Reading> grid(String text) {
        var r = new ArrayList<ScoreHeaderReadConsensus.Reading>();
        for (int scale : new int[] {1, 2, 4})
            for (String mode : List.of("tight", "horizontal"))
                for (double width : new double[] {1, 1.5})
                    r.add(new ScoreHeaderReadConsensus.Reading(scale, mode, width, text, .96));
        return r;
    }

    void change(List<ScoreHeaderReadConsensus.Reading> r, int i, String s, double confidence) {
        var v = r.get(i);
        r.set(
                i,
                new ScoreHeaderReadConsensus.Reading(
                        v.scale(), v.mode(), v.widthFactor(), s, confidence));
    }

    @Test
    public void literalNames() {
        assertEquals(
                "ALICE BELL / MIRA REED",
                ReviewNameReadConsensus.select(grid("-ALICE BELL /MIRA REED")).text());
    }

    @Test
    public void missingSeparatorReads() {
        var r = grid("ALICE BELL MIRA REED");
        for (int i : new int[] {1, 3, 9, 11}) change(r, i, "ALICE BELL /MIRA REED", .94);
        assertEquals("ALICE BELL / MIRA REED", ReviewNameReadConsensus.select(r).text());
    }

    @Test
    public void minoritySpellingRemainsEvidence() {
        var r = grid("ALICE BELL /MIRA REED");
        for (int i : new int[] {0, 4, 8}) change(r, i, "ALICE BELL /MIRA READ", .99);
        var s = ReviewNameReadConsensus.select(r);
        assertNotNull(s);
        assertEquals(12, s.readings().size());
        assertEquals("ALICE BELL / MIRA REED", s.text());
    }

    @Test
    public void strongCompetingSpelling() {
        var r = grid("ALICE BELL /MIRA REED");
        for (int i = 0; i < 4; i++) change(r, i, "ALICE BELL /MIRA READ", .99);
        assertNull(ReviewNameReadConsensus.select(r));
    }

    @Test
    public void neverSynthesizesName() {
        var r = grid("ALICE BELL /MIRA READ");
        assertEquals("ALICE BELL / MIRA READ", ReviewNameReadConsensus.select(r).text());
    }

    @Test
    public void lowConfidence() {
        var r = grid("ALICE BELL /MIRA REED");
        for (int i = 0; i < 12; i++) change(r, i, r.get(i).text(), .89);
        assertNull(ReviewNameReadConsensus.select(r));
    }

    @Test
    public void independentScaleRequired() {
        var r = grid("ALICE BELL /MIRA REED");
        for (int i = 8; i < 12; i++) change(r, i, r.get(i).text(), .89);
        assertNull(ReviewNameReadConsensus.select(r));
    }

    @Test
    public void conflictingSeparatorBoundaries() {
        var r = grid("ALICE BELL MIRA REED NORA GREEN");
        for (int i : new int[] {0, 2, 8, 10}) change(r, i, "ALICE BELL /MIRA REED NORA GREEN", .96);
        for (int i : new int[] {1, 3, 9, 11}) change(r, i, "ALICE BELL MIRA REED /NORA GREEN", .96);
        assertNull(ReviewNameReadConsensus.select(r));
    }

    @Test
    public void duplicateViews() {
        var r = grid("ALICE BELL /MIRA REED");
        r.set(11, r.get(0));
        assertNull(ReviewNameReadConsensus.select(r));
    }

    @Test
    public void incompleteGrid() {
        var r = grid("ALICE BELL /MIRA REED");
        r.remove(0);
        assertNull(ReviewNameReadConsensus.select(r));
    }

    @Test
    public void controlCharacter() {
        var r = grid("ALICE BELL /MIRA REED");
        change(r, 0, "ALICE\nBELL", .99);
        assertNull(ReviewNameReadConsensus.select(r));
    }

    @Test
    public void nan() {
        var r = grid("ALICE BELL /MIRA REED");
        change(r, 0, r.get(0).text(), Double.NaN);
        assertNull(ReviewNameReadConsensus.select(r));
    }

    @Test
    public void servicePunctuationNotRewritten() {
        assertNull(ReviewNameReadConsensus.select(grid("www.folk.example/credits")));
    }

    ScoreCreditsDetector.Page source() {
        return new ScoreCreditsDetector.Page(
                1800,
                2500,
                700,
                List.of(
                        new ScoreCreditsDetector.Line("Evening Lantern", 500, 100, 1300, 180),
                        new ScoreCreditsDetector.Line(
                                "-ALIce BElLMIrA ReED", 1007, 375, 1629, 431)));
    }

    @Test
    public void sourceBoundReviewRepair() {
        var p = source();
        var l = p.lines().get(1);
        assertTrue(ReviewNameReadConsensus.candidates(p).contains(l));
        var proposal = new ReviewNameReadConsensus.Proposal(0, p, l, grid("ALICE BELL /MIRA REED"));
        var result = ReviewNameReadConsensus.selectDocument("", List.of(p), List.of(proposal));
        assertEquals(1, result.accepted().size());
        assertEquals(result.before().title(), result.after().title());
        assertEquals(result.before().composer(), result.after().composer());
        assertEquals(result.before().artist(), result.after().artist());
        assertEquals(result.before().arranger(), result.after().arranger());
        assertEquals("-ALIce BElLMIrA ReED", p.lines().get(1).text());
    }

    @Test
    public void stalePageCannotMerge() {
        var p = source();
        var proposal =
                new ReviewNameReadConsensus.Proposal(
                        0, p, p.lines().get(1), grid("ALICE BELL /MIRA REED"));
        var newer = new ScoreCreditsDetector.Page(1800, 2500, 710, p.lines());
        assertTrue(
                ReviewNameReadConsensus.selectDocument("", List.of(newer), List.of(proposal))
                        .accepted()
                        .isEmpty());
    }

    @Test
    public void titleCannotBecomeCredit() {
        var p = source();
        assertSame(
                p,
                ReviewNameReadConsensus.merge(p, p.lines().get(0), grid("ALICE BELL /MIRA REED")));
    }

    @Test
    public void photographicPageNotNominated() {
        var p = source();
        assertTrue(
                ReviewNameReadConsensus.candidates(
                                new ScoreCreditsDetector.Page(
                                        p.width(),
                                        p.height(),
                                        p.notationTop(),
                                        p.lines(),
                                        false,
                                        true))
                        .isEmpty());
    }

    @Test
    public void offPageProposalIgnored() {
        var p = source();
        var proposal =
                new ReviewNameReadConsensus.Proposal(
                        1, p, p.lines().get(1), grid("ALICE BELL /MIRA REED"));
        assertTrue(
                ReviewNameReadConsensus.selectDocument("", List.of(p), List.of(proposal))
                        .accepted()
                        .isEmpty());
    }

    @Test
    public void repeatedProposalOnlyAppliedOnce() {
        var p = source();
        var proposal =
                new ReviewNameReadConsensus.Proposal(
                        0, p, p.lines().get(1), grid("ALICE BELL /MIRA REED"));
        assertEquals(
                1,
                ReviewNameReadConsensus.selectDocument("", List.of(p), List.of(proposal, proposal))
                        .accepted()
                        .size());
    }
}
