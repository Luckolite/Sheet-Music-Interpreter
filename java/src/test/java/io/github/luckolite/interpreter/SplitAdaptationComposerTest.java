// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original split adaptation and numbered-work author layouts. */
public class SplitAdaptationComposerTest {
    static ScoreCreditsDetector.Result read(
            String name, String title, String prelude, boolean merged, boolean explicit) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line(title, 500, 80, 1300, 150));
        lines.add(new ScoreCreditsDetector.Line(name, 1400, 260, 1700, 300));
        if (!prelude.isEmpty())
            lines.add(new ScoreCreditsDetector.Line(prelude, 100, 225, 450, 255));
        lines.add(
                new ScoreCreditsDetector.Line(
                        "by Casey Reed (1810-1880)" + (merged ? " " + name : ""),
                        100,
                        260,
                        merged ? 1705 : 550,
                        300));
        if (explicit)
            lines.add(new ScoreCreditsDetector.Line("Music by Rowan Finch", 1250, 190, 1700, 220));
        return ScoreCreditsDetector.detect(
                title,
                List.of(new ScoreCreditsDetector.Page(1800, 2400, 400, lines, false, false)),
                Set.of());
    }

    static String title = "Symphony No. 7 in D Minor";

    @Test
    public void mergedAdaptationKeepsSeparateComposer() {
        var r = read("Morgan van Vale", title, "Adp. from arrangement", true, false);
        assertEquals("Morgan van Vale", r.composer());
        assertEquals("Casey Reed", r.arranger());
    }

    @Test
    public void separateBylineKeepsComposer() {
        var r = read("Morgan van Vale", title, "Adapted from the arrangement", false, false);
        assertEquals("Morgan van Vale", r.composer());
        assertEquals("Casey Reed", r.arranger());
    }

    @Test
    public void noAdaptationDoesNotOverrideExplicitByline() {
        var r = read("Morgan van Vale", title, "", true, false);
        assertEquals("Casey Reed", r.composer());
        assertEquals("", r.arranger());
    }

    @Test
    public void unrelatedPreludeDoesNotReassign() {
        assertEquals(
                "Casey Reed",
                read("Morgan van Vale", title, "Adapted for violin", true, false).composer());
    }

    @Test
    public void explicitComposerWins() {
        assertEquals(
                "Rowan Finch",
                read("Morgan van Vale", title, "Adp. from arrangement", true, true).composer());
    }

    @Test
    public void arbitraryNumberedTitleDoesNotInventRole() {
        assertEquals(
                "",
                read("Morgan van Vale", "Harbor No. 7", "Adp. from arrangement", true, false)
                        .composer());
    }

    @Test
    public void supportedInteriorParticles() {
        for (String n :
                List.of(
                        "Morgan de Vale",
                        "Morgan von Vale",
                        "Morgan de la Vale",
                        "Morgan della Vale",
                        "Morgan dos Vale"))
            assertEquals(n, read(n, title, "Adp. from arrangement", true, false).composer());
    }

    @Test
    public void lowercaseProseIsNotAName() {
        assertEquals(
                "",
                read("Morgan near Vale", title, "Adp. from arrangement", true, false).composer());
    }
}
