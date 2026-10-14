// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StackedBandLeaderReviewTest {
    static ScoreCreditsDetector.Page p(String marker, boolean photo, float staff, float surnameX) {
        return new ScoreCreditsDetector.Page(
                1800,
                1100,
                staff,
                List.of(
                        new ScoreCreditsDetector.Line("MORGAN", 700, 100, 1100, 220),
                        new ScoreCreditsDetector.Line("VALE", surnameX, 215, surnameX + 600, 335),
                        new ScoreCreditsDetector.Line(marker, 1000, 330, 1200, 360)),
                false,
                photo);
    }

    static List<?> names(ScoreCreditsDetector.Page p) throws Exception {
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "photographicBandLeader", ScoreCreditsDetector.Page.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(null, p);
    }

    @Test
    public void stackedBandLeaderRecognized() throws Exception {
        assertEquals(3, names(p("& HIS BAND", true, -1, 600)).size());
    }

    @Test
    public void femaleBandLeaderRecognized() throws Exception {
        assertEquals(3, names(p("& HER BAND", true, -1, 600)).size());
    }

    @Test
    public void neutralBandLeaderRecognized() throws Exception {
        assertEquals(3, names(p("& THEIR BAND", true, -1, 600)).size());
    }

    @Test
    public void markerRequired() throws Exception {
        assertTrue(names(p("WITH STRINGS", true, -1, 600)).isEmpty());
    }

    @Test
    public void scoreExcluded() throws Exception {
        assertTrue(names(p("& HIS BAND", false, 400, 600)).isEmpty());
    }

    @Test
    public void separateColumnsNotJoined() throws Exception {
        assertTrue(names(p("& HIS BAND", true, -1, 100)).isEmpty());
    }

    @Test
    public void resultingNameStaysReview() {
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Waltz", List.of(p("& HIS BAND", true, -1, 600)), Set.of());
        assertTrue(r.unclassifiedCredits().contains("MORGAN VALE"));
        assertFalse(r.unclassifiedCredits().contains("& HIS BAND"));
        assertEquals("", r.composer());
        assertEquals("", r.arranger());
    }
}
