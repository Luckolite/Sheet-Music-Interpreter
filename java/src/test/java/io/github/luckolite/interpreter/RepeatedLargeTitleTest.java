// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepeatedLargeTitleTest {
    static ScoreCreditsDetector.Page page(
            String prefix, String suffix, float x, float size, float staff) {
        return new ScoreCreditsDetector.Page(
                1800,
                2400,
                staff,
                List.of(
                        new ScoreCreditsDetector.Line(prefix, 450, 100, 1750, 300),
                        new ScoreCreditsDetector.Line(suffix, x, 325, x + 500, 325 + size)),
                false,
                false);
    }

    static boolean matches(List<ScoreCreditsDetector.Page> pages, String value, String title)
            throws Exception {
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "repeatedLargeTitleFragment", String.class, List.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, value, pages, title);
    }

    @Test
    public void repeatedTitleCorroboratesDamage() throws Exception {
        assertTrue(
                matches(
                        List.of(
                                page("Morgan Vale", "SONGS", 850, 120, -1),
                                page("Morgan Vle", "SINGS", 850, 120, -1)),
                        "SINGS",
                        "Morgan Vale SONGS"));
    }

    @Test
    public void missingReferencePreserved() throws Exception {
        assertFalse(
                matches(
                        List.of(page("Morgan Vle", "SINGS", 850, 120, -1)),
                        "SINGS",
                        "Morgan Vale SONGS"));
    }

    @Test
    public void unrelatedPrefixPreserved() throws Exception {
        assertFalse(
                matches(
                        List.of(
                                page("Morgan Vale", "SONGS", 850, 120, -1),
                                page("Harbor People", "SINGS", 850, 120, -1)),
                        "SINGS",
                        "Morgan Vale SONGS"));
    }

    @Test
    public void smallCreditPreserved() throws Exception {
        assertFalse(
                matches(
                        List.of(
                                page("Morgan Vale", "SONGS", 850, 120, -1),
                                page("Morgan Vale", "SINGS", 850, 30, -1)),
                        "SINGS",
                        "Morgan Vale SONGS"));
    }

    @Test
    public void differentColumnPreserved() throws Exception {
        assertFalse(
                matches(
                        List.of(
                                page("Morgan Vale", "SONGS", 850, 120, -1),
                                page("Morgan Vale", "SINGS", 50, 120, -1)),
                        "SINGS",
                        "Morgan Vale SONGS"));
    }

    @Test
    public void scorePagePreserved() throws Exception {
        assertFalse(
                matches(
                        List.of(
                                page("Morgan Vale", "SONGS", 850, 120, -1),
                                page("Morgan Vale", "SINGS", 850, 120, 500)),
                        "SINGS",
                        "Morgan Vale SONGS"));
    }

    @Test
    public void moreThanOneEditPreserved() throws Exception {
        assertFalse(
                matches(
                        List.of(
                                page("Morgan Vale", "SONGS", 850, 120, -1),
                                page("Morgan Vale", "SANDS", 850, 120, -1)),
                        "SANDS",
                        "Morgan Vale SONGS"));
    }

    @Test
    public void damagedReferenceInsufficient() throws Exception {
        assertFalse(
                matches(
                        List.of(
                                page("Morgan Vle", "SONGS", 850, 120, -1),
                                page("Morgan Vale", "SINGS", 850, 120, -1)),
                        "SINGS",
                        "Morgan Vale SONGS"));
    }

    @Test
    public void mixedCaseNamePreserved() throws Exception {
        assertFalse(
                matches(
                        List.of(
                                page("Morgan Vale", "SONGS", 850, 120, -1),
                                page("Morgan Vale", "Sings", 850, 120, -1)),
                        "Sings",
                        "Morgan Vale SONGS"));
    }

    @Test
    public void endToEndRemovesDamagedDuplicate() {
        var r =
                ScoreCreditsDetector.detect(
                        "Morgan Vale SONGS",
                        List.of(
                                page("Morgan Vale", "SONGS", 850, 120, -1),
                                page("Morgan Vle", "SINGS", 850, 120, -1)),
                        Set.of());
        assertFalse(r.unclassifiedCredits().contains("SINGS"));
    }

    @Test
    public void knownNameNotRemoved() {
        var r =
                ScoreCreditsDetector.detect(
                        "Morgan Vale SONGS",
                        List.of(
                                page("Morgan Vale", "SONGS", 850, 120, -1),
                                page("Morgan Vle", "SINGS", 850, 120, -1)),
                        Set.of("SINGS"));
        assertTrue(r.unclassifiedCredits().contains("SINGS"));
    }
}
