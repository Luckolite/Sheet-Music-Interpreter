// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original chord-row geometry and genuine contributor controls. */
public class AlignedChordCreditTest {
    static ScoreCreditsDetector.Line l(String s, float x, float y) {
        return new ScoreCreditsDetector.Line(s, x, y, x + 90, y + 25);
    }

    static List<ScoreCreditsDetector.Line> row(String text) {
        return List.of(l("G", 200, 300), l(text, 600, 300), l("Dm7", 1400, 300));
    }

    static boolean check(List<ScoreCreditsDetector.Line> ls, float staff) throws Exception {
        var p = new ScoreCreditsDetector.Page(1800, 2400, staff, ls, false, false);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "alignedChordReviewFragment",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, ls.get(1), ls);
    }

    static ScoreCreditsDetector.Result read(String text, Set<String> known) {
        var ls = new ArrayList<>(row(text));
        ls.add(new ScoreCreditsDetector.Line("Harbor Evening", 500, 100, 1300, 180));
        return ScoreCreditsDetector.detect(
                "Harbor Evening",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, 500, ls, false, false)),
                known);
    }

    @Test
    public void damagedAccidentalWithMinorSuffixExcluded() throws Exception {
        assertTrue(check(row("Fim"), 500));
    }

    @Test
    public void accentedAccidentalWithMinorSuffixExcluded() throws Exception {
        assertTrue(check(row("Fřm"), 500));
    }

    @Test
    public void groupedCleanChordsExcluded() throws Exception {
        assertTrue(check(row("Bb Eb"), 500));
    }

    @Test
    public void damagedFlatInGroupedChordsExcluded() throws Exception {
        assertTrue(check(row("B$ Eb"), 500));
    }

    @Test
    public void oneAnchorInsufficient() throws Exception {
        assertFalse(check(row("Fim").subList(0, 2), 500));
    }

    @Test
    public void differentRowsInsufficient() throws Exception {
        assertFalse(check(List.of(l("G", 200, 200), l("Fim", 600, 300), l("Dm7", 1400, 400)), 500));
    }

    @Test
    public void unnotatedCoverRetainsName() throws Exception {
        assertFalse(check(row("Fim"), -1));
    }

    @Test
    public void ordinaryNameInRowRemains() throws Exception {
        assertFalse(check(row("Graham"), 500));
    }

    @Test
    public void knownNameRemainsReviewable() {
        assertTrue(read("Fim", Set.of("Fim")).unclassifiedCredits().contains("Fim"));
    }

    @Test
    public void explicitComposerRemains() {
        assertEquals("Morgan Vale", read("Music by Morgan Vale", Set.of()).composer());
    }
}
