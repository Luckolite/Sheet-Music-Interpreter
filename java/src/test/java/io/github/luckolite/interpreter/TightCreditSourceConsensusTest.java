// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original pixel-reading contracts; no private score text or pixels. */
public class TightCreditSourceConsensusTest {
    static ScoreCreditsDetector.Page page(String text, boolean arranger) {
        var ls = new ArrayList<ScoreCreditsDetector.Line>();
        ls.add(new ScoreCreditsDetector.Line("Harbor Evening", 500, 70, 1200, 145));
        ls.add(new ScoreCreditsDetector.Line(text, 1300, 170, 1690, 200));
        if (arranger)
            ls.add(new ScoreCreditsDetector.Line("Arranged by Tessa Brook", 1260, 210, 1690, 240));
        return new ScoreCreditsDetector.Page(1800, 2400, 350, ls, false, false);
    }

    static List<ScoreCreditsDetector.Line> merge(
            ScoreCreditsDetector.Page p, String text, List<Float> conf) {
        return ScoreCreditsDetector.mergeTightCreditRepairConsensus(
                p, p.lines().get(1), List.of(text, text, text), conf);
    }

    static List<Float> high = List.of(.995f, .996f, .997f);

    @Test
    public void unanimousMissingFinalLetterAccepted() {
        var p = page("By MORGAN VAL", false);
        assertEquals("By MORGAN VALE", merge(p, "By MORGAN VALE", high).get(1).text());
    }

    @Test
    public void sourcePunctuationRepairAccepted() {
        var p = page("MORGAN (VALE", true);
        assertEquals("MORGAN VALE", merge(p, "MORGAN VALE", high).get(1).text());
    }

    @Test
    public void punctuationWithoutRoleContextRejected() {
        var p = page("MORGAN (VALE", false);
        assertEquals(p.lines(), merge(p, "MORGAN VALE", high));
    }

    @Test
    public void balancedParenthesisNotRemoved() {
        var p = page("MORGAN (VALE)", true);
        assertEquals(p.lines(), merge(p, "MORGAN VALE", high));
    }

    @Test
    public void lowConfidenceRejected() {
        var p = page("By MORGAN VAL", false);
        assertEquals(p.lines(), merge(p, "By MORGAN VALE", List.of(.995f, .97f, .997f)));
    }

    @Test
    public void nonfiniteConfidenceRejected() {
        var p = page("By MORGAN VAL", false);
        assertEquals(p.lines(), merge(p, "By MORGAN VALE", List.of(.995f, Float.NaN, .997f)));
    }

    @Test
    public void disagreeingReadingsRejected() {
        var p = page("By MORGAN VAL", false);
        assertEquals(
                p.lines(),
                ScoreCreditsDetector.mergeTightCreditRepairConsensus(
                        p,
                        p.lines().get(1),
                        List.of("By MORGAN VALE", "By MORGAN VALE", "By MORGAN VAL"),
                        high));
    }

    @Test
    public void roleChangeRejected() {
        var p = page("By MORGAN VAL", false);
        assertEquals(p.lines(), merge(p, "Arranged by MORGAN VALE", high));
    }

    @Test
    public void punctuationRepairCannotChangeLetters() {
        var p = page("MORGAN (VAL", true);
        assertEquals(p.lines(), merge(p, "MORGAN VALE", high));
    }

    @Test
    public void twoAddedLettersRejected() {
        var p = page("By MORGAN VA", false);
        assertEquals(p.lines(), merge(p, "By MORGAN VALE", high));
    }
}
