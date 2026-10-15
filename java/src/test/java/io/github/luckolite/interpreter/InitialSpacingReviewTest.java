// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class InitialSpacingReviewTest {
    static String key(String value) throws Exception {
        var method =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "initialSpacingReviewKey", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, value);
    }

    @Test
    public void initialSpacingOnly() throws Exception {
        assertEquals(key("A.B. Brookfield"), key("A. B.Brookfield"));
        assertEquals(key("A.B. Brookfield"), key("A.\tB.  Brookfield"));
    }

    @Test
    public void noExpansionOrLetterSubstitution() throws Exception {
        assertNotEquals(key("A.B. Brookfield"), key("Alex Blair Brookfield"));
        assertNotEquals(key("A.B. Brookfield"), key("A.C. Brookfield"));
        assertNotEquals(key("A.B. Brookfield"), key("A.B. Brokfield"));
        assertNotEquals(key("A.B. Brookfield"), key("AB Brookfield"));
    }

    @Test
    public void preserveOtherPunctuation() throws Exception {
        assertNotEquals(key("A.B. Brook-Field"), key("A.B. Brook Field"));
        assertNotEquals(key("A.B. Brookfield; Casey Vale"), key("A.B. Brookfield Casey Vale"));
        assertNotEquals(key("Studio. Name"), key("Studio.Name"));
    }

    @Test
    public void keepOriginalReviewSpellingAndRole() {
        var result =
                ScoreCreditsDetector.detect(
                        "Harbor Waltz",
                        List.of(
                                ResolvedCreditReviewTest.page("A.B. Brookfield", false),
                                ResolvedCreditReviewTest.page("A. B.Brookfield", false)),
                        Set.of());
        assertEquals("", result.composer());
        assertEquals(List.of("A.B. Brookfield"), result.unclassifiedCredits());
    }
}
