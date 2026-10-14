// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original clipped instrument part headings and protected cover/name controls. */
public class ClippedInstrumentPartTest {
    static boolean check(String text, float x, float staff) throws Exception {
        var l = new ScoreCreditsDetector.Line(text, x, 80, x + 200, 175);
        var p = new ScoreCreditsDetector.Page(1800, 2400, staff, List.of(l), false, false);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "clippedInstrumentLabel",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, l);
    }

    static ScoreCreditsDetector.Result read() {
        var ls =
                List.of(
                        new ScoreCreditsDetector.Line("iolin u", 35, 80, 245, 175),
                        new ScoreCreditsDetector.Line(
                                "Overture to Harbor Morning", 260, 20, 1490, 92),
                        new ScoreCreditsDetector.Line("Morgan Vale", 1380, 140, 1660, 170),
                        new ScoreCreditsDetector.Line("arr. by Tessa Brook", 1360, 174, 1680, 207));
        return ScoreCreditsDetector.detect(
                "Collected Music",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, 210, ls, true, false)),
                Set.of());
    }

    @Test
    public void romanPartSuffixExcluded() throws Exception {
        assertTrue(check("iolin II", 35, 300));
    }

    @Test
    public void numericPartSuffixExcluded() throws Exception {
        assertTrue(check("ello 2", 35, 300));
    }

    @Test
    public void mergedRomanPartGlyphExcluded() throws Exception {
        assertTrue(check("iolin u", 35, 300));
    }

    @Test
    public void centeredLabelNotAssumedClipped() throws Exception {
        assertFalse(check("iolin II", 700, 300));
    }

    @Test
    public void coverNameNotAssumedPart() throws Exception {
        assertFalse(check("iolin II", 35, -1));
    }

    @Test
    public void ordinaryNameRemains() throws Exception {
        assertFalse(check("Olin U", 35, 300));
    }

    @Test
    public void correctHeadingUnlocksComposer() {
        assertEquals("Morgan Vale", read().composer());
    }

    @Test
    public void arrangementRolePreserved() {
        assertEquals("Tessa Brook", read().arranger());
    }
}
