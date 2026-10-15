// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScorePlaybackDirection.Kind.*;

public final class CodaInstructionSymbolTest {
    private List<ScorePlaybackDirection> detect(String text, float x, float top, float bottom) {
        return ScoreNavigationDetector.detect(
                List.of(new PlayingTechniqueDetector.Word(text, .55f, .18f, .65f, .22f)),
                List.of(new ScoreNavigationDetector.Glyph(CODA, x, top, bottom)),
                List.of(new PlayingTechniqueDetector.Staff(100, 140, 10, 0, 1)),
                List.of(
                        new MeasureRegion(.1f, .5f, .2f, .4f),
                        new MeasureRegion(.51f, .9f, .2f, .4f)),
                1000,
                400);
    }

    @Test
    public void followingCodaGlyphCompletesOutgoingInstruction() {
        for (String text : List.of("To Coda", "D.S. al Coda", "D.C. al Coda")) {
            var marks = detect(text, .663f, .18f, .22f);
            assertEquals(1, marks.size());
            assertTrue(marks.get(0).kind().outgoing());
        }
    }

    @Test
    public void precedingIndependentDestinationSurvives() {
        assertEquals(2, detect("To Coda", .52f, .18f, .22f).size());
    }

    @Test
    public void separateLineDestinationSurvives() {
        assertEquals(2, detect("To Coda", .663f, .09f, .13f).size());
    }

    @Test
    public void separatedDestinationSurvives() {
        assertEquals(2, detect("To Coda", .8f, .18f, .22f).size());
    }
}
