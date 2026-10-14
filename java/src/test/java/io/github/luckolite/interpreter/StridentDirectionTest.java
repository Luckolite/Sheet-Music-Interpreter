// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StridentDirectionTest {
    static ScoreCreditsDetector.Result read(
            String heading, String line, float height, boolean photo) {
        var title = new ScoreCreditsDetector.Line(heading, 500, 90, 1300, 155);
        var direction = new ScoreCreditsDetector.Line(line, 649, 210, 800, 210 + height);
        return ScoreCreditsDetector.detect(
                heading,
                List.of(
                        new ScoreCreditsDetector.Page(
                                1800, 2500, 251, List.of(title, direction), false, photo)),
                Set.of());
    }

    @Test
    public void smallDirectionExcluded() {
        assertFalse(
                read("Lantern Waltz", "strident", 16, false)
                        .unclassifiedCredits()
                        .contains("strident"));
    }

    @Test
    public void titlePreserved() {
        assertEquals("Strident", read("Strident", "Music by Jordan Vale", 24, false).title());
    }

    @Test
    public void explicitComposerPreserved() {
        assertEquals("Strident", read("Lantern Waltz", "Music by Strident", 24, false).composer());
    }

    @Test
    public void unrelatedNamePreserved() {
        assertTrue(
                read("Lantern Waltz", "Jordan Vale", 16, false)
                        .unclassifiedCredits()
                        .contains("Jordan Vale"));
    }

    @Test
    public void mixedDirectionExcluded() {
        assertFalse(
                read("Lantern Waltz", "strident e agitato", 16, false)
                        .unclassifiedCredits()
                        .contains("strident e agitato"));
    }
}
