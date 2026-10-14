// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TitleFragmentOwnershipTest {
    static boolean stack(String title, float staff, boolean credits) throws Exception {
        var a = new ScoreCreditsDetector.Line("LET", 900, 20, 1740, 350);
        var b = new ScoreCreditsDetector.Line("MUSIC", 900, 375, 1740, 580);
        var c = new ScoreCreditsDetector.Line("RISE", 900, 760, 1740, 960);
        var p = new ScoreCreditsDetector.Page(1800, 1013, staff, List.of(a, b, c), credits, false);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "stackedCoverTitleFragment",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, b, title);
    }

    static boolean medley(String title, String text, float x, float y) throws Exception {
        var l = new ScoreCreditsDetector.Line(text, x, y, x + 450, y + 22);
        var p = new ScoreCreditsDetector.Page(1800, 2500, 450, List.of(l), false, false);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "repeatedMedleyWorkLabel",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, l, title);
    }

    @Test
    public void completeStackOwnsWord() throws Exception {
        assertTrue(stack("Let Music Rise", -1, false));
    }

    @Test
    public void attributedTitleSuffix() throws Exception {
        assertTrue(stack("Let Music Rise - Morgan Vale", -1, false));
    }

    @Test
    public void differentTitleRetainsWord() throws Exception {
        assertFalse(stack("Let Music Fall", -1, false));
    }

    @Test
    public void notationPageRetainsWord() throws Exception {
        assertFalse(stack("Let Music Rise", 400, false));
    }

    @Test
    public void creditPageRetainsWord() throws Exception {
        assertFalse(stack("Let Music Rise", -1, true));
    }

    @Test
    public void repeatedMedleyTitle() throws Exception {
        assertTrue(medley("SILVER LAKE MEDLEY", "THE SILVER LAKE", 190, 360));
    }

    @Test
    public void unrelatedWorkRetained() throws Exception {
        assertFalse(medley("SILVER LAKE MEDLEY", "THE GOLDEN RIVER", 190, 360));
    }

    @Test
    public void noMedleyRetained() throws Exception {
        assertFalse(medley("THE SILVER LAKE", "THE SILVER LAKE", 190, 360));
    }

    @Test
    public void rightBylineRetained() throws Exception {
        assertFalse(medley("SILVER LAKE MEDLEY", "THE SILVER LAKE", 1300, 360));
    }

    @Test
    public void farFromStaffRetained() throws Exception {
        assertFalse(medley("SILVER LAKE MEDLEY", "THE SILVER LAKE", 190, 200));
    }
}
