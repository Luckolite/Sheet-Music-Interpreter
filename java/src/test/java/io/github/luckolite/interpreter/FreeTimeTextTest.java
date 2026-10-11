// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class FreeTimeTextTest {
    @Test
    public void exactMusicalPhraseRetainsTextWithoutInventingTempo() {
        for (String text : new String[] {"Free time", "free   time", "FREE TIME"}) {
            var events = ExpressiveDirectionText.parse(text);
            assertEquals(1, events.size());
            assertEquals(ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION, events.get(0).kind());
            assertEquals(text, events.get(0).printedText());
        }
        for (String text :
                new String[] {"free times", "carefree time", "freely", "copyright free timepiece"})
            assertTrue(ExpressiveDirectionText.parse(text).isEmpty());
    }
}
