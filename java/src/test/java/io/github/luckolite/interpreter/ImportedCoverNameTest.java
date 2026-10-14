// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImportedCoverNameTest {
    static boolean accepts(String name, String fallback, String title) throws Exception {
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "coverNameCorroboratedByImport", String.class, String.class, String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, name, fallback, title);
    }

    @Test
    public void surnameCorroboratesTranslatedTitle() throws Exception {
        assertTrue(accepts("MORGAN VALE", "Vale Scale Exercises", "DAS SKALENBUCH"));
    }

    @Test
    public void matchingIsCaseInsensitive() throws Exception {
        assertTrue(accepts("Morgan Vale", "VALE - Exercises", "Exercises"));
    }

    @Test
    public void unrelatedImportDoesNotCorroborate() throws Exception {
        assertFalse(accepts("Morgan Vale", "River Exercises", "Exercises"));
    }

    @Test
    public void substringDoesNotCorroborate() throws Exception {
        assertFalse(accepts("Morgan Vale", "Valerian Exercises", "Exercises"));
    }

    @Test
    public void titleOwnedPhraseExcluded() throws Exception {
        assertFalse(accepts("MORGAN VALE", "Vale Exercises", "MORGAN VALE"));
    }

    @Test
    public void singleNameNotEnough() throws Exception {
        assertFalse(accepts("VALE", "Vale Exercises", "Exercises"));
    }

    @Test
    public void shortSurnameNotEnough() throws Exception {
        assertFalse(accepts("Morgan Li", "Li Exercises", "Exercises"));
    }

    @Test
    public void printedCoverNameRetainedForReview() {
        var p =
                new ScoreCreditsDetector.Page(
                        1800,
                        2400,
                        -1,
                        List.of(
                                new ScoreCreditsDetector.Line("MORGAN VALE", 180, 230, 1620, 395),
                                new ScoreCreditsDetector.Line("DAS", 746, 587, 1051, 705),
                                new ScoreCreditsDetector.Line("SKALENBUCH", 243, 760, 1554, 879)),
                        false,
                        false);
        var r = ScoreCreditsDetector.detect("Vale Scale Exercises", List.of(p), Set.of());
        assertTrue(r.unclassifiedCredits().contains("MORGAN VALE"));
        assertEquals("", r.artist());
        assertEquals("", r.composer());
    }
}
