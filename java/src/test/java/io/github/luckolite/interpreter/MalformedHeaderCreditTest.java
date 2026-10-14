// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MalformedHeaderCreditTest {
    static ScoreCreditsDetector.Page page(String text, float left, float y, boolean photo) {
        return new ScoreCreditsDetector.Page(
                1800,
                2400,
                500,
                List.of(
                        new ScoreCreditsDetector.Line("Evening Study", 500, 100, 1300, 170),
                        new ScoreCreditsDetector.Line(text, left, y, left + 220, y + 40)),
                false,
                photo);
    }

    static List<ScoreCreditsDetector.Line> repair(
            ScoreCreditsDetector.Page p, List<String> t, List<Float> c) {
        return ScoreCreditsDetector.mergeMalformedHeaderCredit(p, p.lines().get(1), t, c);
    }

    @Test
    public void recoverFromPixels() {
        var p = page("NosraM elsiL", 1450, 230, false);
        assertEquals(
                "Mara Ellis",
                repair(p, Collections.nCopies(3, "Mara Ellis"), Collections.nCopies(3, .999f))
                        .get(1)
                        .text());
    }

    @Test
    public void preserveNormalName() {
        assertTrue(
                ScoreCreditsDetector.malformedHeaderCreditCandidates(
                                page("Mara Ellis", 1450, 230, false))
                        .isEmpty());
    }

    @Test
    public void preserveCover() {
        assertTrue(
                ScoreCreditsDetector.malformedHeaderCreditCandidates(
                                page("NosraM elsiL", 1450, 230, true))
                        .isEmpty());
    }

    @Test
    public void preserveBody() {
        assertTrue(
                ScoreCreditsDetector.malformedHeaderCreditCandidates(
                                page("NosraM elsiL", 1450, 600, false))
                        .isEmpty());
    }

    @Test
    public void preserveCenteredTitle() {
        assertTrue(
                ScoreCreditsDetector.malformedHeaderCreditCandidates(
                                page("NosraM elsiL", 500, 230, false))
                        .isEmpty());
    }

    @Test
    public void rejectDisagreement() {
        var p = page("NosraM elsiL", 1450, 230, false);
        assertEquals(
                p.lines(),
                repair(
                        p,
                        List.of("Mara Ellis", "Maria Ellis", "Mara Ellis"),
                        Collections.nCopies(3, .999f)));
    }

    @Test
    public void rejectLowConfidence() {
        var p = page("NosraM elsiL", 1450, 230, false);
        assertEquals(
                p.lines(),
                repair(p, Collections.nCopies(3, "Mara Ellis"), List.of(.999f, .96f, .999f)));
    }

    @Test
    public void rejectDirection() {
        var p = page("NosraM elsiL", 1450, 230, false);
        assertEquals(
                p.lines(),
                repair(
                        p,
                        Collections.nCopies(3, "Molto Espressivo"),
                        Collections.nCopies(3, .999f)));
    }
}
