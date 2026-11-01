// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original canonical clocks, independent of the retained printed metronome-note unit. */
public class QuarterTempoEngravingOriginalTest {
    private static List<ScoreTempoChange> printed() {
        return List.of(new ScoreTempoChange(0, 0, 96, .75), new ScoreTempoChange(2, 0, 48, .75));
    }

    @Test
    public void alreadyQuarterClockDoesNotApplyDottedEighthUnitAgain() {
        assertEquals(printed(), ScoreTempoSelection.forEngravingQuarterBpm(96, 1, printed()));
    }

    @Test
    public void convertedCompoundMeterClockDoesNotApplyPrintedUnitAgain() {
        assertEquals(
                printed(), ScoreTempoSelection.forEngravingQuarterBpm(64 * 1.5, 1.5, printed()));
    }

    @Test
    public void selectedQuarterClockScalesLaterMarksAndRetainsPrintedUnits() {
        assertEquals(
                List.of(new ScoreTempoChange(0, 0, 120, .75), new ScoreTempoChange(2, 0, 60, .75)),
                ScoreTempoSelection.forEngravingQuarterBpm(120, 1, printed()));
    }

    @Test
    public void noOpeningMarkUsesFallbackUnitAndKeepsLaterAbsoluteTempo() {
        var later = new ScoreTempoChange(2, 0, 90, .5);
        assertEquals(
                List.of(new ScoreTempoChange(0, 0, 120, 1.5), later),
                ScoreTempoSelection.forEngravingQuarterBpm(120, 1.5, List.of(later)));
    }

    @Test
    public void unsetQuarterClockLeavesPrintedValues() {
        assertEquals(printed(), ScoreTempoSelection.forEngravingQuarterBpm(0, 1, printed()));
    }

    @Test
    public void explicitPrintedPulseApiStillConvertsEighthPulse() {
        var marks = List.of(new ScoreTempoChange(0, 0, 81.5, .5));
        assertEquals(81.5, ScoreTempoSelection.selectedQuarterBpm(163, 163, marks), 0);
        assertEquals(marks, ScoreTempoSelection.forEngraving(163, 163, 1, marks));
    }
}
