// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepeatedRoleReviewEchoTest {
    static Set<String> clean(String review, Set<String> assigned, String... labels)
            throws Exception {
        var result = new LinkedHashSet<String>(List.of(review));
        var pages = new ArrayList<ScoreCreditsDetector.Page>();
        for (String label : labels)
            pages.add(
                    new ScoreCreditsDetector.Page(
                            1800,
                            2400,
                            400,
                            List.of(new ScoreCreditsDetector.Line(label, 1200, 200, 1700, 230)),
                            false,
                            false));
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "discardRepeatedRoleSpellingEcho",
                        Set.class,
                        List.class,
                        Set.class,
                        Set.class,
                        Set.class);
        m.setAccessible(true);
        m.invoke(null, result, pages, Set.of(), assigned, Set.of());
        return result;
    }

    static String full = "Morgan Brookfield",
            damaged = "Morgan Brokfield",
            label = "Music by Morgan Brookfield";

    @Test
    public void repeatedExplicitCreditRemovesEcho() throws Exception {
        assertTrue(clean(damaged, Set.of(full), label, label).isEmpty());
    }

    @Test
    public void singleCreditInsufficient() throws Exception {
        assertTrue(clean(damaged, Set.of(full), label).contains(damaged));
    }

    @Test
    public void bareNamesInsufficient() throws Exception {
        assertTrue(clean(damaged, Set.of(full), full, full).contains(damaged));
    }

    @Test
    public void unassignedCreditInsufficient() throws Exception {
        assertTrue(clean(damaged, Set.of(), label, label).contains(damaged));
    }

    @Test
    public void twoLetterDifferencePreserved() throws Exception {
        assertTrue(
                clean("Morgan Brokfeld", Set.of(full), label, label).contains("Morgan Brokfeld"));
    }

    @Test
    public void ambiguityPreserved() throws Exception {
        assertTrue(
                clean(damaged, Set.of(full, "Morgan Brockfield"), label, label).contains(damaged));
    }

    @Test
    public void lyricsDoNotEstablishComposer() throws Exception {
        assertTrue(
                clean(damaged, Set.of(full), "Lyrics by " + full, "Lyrics by " + full)
                        .contains(damaged));
    }

    @Test
    public void oneWordNamesPreserved() throws Exception {
        assertTrue(
                clean(
                                "Brookfild",
                                Set.of("Brookfield"),
                                "Music by Brookfield",
                                "Music by Brookfield")
                        .contains("Brookfild"));
    }

    static boolean instrument(String name, String repeated) throws Exception {
        var heading = new ScoreCreditsDetector.Line("Harbor Waltz", 600, 80, 1200, 140);
        var line = new ScoreCreditsDetector.Line(name, 80, 350, 180, 380);
        var lines =
                List.of(heading, line, new ScoreCreditsDetector.Line(repeated, 80, 520, 180, 550));
        var p = new ScoreCreditsDetector.Page(1800, 2400, 500, lines, false, false);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "layoutReviewCaption",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, heading, line, lines);
    }

    @Test
    public void damagedInstrumentUsesAlignedRepeat() throws Exception {
        assertTrue(instrument("Flule", "Flute"));
    }

    @Test
    public void unrelatedRepeatPreservesName() throws Exception {
        assertFalse(instrument("Flule", "Morgan"));
    }

    @Test
    public void distinctWordPreserved() throws Exception {
        assertFalse(instrument("Flora", "Flute"));
    }

    @Test
    public void animeServiceRemainsReview() {
        var p =
                new ScoreCreditsDetector.Page(
                        1800,
                        2400,
                        600,
                        List.of(
                                new ScoreCreditsDetector.Line("Harbor Waltz", 500, 80, 1300, 150),
                                new ScoreCreditsDetector.Line(
                                        "Anime: Harbor Story - North Ensemble",
                                        900,
                                        210,
                                        1750,
                                        245)),
                        false,
                        false);
        var r = ScoreCreditsDetector.detect("Harbor Waltz", List.of(p), Set.of());
        assertTrue(r.unclassifiedCredits().contains("North Ensemble"));
        assertEquals("", r.arranger());
    }
}
