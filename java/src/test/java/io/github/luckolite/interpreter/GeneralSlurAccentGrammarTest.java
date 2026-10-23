// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeneralSlurAccentGrammarTest {
    @Test
    public void completeHigherCurvatureAtIndependentStaffScaleRetainsAccent() {
        var s = new SlurEndpointAccentOriginalTest.Scene();
        for (int x = 70; x <= 190; x++) {
            double t = (x - 70) / 120d;
            int y = (int) Math.round(75 + 40 * t - 18 * 4 * t * (1 - t));
            for (int d = -1; d <= 1; d++) s.gray[(y + d) * 320 + x] = 0;
        }
        s.accent();
        s.notes.set(0, new NoteArticulationDetector.Anchor(73, 95, 12, 0));
        s.notes.set(1, new NoteArticulationDetector.Anchor(193, 135, 12, 0));
        // This independently drawn bend18 exceeds the compact-dot1.4*12 geometry.
        assertTrue(18 > 1.4 * 12);
        assertEquals(1, s.result().size());
    }

    @Test
    public void sharpHingedConnectorCannotBorrowReturningSlurRole() {
        var s = new SlurEndpointAccentOriginalTest.Scene();
        s.line(70, 75, 130, 55);
        s.line(130, 55, 190, 115);
        s.accent();
        assertTrue(s.result().isEmpty());
    }
}
