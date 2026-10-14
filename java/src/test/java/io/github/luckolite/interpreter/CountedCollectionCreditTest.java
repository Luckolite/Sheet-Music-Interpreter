// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original fictional collection captions; no private source content. */
public class CountedCollectionCreditTest {
    static ScoreCreditsDetector.Result read(String caption, String item, float x, float y) {
        return ScoreCreditsDetector.detect(
                "Harbor Favorites",
                List.of(
                        new ScoreCreditsDetector.Page(
                                1800,
                                2400,
                                -1,
                                List.of(
                                        new ScoreCreditsDetector.Line(
                                                "Harbor Favorites", 400, 80, 1700, 180),
                                        new ScoreCreditsDetector.Line(caption, 700, 220, 1600, 250),
                                        new ScoreCreditsDetector.Line(item, x, y, x + 450, y + 25)),
                                false,
                                false)),
                Set.of());
    }

    @Test
    public void includedSoundtrackListExcluded() {
        var r = read("Includes 7 hit songs from the soundtrack: Dawn Road", "River Song", 800, 270);
        assertTrue(r.unclassifiedCredits().isEmpty());
    }

    @Test
    public void countedGameThemesExcluded() {
        assertTrue(
                read("8 themes from the game", "Dawn Song • Evening Tune", 400, 270)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void greatestHitsExcluded() {
        assertTrue(
                read("7 of their greatest hits: Golden Road", "Evening Song", 1000, 285)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void favoriteHitsArrangementsExcluded() {
        assertTrue(
                read("Solo arrangements of 8 favorite hits including:", "Evening Song", 800, 275)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void explicitPerformerPreserved() {
        assertEquals(
                "Morgan Vale",
                read("8 themes from the game", "Performed by Morgan Vale", 800, 270).artist());
    }

    @Test
    public void explicitComposerPreserved() {
        assertEquals(
                "Morgan Vale",
                read("8 themes from the game", "Music by Morgan Vale", 800, 270).composer());
    }

    @Test
    public void explicitArrangerPreserved() {
        assertEquals(
                "Morgan Vale",
                read("8 themes from the game", "Arranged by Morgan Vale", 800, 270).arranger());
    }

    @Test
    public void distantNamePreserved() {
        assertTrue(
                read("8 themes from the game", "Morgan Vale", 800, 450)
                        .unclassifiedCredits()
                        .contains("Morgan Vale"));
    }

    @Test
    public void separateColumnNamePreserved() {
        assertTrue(
                read("8 themes from the game", "Morgan Vale", 50, 270)
                        .unclassifiedCredits()
                        .contains("Morgan Vale"));
    }

    @Test
    public void noCaptionNamePreserved() {
        assertTrue(
                read("Evening Collection", "Morgan Vale", 800, 270)
                        .unclassifiedCredits()
                        .contains("Morgan Vale"));
    }
}
