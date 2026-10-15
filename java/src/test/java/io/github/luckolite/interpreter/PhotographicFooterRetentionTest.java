// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhotographicFooterRetentionTest {
    private static ScoreCreditsDetector.Page page(String footer, float staff) {
        return new ScoreCreditsDetector.Page(
                1000,
                1400,
                staff,
                List.of(
                        new ScoreCreditsDetector.Line("MORNING HARBOUR", 100, 40, 900, 110),
                        new ScoreCreditsDetector.Line(footer, 180, 1250, 820, 1310)),
                false,
                true);
    }

    @Test
    public void photographicFooterRemainsForReviewWithoutAssignedRole() {
        var p = page("ROWAN VALE", -1);
        var r = ScoreCreditsDetector.detect("", List.of(p), Set.of());
        assertEquals(List.of("ROWAN VALE"), r.unclassifiedCredits());
        assertEquals("", r.artist());
        assertEquals("", r.composer());
        assertEquals("", r.arranger());
    }

    @Test
    public void footerCannotBeRewrittenByGeneralHeadingConsensus() {
        var p = page("ROWAN H A RLS", -1);
        assertEquals(
                List.of(p.lines().get(1)), ScoreCreditsDetector.coverFooterReviewCandidates(p));
        assertFalse(
                ScoreCreditsDetector.locatedCoverHeadingCandidates(p).contains(p.lines().get(1)));
        assertNull(
                ScoreCreditsDetector.locatedCoverHeadingReading(
                        p, p.lines().get(1), List.of("ROWANHARIS", "ROWANHARIS", "ROWANHARIS")));
    }

    @Test
    public void guardedFooterConsensusCanRepairFragmentedName() {
        var p = page("ROWAN H A RLS", -1);
        var lines =
                ScoreCreditsDetector.mergeCoverFooterReviewConsensus(
                        p,
                        p.lines().get(1),
                        List.of("ROWAN HARIS", "ROWAN HARIS", "ROWAN HARIS"),
                        List.of(.99f, .99f, .99f));
        assertEquals("ROWAN HARIS", lines.get(1).text());
    }

    @Test
    public void wordBoundaryCannotDisappear() {
        var p = page("ROWAN VALE", -1);
        assertEquals(
                p.lines(),
                ScoreCreditsDetector.mergeCoverFooterReviewConsensus(
                        p,
                        p.lines().get(1),
                        List.of("ROWANVALE", "ROWANVALE", "ROWANVALE"),
                        List.of(.99f, .99f, .99f)));
    }

    @Test
    public void notationKeepsFooterRouteClosed() {
        assertTrue(
                ScoreCreditsDetector.coverFooterReviewCandidates(page("ROWAN VALE", 200))
                        .isEmpty());
    }

    @Test
    public void publisherAndLegalCaptionsRemainExcluded() {
        for (String s : List.of("NORTH PRESS", "COPYRIGHT RESERVED", "VIOLIN SOLO"))
            assertTrue(ScoreCreditsDetector.coverFooterReviewCandidates(page(s, -1)).isEmpty());
    }
}
