// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class CenteredWorkSubtitleTest {
    static ScoreCreditsDetector.Result read(
            String text, float x, float top, float staff, Set<String> names, boolean explicit) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line("Harbor Lanterns", 600, 80, 1200, 140));
        lines.add(new ScoreCreditsDetector.Line(text, x, top, x + 400, top + 40));
        if (explicit) lines.add(new ScoreCreditsDetector.Line("Music by", 700, 145, 1000, 170));
        return ScoreCreditsDetector.detect(
                "Harbor Lanterns",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, staff, lines, false, false)),
                names);
    }

    @Test
    public void parenthesizedWorkIsNotContributor() {
        assertTrue(
                read("(Evening Tales)", 700, 180, 400, Set.of(), false)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void catalogueWorkIsNotContributor() {
        assertTrue(
                read("Sonata K.218 (2nd Mvt)", 700, 180, 400, Set.of(), false)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void bareNameIsPreserved() {
        assertTrue(
                read("Morgan Vale", 700, 180, 400, Set.of(), false)
                        .unclassifiedCredits()
                        .contains("Morgan Vale"));
    }

    @Test
    public void knownParentheticalNameIsPreserved() {
        assertFalse(
                read("(Morgan Vale)", 700, 180, 400, Set.of("Morgan Vale"), false)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void sideParentheticalNameIsPreserved() {
        assertFalse(
                read("(Morgan Vale)", 1250, 180, 400, Set.of(), false)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void noNotationKeepsReviewEvidence() {
        assertFalse(
                read("(Morgan Vale)", 700, 180, -1, Set.of(), false)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void remoteBylineIsPreserved() {
        assertFalse(
                read("(Morgan Vale)", 700, 260, 450, Set.of(), false)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void explicitComposerIsPreserved() {
        assertTrue(
                read("(Morgan Vale)", 700, 180, 400, Set.of(), true)
                        .composer()
                        .contains("Morgan Vale"));
    }
}
