// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Invented anthology layout; no score text or commercial artwork. */
public class CoverWorkCreditListTest {
    private static ScoreCreditsDetector.Line line(String t, float y) {
        return new ScoreCreditsDetector.Line(t, 680, y, 950, y + 20);
    }

    private static ScoreCreditsDetector.Page cover(boolean aliases) {
        List<ScoreCreditsDetector.Line> lines = new ArrayList<>();
        lines.add(new ScoreCreditsDetector.Line("HARBOR SONGS", 100, 40, 940, 130));
        lines.add(
                new ScoreCreditsDetector.Line(
                        "Play 4 favorites with audio tracks", 400, 240, 950, 270));
        String[] songs = {"Across the Bay", "Morning Lantern", "The Last Ferry", "A.B.C."};
        String[] bands = {
            "THE MAPLE LANTERNS", "SILVER HARBOR", "COPPER GARDEN", "THE WINTER KITES"
        };
        for (int i = 0; i < 4; i++) {
            lines.add(line(songs[i], 400 + i * 150));
            lines.add(line(bands[i], 422 + i * 150));
            if (aliases && i == 2) lines.add(line("(formerly THE AMBER WINDOWS)", 444 + i * 150));
        }
        return new ScoreCreditsDetector.Page(1000, 1400, -1, lines, false, true);
    }

    private static ScoreCreditsDetector.Result detect(ScoreCreditsDetector.Page p) {
        return ScoreCreditsDetector.detect("Harbor Songs", List.of(p), Set.of());
    }

    @Test
    public void retainsEveryPairedContributorWithoutAssigningRoles() {
        var r = detect(cover(false));
        assertEquals(
                Set.of("THE MAPLE LANTERNS", "SILVER HARBOR", "COPPER GARDEN", "THE WINTER KITES"),
                new HashSet<>(r.unclassifiedCredits()));
        assertEquals("", r.artist());
        assertEquals("", r.composer());
        assertEquals("", r.arranger());
        assertEquals("Harbor Songs", r.title());
    }

    @Test
    public void retainsPrintedFormerNameWithoutSwallowingNextWork() {
        var r = detect(cover(true));
        assertTrue(r.unclassifiedCredits().contains("THE AMBER WINDOWS"));
        assertTrue(r.unclassifiedCredits().contains("THE WINTER KITES"));
        assertFalse(r.unclassifiedCredits().contains("A.B.C."));
    }

    @Test
    public void allCapitalWorkListIsNotContributorEvidence() {
        var p = cover(false);
        var lines =
                p.lines().stream()
                        .map(
                                l ->
                                        new ScoreCreditsDetector.Line(
                                                l.text().toUpperCase(Locale.ROOT),
                                                l.left(),
                                                l.top(),
                                                l.right(),
                                                l.bottom()))
                        .toList();
        var r = detect(new ScoreCreditsDetector.Page(1000, 1400, -1, lines, false, true));
        assertFalse(r.unclassifiedCredits().contains("THE WINTER KITES"));
    }

    private static List<ScoreHeaderReadConsensus.Reading> reads(String text) {
        var rows = new ArrayList<ScoreHeaderReadConsensus.Reading>();
        for (int scale : new int[] {1, 2, 4})
            for (String mode : List.of("tight", "horizontal"))
                for (double width : new double[] {1, 1.5})
                    rows.add(new ScoreHeaderReadConsensus.Reading(scale, mode, width, text, .99));
        return rows;
    }

