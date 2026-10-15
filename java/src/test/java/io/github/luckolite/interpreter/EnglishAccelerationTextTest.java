// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.*;

public class EnglishAccelerationTextTest {
    @Test
    public void gradualDirectionRetainsExactEvidence() {
        String text = "mp gradually faster -";
        var d = ExpressiveDirectionText.parse(text);
        assertEquals(1, d.size());
        assertEquals(Kind.ACCELERANDO, d.get(0).kind());
        assertEquals(text, d.get(0).printedText());
        assertEquals(Strength.UNSPECIFIED, d.get(0).strength());
    }

    @Test
    public void bareFasterPreservesUnresolvedIntent() {
        var d = ExpressiveDirectionText.parse("faster -");
        assertEquals(1, d.size());
        assertEquals(Kind.UNRESOLVED_DIRECTION, d.get(0).kind());
    }

    @Test
    public void gradualWhitespaceAndCaseAreRetained() {
        var d = ExpressiveDirectionText.parse("Gradually   FASTER");
        assertEquals(1, d.size());
        assertEquals(Kind.ACCELERANDO, d.get(0).kind());
    }

    @Test
    public void unrelatedPhrasesAreNotTempoDirections() {
        for (String text :
                new String[] {
                    "run faster",
                    "faster-than-light",
                    "gradually fasterish",
                    "breakfast",
                    "faster 108"
                }) assertTrue(text, ExpressiveDirectionText.parse(text).isEmpty());
    }
}
