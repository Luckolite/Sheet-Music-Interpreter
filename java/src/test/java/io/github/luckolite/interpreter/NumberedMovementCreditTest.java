// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original movement headings and initialed-person controls. */
public class NumberedMovementCreditTest {
    static boolean check(String work, String caption, float x, float y, float staff)
            throws Exception {
        var h = new ScoreCreditsDetector.Line(work, 500, 80, 1200, 160);
        var l = new ScoreCreditsDetector.Line(caption, x, y, x + 400, y + 55);
        var p = new ScoreCreditsDetector.Page(1800, 2400, staff, List.of(h, l), false, false);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "numberedMovementSubtitle",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        ScoreCreditsDetector.Line.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, h, l);
    }

    @Test
    public void centeredNumberedRondoIsMovement() throws Exception {
        assertTrue(check("Suite in D", "II. Rondo", 650, 180, 400));
    }

    @Test
    public void centeredNumberedAlboradaIsMovement() throws Exception {
        assertTrue(check("Capriccio in A", "I. Alborada", 650, 180, 400));
    }

    @Test
    public void initialedPersonNotMovement() throws Exception {
        assertFalse(check("Suite in D", "I. Morgan", 650, 180, 400));
    }

    @Test
    public void separateRightColumnNotMovement() throws Exception {
        assertFalse(check("Suite in D", "II. Rondo", 1350, 180, 400));
    }

    @Test
    public void absentWorkFormNotMovement() throws Exception {
        assertFalse(check("Harbor Evening", "II. Rondo", 650, 180, 400));
    }

    @Test
    public void unnotatedCoverNotMovement() throws Exception {
        assertFalse(check("Suite in D", "II. Rondo", 650, 180, -1));
    }

    @Test
    public void distantSubtitleNotClaimed() throws Exception {
        assertFalse(check("Suite in D", "II. Rondo", 650, 500, 700));
    }

    @Test
    public void explicitComposerPreserved() {
        var ls =
                List.of(
                        new ScoreCreditsDetector.Line("Suite in D", 500, 80, 1200, 160),
                        new ScoreCreditsDetector.Line("II. Rondo", 650, 180, 1050, 235),
                        new ScoreCreditsDetector.Line(
                                "Music by Morgan Vale", 1300, 240, 1740, 270));
        var r =
                ScoreCreditsDetector.detect(
                        "Suite in D",
                        List.of(new ScoreCreditsDetector.Page(1800, 2400, 400, ls, false, false)),
                        Set.of());
        assertEquals("Morgan Vale", r.composer());
    }

    static ScoreCreditsDetector.Result numbered(Set<String> known) {
        var ls =
                List.of(
                        new ScoreCreditsDetector.Line("Suite in D", 500, 80, 1200, 160),
                        new ScoreCreditsDetector.Line("I. Rondo", 650, 180, 1050, 235));
        return ScoreCreditsDetector.detect(
                "Suite in D",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, 400, ls, false, false)),
                known);
    }

    @Test
    public void initialedNameCollectionAlsoExcludesMovement() {
        assertFalse(numbered(Set.of()).unclassifiedCredits().contains("I. Rondo"));
    }

    @Test
    public void knownInitialedNameStillRetained() {
        assertTrue(numbered(Set.of("I. Rondo")).unclassifiedCredits().contains("I. Rondo"));
    }
}
