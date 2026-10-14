// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OverlapAccentCreditTest {
    static ScoreCreditsDetector.Page p(float previousBottom) {
        return new ScoreCreditsDetector.Page(
                1800,
                2400,
                500,
                List.of(
                        new ScoreCreditsDetector.Line(
                                "Music by Morgan Vale", 1000, 190, 1700, previousBottom),
                        new ScoreCreditsDetector.Line("Produced by JÖRG", 1300, 232, 1700, 263)),
                false,
                false);
    }

    static List<Float> high = List.of(.99f, .995f, .999f);

    static List<String> text(String s) {
        return List.of(s, s, s);
    }

    static List<ScoreCreditsDetector.Line> merge(String a, String b, List<Float> confidence) {
        var p = p(233);
        return ScoreCreditsDetector.mergeOverlappingAccentCredit(
                p, p.lines().get(1), text(a), high, text(b), confidence);
    }

    @Test
    public void boundaryExcludesPreviousDescender() {
        assertEquals(
                234,
                ScoreCreditsDetector.creditOverlapRegion(p(233), p(233).lines().get(1)).top(),
                .001);
    }

    @Test
    public void missingOverlapExcluded() {
        assertTrue(ScoreCreditsDetector.overlappingAccentCreditCandidates(p(231)).isEmpty());
    }

    @Test
    public void deepOverlapExcluded() {
        assertTrue(ScoreCreditsDetector.overlappingAccentCreditCandidates(p(245)).isEmpty());
    }

    @Test
    public void twoAgreementsRepairAccent() {
        assertEquals(
                "Produced by JORG",
                merge("Produced by JORG", "Produced by JORG", high).get(1).text());
    }

    @Test
    public void genuineAccentRetained() {
        assertEquals(
                "Produced by JÖRG",
                merge("Produced by JÖRG", "Produced by JÖRG", high).get(1).text());
    }

    @Test
    public void disagreementRejected() {
        assertEquals(
                "Produced by JÖRG",
                merge("Produced by JORG", "Produced by JÓRG", high).get(1).text());
    }

    @Test
    public void changedLettersRejected() {
        assertEquals(
                "Produced by JÖRG",
                merge("Produced by JOHN", "Produced by JOHN", high).get(1).text());
    }

    @Test
    public void changedRoleRejected() {
        assertEquals(
                "Produced by JÖRG", merge("Music by JORG", "Music by JORG", high).get(1).text());
    }

    @Test
    public void lowConfidenceRejected() {
        assertEquals(
                "Produced by JÖRG",
                merge("Produced by JORG", "Produced by JORG", List.of(.99f, .97f, .99f))
                        .get(1)
                        .text());
    }

    @Test
    public void nonfiniteConfidenceRejected() {
        assertEquals(
                "Produced by JÖRG",
                merge("Produced by JORG", "Produced by JORG", List.of(.99f, Float.NaN, .99f))
                        .get(1)
                        .text());
    }
}
