// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fictional score headers, authored independently of private scores. */
public class ScoreInitialCreditResolutionTest {
    private static ScoreCreditsDetector.Line l(String s, int x, int y, int w, int h) {
        return new ScoreCreditsDetector.Line(s, x, y, x + w, y + h);
    }

    private static ScoreCreditsDetector.Page p(String title, String credit) {
        return new ScoreCreditsDetector.Page(
                1000, 1400, 300, List.of(l(title, 200, 60, 600, 50), l(credit, 650, 150, 300, 25)));
    }

    private static ScoreCreditsDetector.Result scan(
            String work, String abbreviation, String... full) {
        var pages = new ArrayList<ScoreCreditsDetector.Page>();
        pages.add(p(work, abbreviation));
        for (String name : full) pages.add(p("Amber Skies", "Music by " + name));
        return ScoreCreditsDetector.detect("Imported", pages, Set.of());
    }

    @Test
    public void sameWorkInitialResolves() {
        var r = scan("Amber Skies", "M. North", "Mira North");
        assertEquals("Mira North", r.composer());
        assertFalse(r.unclassifiedCredits().contains("M. North"));
    }

    @Test
    public void unrelatedWorkRemains() {
        assertTrue(
                scan("Copper Rain", "M. North", "Mira North")
                        .unclassifiedCredits()
                        .contains("M. North"));
    }

    @Test
    public void ambiguousInitialRemains() {
        assertTrue(
                scan("Amber Skies", "M. North", "Mira North", "Mara North")
                        .unclassifiedCredits()
                        .contains("M. North"));
    }

    @Test
    public void mismatchedInitialRemains() {
        assertTrue(
                scan("Amber Skies", "E. North", "Mira North")
                        .unclassifiedCredits()
                        .contains("E. North"));
    }

    @Test
    public void missingMiddleNameRemains() {
        assertTrue(
                scan("Amber Skies", "M.L. North", "Mira North")
                        .unclassifiedCredits()
                        .contains("M.L. North"));
    }

    @Test
    public void additionalInitialsResolve() {
        assertFalse(
                scan("Amber Skies", "M. L. North", "Mira Lena North")
                        .unclassifiedCredits()
                        .contains("M. L. North"));
    }

    @Test
    public void everyOccurrenceNeedsSameWorkProof() {
        var r =
                ScoreCreditsDetector.detect(
                        "Imported",
                        List.of(
                                p("Amber Skies", "M. North"),
                                p("Copper Rain", "M. North"),
                                p("Amber Skies", "Music by Mira North")),
                        Set.of());
        assertTrue(r.unclassifiedCredits().contains("M. North"));
    }

    @Test
    public void explicitArtistRemainsArtist() {
        var r =
                ScoreCreditsDetector.detect(
                        "Imported",
                        List.of(
                                p("Amber Skies", "Performed by M. North"),
                                p("Amber Skies", "Music by Mira North")),
                        Set.of());
        assertEquals("M. North", r.artist());
        assertEquals("Mira North", r.composer());
    }

    private static ScoreCreditsDetector.Result cover(
            boolean photo, boolean repeated, String caption, int gap) {
        var name = l("M. North", 750, 400 + gap, 170, 24);
        var lines =
                new ArrayList<>(
                        List.of(
                                l("Evening Collection", 100, 50, 800, 60),
                                l(caption, 600, 365, 320, 30),
                                name));
        if (repeated) {
            lines.add(l("Copper Rain", 600, 550, 320, 30));
            lines.add(l("Quiet River", 600, 700, 320, 30));
        }
        var cover =
                new ScoreCreditsDetector.Page(
                        1000,
                        1400,
                        -1,
                        lines,
                        false,
                        photo,
                        List.of(new ScoreCreditsDetector.ReviewCredit(name, "M. North")));
        return ScoreCreditsDetector.detect(
                "Imported",
                List.of(
                        cover,
                        p("Amber Skies", "Music by Mira North"),
                        p("Copper Rain", "Music by Evan Shore"),
                        p("Quiet River", "Music by Lena Vale")),
                Set.of());
    }

    @Test
    public void repeatedScoreTitlesCorroborateCoverPair() {
        for (boolean photo : List.of(false, true))
            assertFalse(
                    cover(photo, true, "Amber Skies", 0)
                            .unclassifiedCredits()
                            .contains("M. North"));
    }

    @Test
    public void isolatedPhotoCaptionDoesNotResolve() {
        assertTrue(cover(true, false, "Amber Skies", 0).unclassifiedCredits().contains("M. North"));
    }

    @Test
    public void coverCreditForDifferentWorkRemains() {
        assertTrue(cover(true, true, "Copper Rain", 0).unclassifiedCredits().contains("M. North"));
    }

    @Test
    public void distantCoverCaptionDoesNotResolve() {
        assertTrue(
                cover(true, true, "Amber Skies", 100).unclassifiedCredits().contains("M. North"));
    }

    @Test
    public void unassignedCompetingFullNamePreventsResolution() {
        var r =
                ScoreCreditsDetector.detect(
                        "Imported",
                        List.of(
                                p("Amber Skies", "M. North"),
                                p("Amber Skies", "Music by Mira North"),
                                p("Copper Rain", "Mara North")),
                        Set.of());
        assertTrue(r.unclassifiedCredits().contains("M. North"));
    }
}
