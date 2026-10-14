// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaffTrillCreditTest {
    static boolean fragment(
            String text, float top, float bottom, float width, boolean photo, boolean credits)
            throws Exception {
        var p =
                new ScoreCreditsDetector.Page(
                        1800,
                        2400,
                        400,
                        List.of(
                                new ScoreCreditsDetector.Line(
                                        text, 1300, top, 1300 + width, bottom)),
                        credits,
                        photo);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "staffTrillFragment", String.class, List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, text, List.of(p));
    }

    @Test
    public void trillWithWavyTail() throws Exception {
        assertTrue(fragment("trm", 395, 410, 55, false, false));
    }

    @Test
    public void anotherTailReading() throws Exception {
        assertTrue(fragment("trww", 395, 410, 70, false, false));
    }

    @Test
    public void ordinaryNamePreserved() throws Exception {
        assertFalse(fragment("Tom", 395, 410, 55, false, false));
    }

    @Test
    public void capitalizedCreditPreserved() throws Exception {
        assertFalse(fragment("Trm", 395, 410, 55, false, false));
    }

    @Test
    public void headerPreserved() throws Exception {
        assertFalse(fragment("trm", 250, 270, 55, false, false));
    }

    @Test
    public void largeTextPreserved() throws Exception {
        assertFalse(fragment("trm", 380, 425, 55, false, false));
    }

    @Test
    public void wideTextPreserved() throws Exception {
        assertFalse(fragment("trm", 395, 410, 300, false, false));
    }

    @Test
    public void photographPreserved() throws Exception {
        assertFalse(fragment("trm", 395, 410, 55, true, false));
    }

    @Test
    public void creditsPagePreserved() throws Exception {
        assertFalse(fragment("trm", 395, 410, 55, false, true));
    }

    static ScoreCreditsDetector.Result read(Set<String> known, String explicit) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line("Quiet Harbor", 600, 100, 1200, 150));
        lines.add(new ScoreCreditsDetector.Line("trm", 1300, 395, 1355, 410));
        if (!explicit.isEmpty())
            lines.add(new ScoreCreditsDetector.Line(explicit, 1200, 240, 1700, 275));
        return ScoreCreditsDetector.detect(
                "Quiet Harbor",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, 400, lines, false, false)),
                known);
    }

    @Test
    public void trillRemovedFromReview() {
        assertFalse(read(Set.of(), "").unclassifiedCredits().contains("trm"));
    }

    @Test
    public void knownNameRetained() {
        assertTrue(read(Set.of("trm"), "").unclassifiedCredits().contains("trm"));
    }

    @Test
    public void explicitComposerRetained() {
        assertEquals("Morgan Vale", read(Set.of(), "Music by Morgan Vale").composer());
    }
}
