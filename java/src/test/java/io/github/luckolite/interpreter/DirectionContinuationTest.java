// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectionContinuationTest {
    static boolean direction(String text, String prior, float x, float y, boolean photo)
            throws Exception {
        var a = new ScoreCreditsDetector.Line(text, 375, 490, 510, 520);
        var b = new ScoreCreditsDetector.Line(prior, x, y, x + 200, y + 30);
        var lines = List.of(a, b);
        var p = new ScoreCreditsDetector.Page(1800, 2400, 540, lines, false, photo);
        var m =
                ScoreCreditsDetector.class.getDeclaredMethod(
                        "damagedDirectionContinuation",
                        ScoreCreditsDetector.Page.class,
                        ScoreCreditsDetector.Line.class,
                        List.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, p, a, lines);
    }

    @Test
    public void damagedExpressionUnderTempo() throws Exception {
        assertTrue(direction("y laarimoso", "Largo poco rubato", 340, 455, false));
    }

    @Test
    public void anotherDamagedExpression() throws Exception {
        assertTrue(direction("p esprezsivo", "Andante", 340, 455, false));
    }

    @Test
    public void properNameRetained() throws Exception {
        assertFalse(direction("Lacrimoso", "Andante", 340, 455, false));
    }

    @Test
    public void lowercaseNameRetained() throws Exception {
        assertFalse(direction("morgan vale", "Andante", 340, 455, false));
    }

    @Test
    public void uncorroboratedRetained() throws Exception {
        assertFalse(direction("laarimoso", "Morgan Vale", 340, 455, false));
    }

    @Test
    public void farAboveRetained() throws Exception {
        assertFalse(direction("laarimoso", "Andante", 340, 300, false));
    }

    @Test
    public void differentColumnRetained() throws Exception {
        assertFalse(direction("laarimoso", "Andante", 1000, 455, false));
    }

    @Test
    public void photographicTextRetained() throws Exception {
        assertFalse(direction("laarimoso", "Andante", 340, 455, true));
    }
}
