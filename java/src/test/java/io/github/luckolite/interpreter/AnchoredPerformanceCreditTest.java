// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/** Original tempo/instruction layouts, including a late detected staff. */
public class AnchoredPerformanceCreditTest {
    static ScoreCreditsDetector.Result read(
            String text, float x, float y, float staff, boolean tempo) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        lines.add(new ScoreCreditsDetector.Line("Harbor Lanterns", 500, 80, 1300, 150));
        if (tempo) lines.add(new ScoreCreditsDetector.Line("Moderately- 96", 220, 260, 480, 292));
        lines.add(new ScoreCreditsDetector.Line(text, x, y, x + 500, y + 25));
        return ScoreCreditsDetector.detect(
                "Harbor Lanterns",
                List.of(new ScoreCreditsDetector.Page(1800, 2400, staff, lines, false, false)),
                Set.of());
    }

    @Test
    public void introBelowTempoIsInstructionDespiteLateStaff() {
        assertTrue(read("Intro", 220, 310, 900, true).unclassifiedCredits().isEmpty());
    }

    @Test
    public void repeatedPlayOrderIsInstruction() {
        assertTrue(
                read("Solo - 2nd time, All - 3rd time", 220, 310, 900, true)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void ItalianPlayingInstructionIsNotCredit() {
        assertTrue(
                read("Si deve suonare dolcemente", 220, 310, 900, true)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void expressionAtStaffIsNotCredit() {
        assertTrue(
                read("M with gentle expression", 500, 370, 400, false)
                        .unclassifiedCredits()
                        .isEmpty());
    }

    @Test
    public void organIsInstrumentAtStaff() {
        assertTrue(read("Organ", 220, 370, 400, false).unclassifiedCredits().isEmpty());
    }

    @Test
    public void explicitPerformerProtected() {
        assertEquals("Intro", read("Performed by Intro", 220, 310, 900, true).artist());
    }

    @Test
    public void explicitComposerProtected() {
        assertEquals("Organ", read("Music by Organ", 220, 370, 400, true).composer());
    }

    @Test
    public void rightColumnNameProtected() {
        assertTrue(read("Intro", 1250, 310, 900, true).unclassifiedCredits().contains("Intro"));
    }

    @Test
    public void unanchoredNameProtected() {
        assertTrue(read("Intro", 220, 310, 900, false).unclassifiedCredits().contains("Intro"));
    }

    @Test
    public void distantTempoDoesNotClaimName() {
        assertTrue(read("Intro", 220, 410, 900, true).unclassifiedCredits().contains("Intro"));
    }
}
