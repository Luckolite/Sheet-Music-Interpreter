// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original fictional headers; no private score text or pixels. */
public class ScoreCreditRoleContextTest {
    private static ScoreCreditsDetector.Line line(String s, int x, int y, int w, int h) {
        return new ScoreCreditsDetector.Line(s, x, y, x + w, y + h);
    }

    private static ScoreCreditsDetector.Result scan(
            String title, String name, boolean photo, int notation) {
        return ScoreCreditsDetector.detect(
                "Imported score",
                List.of(
                        new ScoreCreditsDetector.Page(
                                1000,
                                1400,
                                notation,
                                List.of(
                                        line(title, 180, 55, 640, 50),
                                        line(name, 760, 125, 170, 24)),
                                false,
                                photo)),
                Set.of());
    }

    @Test
    public void formalWorkAndPrintedKeySupportsOriginalAuthor() {
        for (String title :
                List.of(
                        "Nocturne in E flat Major",
                        "Prelude in F sharp Minor",
                        "Étude in A Minor")) {
            var r = scan(title, "Mira North", false, 250);
            assertEquals(title, r.title());
            assertEquals("Mira North", r.composer());
            assertEquals("", r.artist());
            assertEquals("", r.arranger());
            assertFalse(r.unclassifiedCredits().contains("Mira North"));
        }
    }

    @Test
    public void locationAloneDoesNotResolveAnOrdinaryByline() {
        for (String title : List.of("Amber Skies", "G minor", "Nocturne", "Nocturne in the Garden"))
            assertEquals("", scan(title, "Mira North", false, 250).composer());
    }

    @Test
    public void notationAndNonPhotographicScoreAreRequired() {
        assertEquals("", scan("Prelude in E Minor", "Mira North", true, 250).composer());
        assertEquals("", scan("Prelude in E Minor", "Mira North", false, 0).composer());
    }

    @Test
    public void explicitRolesRemainAuthoritative() {
        var r = scan("Waltz in B Minor", "Performed by Mira North", false, 250);
        assertEquals("Mira North", r.artist());
        assertEquals("", r.composer());
    }

    @Test
    public void geographicOriginIsNotAComposer() {
        for (String credit : List.of("Irish Folksong", "Ukrainian Folk Śong Shchedryk"))
            assertEquals("", scan("Prelude in C Major", credit, false, 250).composer());
    }

    @Test
    public void transcriptionServiceRemainsUnclassified() {
        var r = scan("Prelude in C Major", "Transcribed by Acorn Studio", false, 250);
        assertEquals("", r.composer());
        assertEquals("", r.arranger());
        assertTrue(r.unclassifiedCredits().stream().anyMatch(s -> s.contains("Acorn Studio")));
    }

    @Test
    public void separateExplicitComposerPreventsReassignment() {
        var r =
                ScoreCreditsDetector.detect(
                        "Imported score",
                        List.of(
                                new ScoreCreditsDetector.Page(
                                        1000,
                                        1400,
                                        300,
                                        List.of(
                                                line("Prelude in A Major", 180, 55, 640, 50),
                                                line("Mira North", 760, 125, 170, 24),
                                                line("Music by Evan Shore", 600, 165, 330, 24)))),
                        Set.of());
        assertEquals("Evan Shore", r.composer());
        assertFalse(r.composer().contains("Mira"));
    }
}
