// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original fictional author-work headers and cover geometry. */
public class RepeatedCoverAuthorTest {
    static ScoreCreditsDetector.Result read(
            String running, boolean notation, boolean photo, String extra) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line("M. Vale", 750, 220, 1050, 280));
        lines.add(new ScoreCreditsDetector.Line("Sonata in D Minor", 500, 340, 1300, 410));
        lines.add(new ScoreCreditsDetector.Line("Evening Lanterns", 700, 450, 1100, 495));
        if (!extra.isEmpty()) lines.add(new ScoreCreditsDetector.Line(extra, 740, 120, 1060, 180));
        return ScoreCreditsDetector.detect(
                "Imported study",
                List.of(
                        new ScoreCreditsDetector.Page(1800, 2400, -1, lines, false, photo),
                        new ScoreCreditsDetector.Page(
                                1800,
                                2400,
                                notation ? 350 : -1,
                                List.of(
                                        new ScoreCreditsDetector.Line(
                                                running, 350, 150, 1450, 195)),
                                true,
                                false)),
                Set.of());
    }

    static String header = "Vale - Sonata in D Minor - Evening Lanterns";

    @Test
    public void authorAndWorkAcrossPagesInferComposer() {
        var r = read(header, true, false, "");
        assertEquals("M. Vale", r.composer());
        assertFalse(r.unclassifiedCredits().contains("M. Vale"));
        assertFalse(r.unclassifiedCredits().contains("Evening Lanterns"));
    }

    @Test
    public void completeNameHeaderAlsoCorroborates() {
        assertEquals(
                "M. Vale",
                read("M. Vale - Sonata in D Minor - Evening Lanterns", true, false, "").composer());
    }

    @Test
    public void wrongSurnameDoesNotInfer() {
        assertEquals(
                "",
                read("Finch - Sonata in D Minor - Evening Lanterns", true, false, "").composer());
    }

    @Test
    public void differentWorkDoesNotInfer() {
        assertEquals(
                "",
                read("Vale - Sonata in G Major - Evening Lanterns", true, false, "").composer());
    }

    @Test
    public void noNotationDoesNotInfer() {
        assertEquals("", read(header, false, false, "").composer());
    }

    @Test
    public void photographDoesNotInfer() {
        assertEquals("", read(header, true, true, "").composer());
    }

    @Test
    public void explicitOtherComposerTakesPrecedence() {
        assertEquals("Rowan Finch", read(header, true, false, "Music by Rowan Finch").composer());
    }

    @Test
    public void explicitArrangerIsNotPromoted() {
        assertFalse(
                read(header, true, false, "Arranged by M. Vale").composer().contains("M. Vale"));
    }

    @Test
    public void ambiguousSurnameIsNotExpanded() {
        assertEquals("", read(header, true, false, "A. Vale").composer());
    }

    @Test
    public void unsupportedSubtitleRemainsReviewable() {
        assertTrue(
                read("Vale - Sonata in D Minor - Other Work", true, false, "")
                        .unclassifiedCredits()
                        .contains("Evening Lanterns"));
    }
}
