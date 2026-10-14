// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original contents layouts and contributor controls, without private source text. */
public class CollectionContentsLayoutTest {
    static ScoreCreditsDetector.Line line(String s, float x, float y, float w) {
        return new ScoreCreditsDetector.Line(s, x, y, x + w, y + 35);
    }

    static boolean check(String method, ScoreCreditsDetector.Page p, ScoreCreditsDetector.Line l)
            throws Exception {
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        method,
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, l, p.lines());
    }

    static ScoreCreditsDetector.Page contents(
            boolean left, boolean playback, float staff, int count) {
        var ls = new ArrayList<ScoreCreditsDetector.Line>();
        if (playback) ls.add(line("Speed Pitch Balance Loop", 60, 150, 300));
        for (int i = 0; i < count; i++) {
            ls.add(line("Harbor Song " + (char) ('A' + i), 500, 650 + i * 85, 220));
            ls.add(
                    line(
                            left ? "" + (i + 2) : "..." + (i + 2),
                            left ? 400 : 1200,
                            650 + i * 85,
                            35));
        }
        return new ScoreCreditsDetector.Page(1800, 2400, staff, ls, false, false);
    }

    @Test
    public void leftPageNumberColumnIsContents() throws Exception {
        var p = contents(true, false, -1, 5);
        assertTrue(check("numberedContentsEntry", p, p.lines().get(0)));
    }

    @Test
    public void dottedRightColumnIsContents() throws Exception {
        var p = contents(false, false, -1, 5);
        assertTrue(check("numberedContentsEntry", p, p.lines().get(0)));
    }

    @Test
    public void playbackContextToleratesFalseStaff() throws Exception {
        var p = contents(false, true, 700, 5);
        assertTrue(check("numberedContentsEntry", p, p.lines().get(1)));
    }

    @Test
    public void actualStaffWithoutPlaybackIsNotContents() throws Exception {
        var p = contents(false, false, 700, 5);
        assertFalse(check("numberedContentsEntry", p, p.lines().get(0)));
    }

    @Test
    public void ThreeNumberedRowsAreInsufficient() throws Exception {
        var p = contents(true, false, -1, 3);
        assertFalse(check("numberedContentsEntry", p, p.lines().get(0)));
    }

    @Test
    public void unrelatedContributorOutsideColumnSurvives() throws Exception {
        var p = contents(true, false, -1, 5);
        assertFalse(check("numberedContentsEntry", p, line("Morgan Vale", 1300, 600, 250)));
    }

    @Test
    public void explicitCreditIsNotContents() throws Exception {
        var p = contents(true, false, -1, 5);
        assertFalse(check("numberedContentsEntry", p, line("Music by Morgan Vale", 500, 650, 300)));
    }

    @Test
    public void leftAlignedCoverListIsRecognized() throws Exception {
        var ls = new ArrayList<ScoreCreditsDetector.Line>();
        ls.add(line("Play 6 Favorites with Sound-Alike Audio Tracks", 900, 430, 750));
        for (int i = 0; i < 6; i++)
            ls.add(line("Harbor Song " + (char) ('A' + i), 1400, 600 + i * 110, 130 + i * 20));
        var p = new ScoreCreditsDetector.Page(1800, 2400, -1, ls, false, false);
        assertTrue(check("collectionListEntry", p, ls.get(1)));
    }

    static boolean medley(String heading, String text, boolean author, float x) throws Exception {
        var h = new ScoreCreditsDetector.Line(heading, 500, 100, 1300, 170);
        var l = new ScoreCreditsDetector.Line(text, x, 190, x + 800, 225);
        var ls = new ArrayList<ScoreCreditsDetector.Line>(List.of(h, l));
        if (author) ls.add(line("Morgan Vale", 800, 260, 200));
        var p = new ScoreCreditsDetector.Page(1800, 2400, 440, ls, false, false);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "medleyContentsSubtitle",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, h, l, ls);
    }

    @Test
    public void centeredCommaSeparatedMedleyContents() throws Exception {
        assertTrue(medley("Harbor Medley", "Morning Song, Moonlight, Returning Home", true, 500));
    }

    @Test
    public void centeredBulletSeparatedMedleyContents() throws Exception {
        assertTrue(medley("Harbor Medley", "Morning Song • Moonlight Returning Home", true, 500));
    }

    @Test
    public void absentSeparateBylineRetainsReview() throws Exception {
        assertFalse(medley("Harbor Medley", "Morning Song, Moonlight, Returning Home", false, 500));
    }

    @Test
    public void nonmedleyHeadingDoesNotClaimList() throws Exception {
        assertFalse(medley("Harbor Evening", "Morning Song, Moonlight, Returning Home", true, 500));
    }

    @Test
    public void explicitComposersRetained() throws Exception {
        assertFalse(
                medley(
                        "Harbor Medley",
                        "Music by Morgan Vale, Tessa Brook, Rowan Lake",
                        true,
                        500));
    }

    @Test
    public void sideColumnNotMedleyContents() throws Exception {
        assertFalse(medley("Harbor Medley", "Morning Song, Moonlight, Returning Home", true, 1100));
    }

    @Test
    public void fullContentsExtractionPreservesExplicitComposer() {
        var p = contents(true, false, -1, 5);
        var ls = new ArrayList<>(p.lines());
        ls.add(new ScoreCreditsDetector.Line("Harbor Collection", 500, 100, 1300, 190));
        ls.add(line("Music by Morgan Vale", 1300, 250, 400));
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Collection",
                        List.of(new ScoreCreditsDetector.Page(1800, 2400, -1, ls, false, false)),
                        Set.of());
        assertEquals("Morgan Vale", r.composer());
        assertFalse(r.unclassifiedCredits().contains("Harbor Song A"));
    }

    @Test
    public void fullMedleyExtractionPreservesTitleAndByline() {
        var ls =
                List.of(
                        new ScoreCreditsDetector.Line("Harbor Medley", 500, 100, 1300, 170),
                        line("Morning Song, Moonlight, Returning Home", 500, 190, 800),
                        line("Music by Morgan Vale", 800, 260, 350));
        var r =
                ScoreCreditsDetector.detect(
                        "Harbor Medley",
                        List.of(new ScoreCreditsDetector.Page(1800, 2400, 440, ls, false, false)),
                        Set.of());
        assertEquals("Harbor Medley", r.title());
        assertEquals("Morgan Vale", r.composer());
        assertFalse(r.unclassifiedCredits().contains("Morning Song, Moonlight, Returning Home"));
    }
}
