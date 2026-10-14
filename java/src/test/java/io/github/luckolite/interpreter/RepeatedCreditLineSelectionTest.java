// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepeatedCreditLineSelectionTest {
    private final ScoreCreditsDetector.Line title =
            new ScoreCreditsDetector.Line("Morning Lantern", 350, 50, 650, 100);
    private final ScoreCreditsDetector.Line names =
            new ScoreCreditsDetector.Line("Mira Bel, Leon Hart", 650, 150, 950, 175);

    private ScoreCreditsDetector.Page page(ScoreCreditsDetector.Line line) {
        return new ScoreCreditsDetector.Page(1000, 1400, 300, List.of(title, line));
    }

    @Test
    public void locatedNames() {
        assertTrue(
                ScoreCreditsDetector.repeatedCreditLetterCandidates(page(names)).contains(names));
    }

    @Test
    public void titleExcluded() {
        assertFalse(
                ScoreCreditsDetector.repeatedCreditLetterCandidates(page(names)).contains(title));
    }

    @Test
    public void staffTextExcluded() {
        var l = new ScoreCreditsDetector.Line("Mira Bel, Leon Hart", 650, 350, 950, 375);
        assertTrue(ScoreCreditsDetector.repeatedCreditLetterCandidates(page(l)).isEmpty());
    }

    @Test
    public void photoExcluded() {
        var p = page(names);
        assertTrue(
                ScoreCreditsDetector.repeatedCreditLetterCandidates(
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
    public void preservesRolesAndOtherLines() {
        var p = page(names);
        var r =
                ScoreCreditsDetector.mergeRepeatedCreditLetter(
                        p,
                        names,
                        Collections.nCopies(3, "Mira Bell, Leon Hart"),
                        Collections.nCopies(3, .99f));
        assertEquals(title, r.get(0));
        assertEquals("Mira Bell, Leon Hart", r.get(1).text());
        assertEquals(names.left(), r.get(1).left(), 0);
    }
}
