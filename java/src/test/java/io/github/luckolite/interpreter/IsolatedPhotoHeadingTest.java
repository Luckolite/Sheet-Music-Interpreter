// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original ambiguous photograph headings and independent title evidence. */
public class IsolatedPhotoHeadingTest {
    static ScoreCreditsDetector.Result read(String hint, String text, boolean photo, String extra) {
        var ls = new ArrayList<ScoreCreditsDetector.Line>();
        ls.add(new ScoreCreditsDetector.Line(text, 480, 100, 1000, 210));
        if (!extra.isEmpty()) ls.add(new ScoreCreditsDetector.Line(extra, 500, 400, 1300, 500));
        return ScoreCreditsDetector.detect(
                hint,
                List.of(new ScoreCreditsDetector.Page(1800, 1000, -1, ls, false, photo)),
                Set.of());
    }

    @Test
    public void ambiguousHeadingKeepsImportTitle() {
        assertEquals("Harbor Waltz", read("Harbor Waltz", "MORGAN VALE", true, "").title());
    }

    @Test
    public void ambiguousHeadingRetainedForReview() {
        assertTrue(
                read("Harbor Waltz", "MORGAN VALE", true, "")
                        .unclassifiedCredits()
                        .contains("MORGAN VALE"));
    }

    @Test
    public void noAutomaticRoleAssigned() {
        var r = read("Harbor Waltz", "MORGAN VALE", true, "");
        assertEquals("", r.artist());
        assertEquals("", r.composer());
        assertEquals("", r.arranger());
    }

    @Test
    public void matchingTitlePreserved() {
        var r = read("Harbor Waltz", "HARBOR WALTZ", true, "");
        assertEquals("Harbor Waltz", r.title());
        assertTrue(r.unclassifiedCredits().isEmpty());
    }

    @Test
    public void missingHintKeepsDetectedHeading() {
        assertEquals("MORGAN VALE", read("", "MORGAN VALE", true, "").title());
    }

    @Test
    public void nonPhotographicHeadingUnaffected() {
        assertEquals("MORGAN VALE", read("Harbor Waltz", "MORGAN VALE", false, "").title());
    }

    @Test
    public void explicitArtistNotAmbiguous() {
        assertEquals(
                "Morgan Vale", read("Harbor Waltz", "Performed by Morgan Vale", true, "").artist());
    }

    @Test
    public void singleWordWorkUnaffected() {
        assertEquals("SUNRISE", read("Harbor Waltz", "SUNRISE", true, "").title());
    }
}
