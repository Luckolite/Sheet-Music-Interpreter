// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BowDivisionCreditTest {
    static ScoreCreditsDetector.Result read(String text, float y) {
        var lines =
                List.of(
                        new ScoreCreditsDetector.Line("Original Study", 500, 100, 1300, 160),
                        new ScoreCreditsDetector.Line(text, 546, y, 700, y + 33));
        return ScoreCreditsDetector.detect(
                "Original Study",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, 1484, lines, true, false)),
                Set.of());
    }

    @Test
    public void middleHalfBowExcluded() {
        assertFalse(read("H.BM", 1417).unclassifiedCredits().contains("H.BM"));
    }

    @Test
    public void upperHalfBowExcluded() {
        assertFalse(read("H.B.u.", 1417).unclassifiedCredits().contains("H.B.u."));
    }

    @Test
    public void lowerHalfBowExcluded() {
        assertFalse(read("H.B.l.", 1417).unclassifiedCredits().contains("H.B.l."));
    }

    @Test
    public void wholeBowExcluded() {
        assertFalse(read("W.B.", 1417).unclassifiedCredits().contains("W.B."));
    }

    @Test
    public void shortMiddleStrokeExcluded() {
        assertFalse(read("S.St.m.", 1417).unclassifiedCredits().contains("S.St.m."));
    }

    @Test
    public void headerInitialsRetained() {
        assertTrue(read("H.BM", 200).unclassifiedCredits().contains("H.BM"));
    }

    @Test
    public void explicitComposerRetained() {
        assertEquals("H.B.M", read("Music by H.B.M", 1417).composer());
    }

    @Test
    public void otherInitialsRetained() {
        assertTrue(read("J.RV", 1417).unclassifiedCredits().contains("J.RV"));
    }
}
