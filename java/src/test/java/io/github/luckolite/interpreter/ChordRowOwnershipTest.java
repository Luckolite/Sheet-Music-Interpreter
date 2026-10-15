// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChordRowOwnershipTest {
    static byte[] ink() {
        byte[] g = new byte[200 * 300];
        Arrays.fill(g, (byte) 255);
        for (int y = 60; y < 84; y++)
            for (int x = 40; x < 56; x++)
                if (x < 43 || y < 63 || y >= 71 && y < 74) g[y * 200 + x] = 0;
        return g;
    }

    static final PlayingTechniqueDetector.Word WORD =
            new PlayingTechniqueDetector.Word("F", .2f, .2f, .28f, .28f);
    static final PlayingTechniqueDetector.Staff
            OLD = new PlayingTechniqueDetector.Staff(5, 45, 10, 0, 1),
            NEXT = new PlayingTechniqueDetector.Staff(100, 140, 10, 0, 1);

    @Test
    public void nextPrintedSystemRejectsChordDespiteWrongDynamicOwner() {
        var g = ink();
        assertFalse(ScoreDynamicsDetector.uprightChordF(WORD, OLD, g, 200, 300));
        assertTrue(ScoreDynamicsDetector.uprightChordF(WORD, List.of(OLD, NEXT), g, 200, 300));
    }

    @Test
    public void distantSystemDoesNotSupplyChordContext() {
        assertFalse(
                ScoreDynamicsDetector.uprightChordF(
                        WORD,
                        List.of(OLD, new PlayingTechniqueDetector.Staff(200, 240, 10, 0, 1)),
                        ink(),
                        200,
                        300));
    }

    @Test
    public void noNextSystemDoesNotInventOne() {
        assertFalse(ScoreDynamicsDetector.uprightChordF(WORD, List.of(OLD), ink(), 200, 300));
    }

    @Test
    public void sourcePixelsUnchanged() {
        var g = ink();
        var copy = g.clone();
        ScoreDynamicsDetector.uprightChordF(WORD, List.of(OLD, NEXT), g, 200, 300);
        assertArrayEquals(copy, g);
    }
}
