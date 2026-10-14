// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original accompaniment collection layouts, no source score fixtures. */
public class AccompanimentTuneListTest {
    static ScoreCreditsDetector.Result read(
            String noun, boolean audio, int count, float staff, boolean left) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line("Harbor Collection", 400, 100, 1700, 280));
        lines.add(
                new ScoreCreditsDetector.Line(
                        "Play 8 Fiddle " + noun + (audio ? " with Sound-alike Audio Tracks" : ""),
                        700,
                        420,
                        1500,
                        470));
        lines.add(new ScoreCreditsDetector.Line("Music by Morgan Vale", 650, 300, 1500, 340));
        for (int i = 0; i < count; i++) {
            float w = 190 + i * 13;
            float x = left ? 1300 : 1700 - w;
            lines.add(
                    new ScoreCreditsDetector.Line(
                            "Harbor Song " + (char) ('A' + i),
                            x,
                            550 + i * 85,
                            x + w,
                            585 + i * 85));
        }
        return ScoreCreditsDetector.detect(
                "Harbor Collection",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, staff, lines, false, false)),
                Set.of());
    }

    @Test
    public void tunesListRightAligned() {
        assertFalse(
                read("Tunes", true, 8, -1, false).unclassifiedCredits().contains("Harbor Song A"));
    }

    @Test
    public void tunesListLeftAligned() {
        assertFalse(
                read("Tunes", true, 8, -1, true).unclassifiedCredits().contains("Harbor Song A"));
    }

    @Test
    public void songsList() {
        assertFalse(
                read("Songs", true, 8, -1, false).unclassifiedCredits().contains("Harbor Song A"));
    }

    @Test
    public void piecesList() {
        assertFalse(
                read("Pieces", true, 8, -1, false).unclassifiedCredits().contains("Harbor Song A"));
    }

    @Test
    public void missingAudioKeepsAmbiguousReview() {
        assertTrue(
                read("Tunes", false, 8, -1, false).unclassifiedCredits().contains("Harbor Song A"));
    }

    @Test
    public void shortColumnKeepsAmbiguousReview() {
        assertTrue(
                read("Tunes", true, 3, -1, false).unclassifiedCredits().contains("Harbor Song A"));
    }

    @Test
    public void explicitComposerPreserved() {
        assertEquals("Morgan Vale", read("Tunes", true, 8, -1, false).composer());
    }

    @Test
    public void titlePreserved() {
        assertEquals("Harbor Collection", read("Tunes", true, 8, -1, false).title());
    }
}
