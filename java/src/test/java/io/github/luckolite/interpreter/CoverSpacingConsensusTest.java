// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original source-spacing agreements and clipping counterexamples. */
public class CoverSpacingConsensusTest {
    static ScoreCreditsDetector.Page page(String text, float staff) {
        return new ScoreCreditsDetector.Page(
                1800,
                1000,
                staff,
                List.of(new ScoreCreditsDetector.Line(text, 450, 100, 1350, 160)),
                false,
                true);
    }

    static List<ScoreCreditsDetector.Line> merge(
            String before, String after, List<Float> confidence) {
        var p = page(before, -1);
        return ScoreCreditsDetector.mergeCoverSpacingConsensus(
                p, p.lines().get(0), List.of(after, after, after), confidence);
    }

    static List<Float> high = List.of(.995f, .996f, .997f);

    @Test
    public void fusedNameRestoresSpace() {
        assertEquals("ROWAN LAKE", merge("ROWANLAKE", "ROWAN LAKE", high).get(0).text());
    }

    @Test
    public void fragmentedWordJoins() {
        assertEquals("ROWANLAKE", merge("R OW AN L A KE", "ROWANLAKE", high).get(0).text());
    }

    @Test
    public void clippedFirstLetterRejected() {
        assertEquals("R OW AN L A KE", merge("R OW AN L A KE", "OWANLAKE", high).get(0).text());
    }

    @Test
    public void addedLetterRejected() {
        assertEquals("ROWANLAKE", merge("ROWANLAKE", "ROWANN LAKE", high).get(0).text());
    }

    @Test
    public void substitutedLetterRejected() {
        assertEquals("ROWANLAKE", merge("ROWANLAKE", "ROWAN LACE", high).get(0).text());
    }

    @Test
    public void caseChangeRejected() {
        assertEquals("ROWANLAKE", merge("ROWANLAKE", "Rowan Lake", high).get(0).text());
    }

    @Test
    public void lowConfidenceRejected() {
        assertEquals(
                "ROWANLAKE",
                merge("ROWANLAKE", "ROWAN LAKE", List.of(.99f, .97f, .99f)).get(0).text());
    }

    @Test
    public void nonfiniteRejected() {
        assertEquals(
                "ROWANLAKE",
                merge("ROWANLAKE", "ROWAN LAKE", List.of(.99f, Float.NaN, .99f)).get(0).text());
    }

    @Test
    public void disagreementRejected() {
        var p = page("ROWANLAKE", -1);
        assertEquals(
                p.lines(),
                ScoreCreditsDetector.mergeCoverSpacingConsensus(
                        p,
                        p.lines().get(0),
                        List.of("ROWAN LAKE", "ROW ANLAKE", "ROWAN LAKE"),
                        high));
    }

    @Test
    public void scoreNotCandidate() {
        var p = page("ROWANLAKE", 500);
        var score =
                new ScoreCreditsDetector.Page(
                        p.width(), p.height(), p.notationTop(), p.lines(), false, false);
        assertTrue(ScoreCreditsDetector.coverSpacingCandidates(score).isEmpty());
    }

    @Test
    public void photographicFalseStaffDoesNotHideHeading() {
        assertEquals(1, ScoreCreditsDetector.coverSpacingCandidates(page("ROWANLAKE", 148)).size());
    }

    @Test
    public void digitsNotCandidate() {
        assertTrue(ScoreCreditsDetector.coverSpacingCandidates(page("ROWANLAKE2", -1)).isEmpty());
    }

    @Test
    public void ordinaryTitlecaseNotCandidate() {
        assertTrue(ScoreCreditsDetector.coverSpacingCandidates(page("Rowan Lake", -1)).isEmpty());
    }

    @Test
    public void expandedRegionRetainsMargins() {
        var p = page("ROWANLAKE", -1);
        var r = ScoreCreditsDetector.coverSpacingRegion(p, p.lines().get(0));
        assertEquals(438f, r.left(), 0);
        assertEquals(90f, r.top(), 0);
        assertEquals(1370f, r.right(), 0);
        assertEquals(170f, r.bottom(), 0);
    }
}
