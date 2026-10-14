// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepeatedInferredReviewTest {
    static ScoreCreditsDetector.Page page(String name, boolean context) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line("Harbor Waltz", 500, 80, 1300, 145));
        lines.add(new ScoreCreditsDetector.Line(name, 1380, 190, 1750, 225));
        if (context)
            lines.add(new ScoreCreditsDetector.Line("Arranged by Casey Vale", 100, 230, 550, 260));
        return new ScoreCreditsDetector.Page(1800, 2400, 500, lines, false, false);
    }

    @Test
    public void repeatedContextResolvesDamagedReview() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Waltz",
                        List.of(
                                page("Morgan Brokfield", false),
                                page("Morgan Brookfield", true),
                                page("Morgan Brookfield", true)),
                        Set.of());
        assertTrue(r.composer().contains("Morgan Brookfield"));
        assertFalse(r.unclassifiedCredits().contains("Morgan Brokfield"));
    }

    @Test
    public void oneInferredContextDoesNotResolve() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Waltz",
                        List.of(page("Morgan Brokfield", false), page("Morgan Brookfield", true)),
                        Set.of());
        assertTrue(r.unclassifiedCredits().contains("Morgan Brokfield"));
    }

    @Test
    public void repeatedBareNameDoesNotResolve() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Waltz",
                        List.of(
                                page("Morgan Brokfield", false),
                                page("Morgan Brookfield", false),
                                page("Morgan Brookfield", false)),
                        Set.of());
        assertTrue(r.unclassifiedCredits().contains("Morgan Brokfield"));
    }

    @Test
    public void contextualRoleAndIndependentExactRepeatResolve() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Waltz",
                        List.of(
                                page("Morgan Brokfield", false),
                                page("Morgan Brookfield", true),
                                page("Morgan Brookfield", false)),
                        Set.of());
        assertTrue(r.composer().contains("Morgan Brookfield"));
        assertFalse(r.unclassifiedCredits().contains("Morgan Brokfield"));
    }
}
