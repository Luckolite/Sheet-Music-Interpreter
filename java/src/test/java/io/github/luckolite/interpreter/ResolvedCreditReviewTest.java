// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResolvedCreditReviewTest {
    static ScoreCreditsDetector.Page page(String name, boolean context) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line("Harbor Waltz", 500, 80, 1300, 145));
        lines.add(new ScoreCreditsDetector.Line(name, 1380, 190, 1750, 225));
        if (context)
            lines.add(new ScoreCreditsDetector.Line("Arranged by Casey Vale", 100, 230, 550, 260));
        return new ScoreCreditsDetector.Page(1800, 2400, 500, lines, false, false);
    }

    static ScoreCreditsDetector.Result scan(String first, String second) {
        return ScoreCreditsDetector.detect(
                "Harbor Waltz", List.of(page(first, false), page(second, true)), Set.of());
    }

    @Test
    public void exactNameResolvedOnLaterPage() {
        var r = scan("Morgan Brookfield", "Morgan Brookfield");
        assertEquals("Morgan Brookfield", r.composer());
        assertFalse(r.unclassifiedCredits().contains("Morgan Brookfield"));
    }

    @Test
    public void caseVariantIsNotAnotherUnresolvedName() {
        var r = scan("MORGAN BROOKFIELD", "Morgan Brookfield");
        assertFalse(r.composer().isEmpty());
        assertTrue(r.unclassifiedCredits().isEmpty());
    }

    @Test
    public void distinctUnresolvedPersonRemains() {
        var r = scan("Rowan Vale", "Morgan Brookfield");
        assertTrue(r.unclassifiedCredits().contains("Rowan Vale"));
    }

    @Test
    public void similarSpellingStillNeedsReview() {
        var r = scan("Morgan Brokfield", "Morgan Brookfield");
        assertTrue(r.unclassifiedCredits().contains("Morgan Brokfield"));
    }

    @Test
    public void bareNameWithoutResolvedRoleRemains() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Waltz", List.of(page("Morgan Brookfield", false)), Set.of());
        assertEquals("", r.composer());
        assertTrue(r.unclassifiedCredits().contains("Morgan Brookfield"));
    }

    @Test
    public void unresolvedServiceRemains() {
        var r = scan("Meadow Transcription Studio", "Morgan Brookfield");
        assertTrue(r.unclassifiedCredits().contains("Meadow Transcription Studio"));
    }

    @Test
    public void mixedCreditRetainsTheUnresolvedPerson() {
        var r = scan("Morgan Brookfield / Rowan Vale", "Morgan Brookfield");
        assertTrue(
                r.unclassifiedCredits().stream().anyMatch(value -> value.contains("Rowan Vale")));
    }

    @Test
    public void commaGroupResolved() {
        var r = scan("Morgan Brookfield, Rowan Vale", "Morgan Brookfield; Rowan Vale");
        assertTrue(r.composer().contains("Morgan Brookfield"));
        assertTrue(r.composer().contains("Rowan Vale"));
        assertTrue(r.unclassifiedCredits().isEmpty());
    }

    @Test
    public void semicolonGroupResolved() {
        var r = scan("Morgan Brookfield; Rowan Vale", "Morgan Brookfield; Rowan Vale");
        assertTrue(r.composer().contains("Rowan Vale"));
        assertTrue(r.unclassifiedCredits().isEmpty());
    }

    @Test
    public void trailingCommaGroupResolved() {
        var r = scan("Morgan Brookfield, Rowan Vale,", "Morgan Brookfield; Rowan Vale");
        assertTrue(r.composer().contains("Rowan Vale"));
        assertTrue(r.unclassifiedCredits().isEmpty());
    }

    @Test
    public void groupWithUnresolvedNameRemains() {
        var r = scan("Morgan Brookfield, Rowan Vale", "Morgan Brookfield");
        assertTrue(r.unclassifiedCredits().stream().anyMatch(v -> v.contains("Rowan Vale")));
    }

    @Test
    public void groupWithDifferentSpellingRemains() {
        var r = scan("Morgan Brokfield, Rowan Vale", "Morgan Brookfield; Rowan Vale");
        assertTrue(r.unclassifiedCredits().stream().anyMatch(v -> v.contains("Morgan Brokfield")));
    }

    @Test
    public void groupWithUnresolvedServiceRemains() {
        var r = scan("Morgan Brookfield, Meadow Studio", "Morgan Brookfield");
        assertTrue(r.unclassifiedCredits().stream().anyMatch(v -> v.contains("Meadow Studio")));
    }
}
