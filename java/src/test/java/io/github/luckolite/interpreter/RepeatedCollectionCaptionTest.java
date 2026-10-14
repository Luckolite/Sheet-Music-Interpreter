// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepeatedCollectionCaptionTest {
    static ScoreCreditsDetector.Line l(String s, float x, float y, float w, float h) {
        return new ScoreCreditsDetector.Line(s, x, y, x + w, y + h);
    }

    static ScoreCreditsDetector.Result read(String name, Set<String> known) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(l("Harbor Collection", 500, 70, 1000, 100));
        lines.add(l("Play 8 Favorites with Sound-Alike Audio Tracks", 900, 400, 800, 40));
        String[] songs = {
            "Quiet Morning",
            "Lantern Song",
            "Garden Waltz",
            "Evening Light",
            "Ocean Breeze",
            "Winter Path"
        };
        for (int i = 0; i < songs.length; i++) lines.add(l(songs[i], 1350, 550 + i * 90, 350, 40));
        var cover = new ScoreCreditsDetector.Page(1800, 2400, -1, lines, false, false);
        var score =
                new ScoreCreditsDetector.Page(
                        1800,
                        2400,
                        500,
                        List.of(l("Lantern Song", 500, 90, 800, 70), l(name, 1350, 220, 350, 30)),
                        false,
                        false);
        return ScoreCreditsDetector.detect("Harbor Collection", List.of(cover, score), known);
    }

    @Test
    public void repeatedTrackIsNotContributor() {
        assertFalse(
                read("Quiet Morning", Set.of()).unclassifiedCredits().contains("Quiet Morning"));
    }

    @Test
    public void differentNamePreserved() {
        assertTrue(read("Morgan Vale", Set.of()).unclassifiedCredits().contains("Morgan Vale"));
    }

    @Test
    public void knownContributorPreserved() {
        assertTrue(
                read("Quiet Morning", Set.of("Quiet Morning"))
                        .unclassifiedCredits()
                        .contains("Quiet Morning"));
    }

    @Test
    public void explicitComposerNotRemoved() {
        assertTrue(read("Music by Quiet Morning", Set.of()).composer().contains("Quiet Morning"));
    }

    @Test
    public void explicitLyricistRemainsReview() {
        assertTrue(
                read("Lyrics by Quiet Morning", Set.of())
                        .unclassifiedCredits()
                        .contains("Quiet Morning"));
    }
}
