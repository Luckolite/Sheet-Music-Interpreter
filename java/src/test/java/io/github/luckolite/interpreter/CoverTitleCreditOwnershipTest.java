// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original cover typography and independent contributor controls. */
public class CoverTitleCreditOwnershipTest {
    static ScoreCreditsDetector.Result read(Set<String> known, String extra) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line("Exploring", 100, 200, 1400, 520));
        lines.add(new ScoreCreditsDetector.Line("The", 250, 530, 760, 800));
        lines.add(new ScoreCreditsDetector.Line("Registers", 420, 840, 1640, 1110));
        if (!extra.isEmpty()) lines.add(new ScoreCreditsDetector.Line(extra, 1450, 700, 1780, 745));
        return ScoreCreditsDetector.detect(
                "Exploring The Registers Book",
                List.of(new ScoreCreditsDetector.Page(1800, 2500, -1, lines, false, false)),
                known);
    }

    @Test
    public void coverFragmentNotContributor() {
        var r = read(Set.of(), "");
        assertEquals("Exploring The Registers", r.title());
        assertFalse(r.unclassifiedCredits().contains("Registers"));
    }

    @Test
    public void knownNameStillReviewable() {
        assertTrue(read(Set.of("Registers"), "").unclassifiedCredits().contains("Registers"));
    }

    @Test
    public void independentNameStillReviewable() {
        assertTrue(read(Set.of(), "Morgan Vale").unclassifiedCredits().contains("Morgan Vale"));
    }

    @Test
    public void explicitComposerPreserved() {
        assertEquals("Morgan Vale", read(Set.of(), "Music by Morgan Vale").composer());
    }

    @Test
    public void explicitArrangerPreserved() {
        assertEquals("Morgan Vale", read(Set.of(), "Arranged by Morgan Vale").arranger());
    }

    @Test
    public void explicitArtistPreserved() {
        assertEquals("Morgan Vale", read(Set.of(), "Performed by Morgan Vale").artist());
    }
}
