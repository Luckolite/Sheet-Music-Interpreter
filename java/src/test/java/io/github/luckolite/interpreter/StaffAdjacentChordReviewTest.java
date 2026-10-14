// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StaffAdjacentChordReviewTest {
    ScoreCreditsDetector.Line title =
            new ScoreCreditsDetector.Line("Morning Lantern", 300, 60, 700, 110);

    boolean test(String text, float left, float top, float bottom, float staff) throws Exception {
        var line = new ScoreCreditsDetector.Line(text, left, top, left + 40, bottom);
        var lines = List.of(title, line);
        var page = new ScoreCreditsDetector.Page(1000, 1400, staff, lines);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "alignedChordReviewFragment",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, page, line, lines);
    }

    @Test
    public void compactMajorFragment() throws Exception {
        assertTrue(test("Gma", 200, 240, 270, 290));
    }

    @Test
    public void compactMinorFragment() throws Exception {
        assertTrue(test("Cmi7", 200, 240, 270, 290));
    }

    @Test
    public void remoteCreditKept() throws Exception {
        assertFalse(test("Gma", 200, 150, 180, 290));
    }

    @Test
    public void rightBylineKept() throws Exception {
        assertFalse(test("Gma", 700, 240, 270, 290));
    }

    @Test
    public void explicitCreditKept() throws Exception {
        assertFalse(test("Music by Gma", 200, 240, 270, 290));
    }

    @Test
    public void ordinaryNameKept() throws Exception {
        assertFalse(test("Mira Bell", 200, 240, 270, 290));
    }

    @Test
    public void noStaffKept() throws Exception {
        assertFalse(test("Gma", 200, 240, 270, -1));
    }

    @Test
    public void beforeTitleKept() throws Exception {
        assertFalse(test("Gma", 200, 20, 50, 70));
    }

    @Test
    public void musicBelowStaffNotThisRule() throws Exception {
        assertFalse(test("Gma", 200, 300, 330, 290));
    }
}
