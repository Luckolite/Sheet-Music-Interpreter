// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original collection grammar and alternating composer-column controls. */
public class CollectionThemeCaptionTest {
    static ScoreCreditsDetector.Line l(String s, float x, float y) {
        return new ScoreCreditsDetector.Line(s, x, y, x + 250, y + 30);
    }

    static boolean listed(ScoreCreditsDetector.Line q, List<ScoreCreditsDetector.Line> ls)
            throws Exception {
        var p = new ScoreCreditsDetector.Page(1800, 2400, -1, ls, false, false);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "collectionListEntry",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, q, ls);
    }

    static List<ScoreCreditsDetector.Line> column() {
        return List.of(
                l(
                        "Play 8 Rock Arrangements of Classical Themes with Sound-Alike Audio Tracks",
                        300,
                        400),
                l("Harbor Waltz", 1300, 500),
                l("MORGAN VALE", 1300, 535),
                l("Meadow Song", 1300, 600),
                l("TESSA BROOK", 1300, 635),
                l("Quiet Road", 1300, 700),
                l("KIRAN REED", 1300, 735));
    }

    static ScoreCreditsDetector.Result read(String item) {
        return CountedCollectionCreditTest.read(
                "Musie from the Motion Picture Soundtrack", item, 800, 270);
    }

    @Test
    public void themeContentsTitleExcluded() throws Exception {
        var ls = column();
        assertTrue(listed(ls.get(1), ls));
    }

    @Test
    public void pairedUppercaseComposerPreserved() throws Exception {
        var ls = column();
        assertFalse(listed(ls.get(2), ls));
    }

    @Test
    public void unanchoredTitleNotAssumedContents() throws Exception {
        var q = l("Harbor Waltz", 1300, 500);
        assertFalse(listed(q, List.of(q)));
    }

    @Test
    public void soundtrackCaptionOcrEndingExcluded() {
        assertTrue(read("River Song").unclassifiedCredits().isEmpty());
    }

    @Test
    public void explicitComposerPreserved() {
        assertEquals("Morgan Vale", read("Music by Morgan Vale").composer());
    }

    @Test
    public void explicitArrangerPreserved() {
        assertEquals("Morgan Vale", read("Arranged by Morgan Vale").arranger());
    }

    @Test
    public void explicitArtistPreserved() {
        assertEquals("Morgan Vale", read("Performed by Morgan Vale").artist());
    }

    @Test
    public void ordinaryMusicNameRemains() {
        assertTrue(
                CountedCollectionCreditTest.read("Evening Collection", "Morgan Vale", 800, 270)
                        .unclassifiedCredits()
                        .contains("Morgan Vale"));
    }
}
