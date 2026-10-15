// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProducerCreditReadConsensusTest {
    private List<ScoreHeaderReadConsensus.Reading> grid(String text) {
        var rows = new ArrayList<ScoreHeaderReadConsensus.Reading>();
        for (int scale : new int[] {1, 2, 4})
            for (String mode : List.of("tight", "horizontal"))
                for (double width : new double[] {1, 1.5})
                    rows.add(new ScoreHeaderReadConsensus.Reading(scale, mode, width, text, .99));
        return rows;
    }

    private ScoreCreditsDetector.Line line(String text) {
        return new ScoreCreditsDetector.Line(text, 1100, 230, 1630, 265);
    }

    private ScoreCreditsDetector.Page page(ScoreCreditsDetector.Line line) {
        return new ScoreCreditsDetector.Page(
                1800,
                2400,
                400,
                List.of(
                        new ScoreCreditsDetector.Line("Evening Journey", 550, 100, 1200, 165),
                        line));
    }

    @Test
    public void rolePrefixedCreditIsNominated() {
        var l = line("Produced by MISTRAL");
        assertTrue(ReviewNameReadConsensus.candidates(page(l)).contains(l));
    }

    @Test
    public void literalProducerCorrectionKeepsRoleAndFields() {
        var l = line("Produced by MÏSTRAL");
        var p = page(l);
        var out =
                ReviewNameReadConsensus.selectDocument(
                        "",
                        List.of(p),
                        List.of(
                                new ReviewNameReadConsensus.Proposal(
                                        0, p, l, grid("Produced by MISTRAL"))));
        assertEquals(1, out.accepted().size());
        assertEquals(List.of("MISTRAL"), out.after().unclassifiedCredits());
        assertEquals(out.before().title(), out.after().title());
        assertEquals(out.before().artist(), out.after().artist());
        assertEquals(out.before().composer(), out.after().composer());
        assertEquals(out.before().arranger(), out.after().arranger());
        assertEquals(out.before().creditConflicts(), out.after().creditConflicts());
        assertTrue(
                out.pages().get(0).lines().stream()
                        .anyMatch(x -> x.text().equals("Produced by MISTRAL")));
    }

    @Test
    public void preservesAccentedLiteralConsensus() {
        var l = line("Produced by AUREOLE");
        var p = page(l);
        var out = ReviewNameReadConsensus.merge(p, l, grid("Produced by AURÉOLE"));
        assertEquals(
                List.of("AURÉOLE"),
                ScoreCreditsDetector.detect("", List.of(out), Set.of()).unclassifiedCredits());
    }

    @Test
    public void missingProducerLabelCannotReplaceLine() {
        var l = line("Produced by ALICE BELL");
        var p = page(l);
        assertEquals(p, ReviewNameReadConsensus.merge(p, l, grid("ALICE BELL")));
    }

    @Test
    public void composerLabelCannotReplaceProducer() {
        var l = line("Produced by ALICE BELL");
        var p = page(l);
        assertEquals(p, ReviewNameReadConsensus.merge(p, l, grid("Music by ALICE BELL")));
    }

    @Test
    public void incompleteVotesCannotChangeCredit() {
        var l = line("Produced by MÏSTRAL");
        var p = page(l);
        assertEquals(
                p, ReviewNameReadConsensus.merge(p, l, grid("Produced by MISTRAL").subList(0, 11)));
    }

    @Test
    public void nonProducerRoleIsNotNewlyNominated() {
        var l = line("Composed by ALICE BELL");
        assertFalse(ReviewNameReadConsensus.candidates(page(l)).contains(l));
    }

    @Test
    public void singleBareTokenStillDeclined() {
        assertNull(ReviewNameReadConsensus.select(grid("MISTRAL")));
    }
}
