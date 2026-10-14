// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuardedSectionLabelTest {
    static boolean section(String text, String companion, float x, boolean photo) throws Exception {
        var h = new ScoreCreditsDetector.Line("Evening Song", 500, 100, 1300, 220);
        var a = new ScoreCreditsDetector.Line(text, 150, 350, 370, 400);
        var b = new ScoreCreditsDetector.Line(companion, x, 600, x + 200, 650);
        var lines = List.of(h, a, b);
        var p = new ScoreCreditsDetector.Page(1800, 2400, 1500, lines, false, photo);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "pairedSectionLabel",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, h, a, lines);
    }

    @Test
    public void shortPersonalNameRetained() throws Exception {
        assertFalse(section("CHAD", "VERSE", 150, false));
    }

    @Test
    public void exactPair() throws Exception {
        assertTrue(section("INTRO", "VERSE", 150, false));
    }

    @Test
    public void damagedIntro() throws Exception {
        assertTrue(section("TTRO", "VERSE", 150, false));
    }

    @Test
    public void damagedSolo() throws Exception {
        assertTrue(section("INTRDSDLC", "HEAD", 150, false));
    }

    @Test
    public void damagedNumericSolo() throws Exception {
        assertTrue(section("INT30 SDLD", "HEAD|", 150, false));
    }

    @Test
    public void exactWithDamagedCompanion() throws Exception {
        assertTrue(section("VERSE", "TTRO", 150, false));
    }

    @Test
    public void bothDamagedNotEnough() throws Exception {
        assertFalse(section("TTRO", "VERSF", 150, false));
    }

    @Test
    public void independentNameRetained() throws Exception {
        assertFalse(section("MORGAN VALE", "VERSE", 150, false));
    }

    @Test
    public void distantColumnRetained() throws Exception {
        assertFalse(section("INTRO", "VERSE", 900, false));
    }

    @Test
    public void photographicTextRetained() throws Exception {
        assertFalse(section("INTRO", "VERSE", 150, true));
    }
}
