// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original author punctuation and separately attributed arranger controls. */
public class HyphenatedAuthorContextTest {
    static ScoreCreditsDetector.Result read(String name, String role) {
        var ls =
                List.of(
                        new ScoreCreditsDetector.Line("Harbor Evening", 500, 80, 1200, 155),
                        new ScoreCreditsDetector.Line(name, 1250, 180, 1700, 215),
                        new ScoreCreditsDetector.Line(role, 1230, 225, 1700, 260));
        return ScoreCreditsDetector.detect(
                "Harbor Evening",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, 400, ls, false, false)),
                Set.of());
    }

    @Test
    public void hyphenGapWithCommaArrangerRecoversAuthor() {
        var r = read("Jana Alder- Brook", "arr, by Morgan Vale");
        assertEquals("Jana Alder-Brook", r.composer());
        assertEquals("Morgan Vale", r.arranger());
    }

    @Test
    public void cleanHyphenStillWorks() {
        assertEquals(
                "Jana Alder-Brook", read("Jana Alder-Brook", "arr. by Morgan Vale").composer());
    }

    @Test
    public void enDashGapPreservesNameLetters() {
        assertEquals(
                "Jana Alder-Brook",
                read("Jana Alder– Brook", "Arranged by Morgan Vale").composer());
    }

    @Test
    public void noRoleDoesNotForceComposer() {
        assertEquals("", read("Jana Alder- Brook", "Quietly").composer());
    }

    @Test
    public void damagedLettersAreNotInvented() {
        assertNotEquals(
                "Jana Alder-Brook", read("Jana Alder- Broo", "arr. by Morgan Vale").composer());
    }

    @Test
    public void separateArtistLabelRetainsArtist() {
        assertEquals("Morgan Vale", read("Jana Alder- Brook", "Performed by Morgan Vale").artist());
    }

    @Test
    public void ordinaryComposerNameUnchanged() {
        assertEquals("Jana Alder", read("Jana Alder", "arr, by Morgan Vale").composer());
    }

    @Test
    public void ordinaryHyphenatedTitleUnchanged() {
        assertEquals("Harbor Evening", read("Jana Alder- Brook", "arr, by Morgan Vale").title());
    }
}
