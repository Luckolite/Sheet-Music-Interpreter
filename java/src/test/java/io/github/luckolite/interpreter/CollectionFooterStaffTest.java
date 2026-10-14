// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CollectionFooterStaffTest {
    static boolean entry(float staff, int count, boolean caption, String value) throws Exception {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        if (caption)
            lines.add(
                    new ScoreCreditsDetector.Line(
                            "Play 8 Favorites with Sound-alike Audio Tracks", 800, 470, 1740, 520));
        for (int i = 0; i < count; i++)
            lines.add(
                    new ScoreCreditsDetector.Line(
                            i == 0 ? value : "Original Work " + i,
                            1450,
                            650 + i * 90,
                            1730,
                            680 + i * 90));
        var p = new ScoreCreditsDetector.Page(1800, 2400, staff, lines);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "collectionListEntry",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, lines.get(caption ? 1 : 0), lines);
    }

    @Test
    public void footerArtifactKeepsCollectionMeaning() throws Exception {
        assertTrue(entry(1900, 6, true, "Lantern Waltz"));
    }

    @Test
    public void absentStaffKeepsExistingBehavior() throws Exception {
        assertTrue(entry(-1, 6, true, "Lantern Waltz"));
    }

    @Test
    public void earlyStaffPreservesNames() throws Exception {
        assertFalse(entry(900, 6, true, "Jordan Vale"));
    }

    @Test
    public void noPrintedCollectionCaption() throws Exception {
        assertFalse(entry(1900, 6, false, "Jordan Vale"));
    }

    @Test
    public void tooFewItems() throws Exception {
        assertFalse(entry(1900, 3, true, "Jordan Vale"));
    }

    @Test
    public void explicitCreditPreserved() throws Exception {
        assertFalse(entry(1900, 6, true, "Music by Jordan Vale"));
    }

    @Test
    public void italianDirectionExcluded() {
        assertFalse(
                StridentDirectionTest.read(
                                "Lantern Waltz", "sempre pianissimo e senza sordini", 20, false)
                        .unclassifiedCredits()
                        .contains("sempre pianissimo e senza sordini"));
    }

    @Test
    public void italianTitlePreserved() {
        assertEquals(
                "Senza Sordini",
                StridentDirectionTest.read("Senza Sordini", "Music by Jordan Vale", 20, false)
                        .title());
    }
}
