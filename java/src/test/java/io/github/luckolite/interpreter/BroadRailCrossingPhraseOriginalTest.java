// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

/** Original wider engraved staff rails, with the same complete ribbon and fixed head owners. */
public class BroadRailCrossingPhraseOriginalTest {
    static CompleteRailCrossingPhraseOriginalTest.Scene scene(float phase) {
        var s = new CompleteRailCrossingPhraseOriginalTest.Scene(phase);
        for (int line = 0; line < 5; line++)
            for (int y = (int) (s.bottom - line * s.gap) - 3;
                    y <= (int) (s.bottom - line * s.gap) + 3;
                    y++) {
                int covered = 0;
                for (int sy = 0; sy < 8; sy++)
                    if (Math.abs(y + (sy + .5) / 8 - .5 - (s.bottom - line * s.gap)) <= 1.25)
                        covered++;
                int shade = (int) Math.round(255 * (1 - covered / 8.));
                for (int x = 0; x < s.w; x++)
                    s.gray[y * s.w + x] = (byte) Math.min(s.gray[y * s.w + x] & 255, shade);
            }
        return s;
    }

    @Test
    public void broaderActualRailCannotSplitTheCompleteBow() {
        scene(0).positive();
    }

    @Test
    public void broaderSubpixelRailCannotSplitTheCompleteBow() {
        scene(.5f).positive();
    }

    @Test
    public void absenceOfRawCrossingInkCannotBeReplacedByTheFrame() {
        var s = scene(0);
        for (int y = 158; y <= 182; y++)
            for (int x = 178; x <= 190; x++) s.gray[y * s.w + x] = (byte) 255;
        assertTrue(s.capture().spans().isEmpty());
    }
}
