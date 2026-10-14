// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudyCaptionExpandedTest {
    static boolean caption(String text, boolean photo, float staff, String neighbor)
            throws Exception {
        var h = new ScoreCreditsDetector.Line("SCALE STUDIES", 200, 300, 1600, 420);
        var a = new ScoreCreditsDetector.Line(text, 400, 520, 1400, 570);
        var b = new ScoreCreditsDetector.Line(neighbor, 450, 620, 1350, 660);
        var lines = List.of(h, a, b);
        var p = new ScoreCreditsDetector.Page(1800, 2400, staff, lines, false, photo);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "scaleStudyCoverCaption",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, h, a, lines);
    }

    @Test
    public void scaleExercises() throws Exception {
        assertTrue(caption("Tonleiterübungen", false, -1, "für das tägliche Studium"));
    }

    @Test
    public void damagedKeyRange() throws Exception {
        assertTrue(caption("durh alle Dur- und Mol-Tonarten", false, -1, "Tonleiterübungen"));
    }

    @Test
    public void keyRange() throws Exception {
        assertTrue(caption("durch alle Dur- und Moll-Tonarten", false, -1, "Tonleiterübungen"));
    }

    @Test
    public void dailyStudy() throws Exception {
        assertTrue(caption("für das tägliche Studium", false, -1, "Tonleiterübungen"));
    }

    @Test
    public void damagedDailyStudy() throws Exception {
        assertTrue(caption("für das täglihe Studium", false, -1, "Tonleiterübungen"));
    }

    @Test
    public void nameNotCaption() throws Exception {
        assertFalse(caption("Morgan Vale", false, -1, "Tonleiterübungen"));
    }

    @Test
    public void isolatedPhraseRetained() throws Exception {
        assertFalse(caption("Tonleiterübungen", false, -1, "Morgan Vale"));
    }

    @Test
    public void photoRetained() throws Exception {
        assertFalse(caption("Tonleiterübungen", true, -1, "für das tägliche Studium"));
    }

    @Test
    public void notationPageRetained() throws Exception {
        assertFalse(caption("Tonleiterübungen", false, 800, "für das tägliche Studium"));
    }

    @Test
    public void contentsNumberHeaderExcluded() {
        var p =
                new ScoreCreditsDetector.Page(
                        1800,
                        2400,
                        -1,
                        List.of(
                                new ScoreCreditsDetector.Line("CONTENTS", 600, 100, 1200, 160),
                                new ScoreCreditsDetector.Line(
                                        "NUMBER NUMBER", 1300, 210, 1700, 250)),
                        false,
                        false);
        var r = ScoreCreditsDetector.detect("Contents", List.of(p), Set.of());
        assertFalse(r.unclassifiedCredits().contains("NUMBER NUMBER"));
    }
}
