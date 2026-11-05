// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

public class IntensityCaptionTest {
    @Test
    public void retainsWholeCharacterCaptionWithoutInventingDynamics() {
        var directions = ExpressiveDirectionText.parse("with vigor, gaining intensity");
        assertEquals(1, directions.size());
        assertEquals(ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION, directions.get(0).kind());
        assertEquals("with vigor, gaining intensity", directions.get(0).printedText());
        assertTrue(ExpressiveDirectionText.parse("She played with vigor, gaining intensity").isEmpty());
        var ocr = new PlayingTechniqueDetector.Word("with vigor, gaining intensity",
                .1f, .2f, .3f, .23f);
        assertFalse(EnglishCharacterOcrContext.admitted(ocr));
    }
}