    @Test
    public void rereadsNamesBelowHeaderAndPreservesPrintedCase() {
        var p = cover(false);
        var line =
                p.lines().stream()
                        .filter(l -> l.text().equals("THE WINTER KITES"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(ReviewNameReadConsensus.candidates(p).contains(line));
        var merged = ReviewNameReadConsensus.merge(p, line, reads("The Winter Lights"));
        assertTrue(detect(merged).unclassifiedCredits().contains("THE WINTER LIGHTS"));
        assertFalse(detect(merged).unclassifiedCredits().contains("THE WINTER KITES"));
    }

    @Test
    public void accentIsKeptWhenRepeatedPixelReadingsAgree() {
        var p = cover(false);
        var line =
                p.lines().stream()
                        .filter(l -> l.text().equals("SILVER HARBOR"))
                        .findFirst()
                        .orElseThrow();
        var merged = ReviewNameReadConsensus.merge(p, line, reads("Jos\u00e9 Rivera"));
        assertTrue(detect(merged).unclassifiedCredits().contains("JOS\u00c9 RIVERA"));
        assertEquals("", detect(merged).composer());
    }

    @Test
    public void documentSelectionAcceptsTwoIndependentNameCorrections() {
        var p = cover(false);
        var names = ReviewNameReadConsensus.candidates(p);
        var a =
                names.stream()
                        .filter(l -> l.text().equals("SILVER HARBOR"))
                        .findFirst()
                        .orElseThrow();
        var b =
                names.stream()
                        .filter(l -> l.text().equals("THE WINTER KITES"))
                        .findFirst()
                        .orElseThrow();
        var selected =
                ReviewNameReadConsensus.selectDocument(
                        "Harbor Songs",
                        List.of(p),
                        List.of(
                                new ReviewNameReadConsensus.Proposal(
                                        0, p, a, reads("Silver Harbour")),
                                new ReviewNameReadConsensus.Proposal(
                                        0, p, b, reads("The Winter Lights"))));
        assertEquals(2, selected.accepted().size());
        assertTrue(
                selected.after()
                        .unclassifiedCredits()
                        .containsAll(List.of("SILVER HARBOUR", "THE WINTER LIGHTS")));
        assertEquals(selected.before().title(), selected.after().title());
    }

    @Test
    public void notatedPagesDoNotBecomeCoverCreditLists() {
        var p = cover(false);
        var score = new ScoreCreditsDetector.Page(1000, 1400, 300, p.lines(), false, false);
        assertTrue(ScoreCreditsDetector.coverWorkCreditCandidates(score).isEmpty());
    }

    @Test
    public void inconsistentRereadsDoNotChangeThePrintedCredit() {
        var p = cover(false);
        var name = ReviewNameReadConsensus.candidates(p).get(0);
        var values = new ArrayList<>(reads("Other Ensemble"));
        for (int i = 0; i < 6; i++) {
            var r = values.get(i);
            values.set(
                    i,
                    new ScoreHeaderReadConsensus.Reading(
                            r.scale(), r.mode(), r.widthFactor(), "Different Ensemble", .99));
        }
        assertEquals(p, ReviewNameReadConsensus.merge(p, name, values));
    }

    @Test
    public void invalidNotationGeometryIsNotAList() {
        var p = cover(false);
        assertTrue(
                ScoreCreditsDetector.coverWorkCreditCandidates(
                                new ScoreCreditsDetector.Page(1000, 1400, Float.NaN, p.lines()))
                        .isEmpty());
    }

    @Test
    public void repeatedFrontAndBackCoverCreditsCanBothBeCorrected() {
        var front = cover(false);
        var back =
                new ScoreCreditsDetector.Page(
                        front.width(), front.height(), -1, front.lines(), true, true);
        var a =
                ReviewNameReadConsensus.candidates(front).stream()
                        .filter(l -> l.text().equals("SILVER HARBOR"))
                        .findFirst()
                        .orElseThrow();
        var proposals =
                List.of(
                        new ReviewNameReadConsensus.Proposal(0, front, a, reads("Silver Harbour")),
                        new ReviewNameReadConsensus.Proposal(1, back, a, reads("Silver Harbour")));
        var selected =
                ReviewNameReadConsensus.selectDocument(
                        "Harbor Songs", List.of(front, back), proposals);
        assertEquals(2, selected.accepted().size());
        assertTrue(selected.after().unclassifiedCredits().contains("SILVER HARBOUR"));
        assertFalse(selected.after().unclassifiedCredits().contains("SILVER HARBOR"));
    }

    @Test
    public void oneCorrectedCopyDoesNotEraseAnotherPrintedVersion() {
        var front = cover(false);
        var back =
                new ScoreCreditsDetector.Page(
                        front.width(), front.height(), -1, front.lines(), true, true);
        var a =
                ReviewNameReadConsensus.candidates(front).stream()
                        .filter(l -> l.text().equals("SILVER HARBOR"))
                        .findFirst()
                        .orElseThrow();
        var selected =
                ReviewNameReadConsensus.selectDocument(
                        "Harbor Songs",
                        List.of(front, back),
                        List.of(
                                new ReviewNameReadConsensus.Proposal(
                                        0, front, a, reads("Silver Harbour"))));
        assertEquals(1, selected.accepted().size());
        assertTrue(
                selected.after()
                        .unclassifiedCredits()
                        .containsAll(List.of("SILVER HARBOUR", "SILVER HARBOR")));
    }
}
