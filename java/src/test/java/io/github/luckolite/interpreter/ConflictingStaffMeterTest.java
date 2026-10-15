// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConflictingStaffMeterTest {
    static List<ScoreNoteEvent> bars(int beats, int count) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int bar = 0; bar < count; bar++)
            for (int beat = 0; beat < beats; beat++)
                notes.add(
                        new ScoreNoteEvent(
                                bar,
                                beat / (float) beats,
                                0,
                                0,
                                1,
                                .3f,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1));
        return notes;
    }

    static Set<ScoreMeterChange> choices() {
        return Set.of(new ScoreMeterChange(0, 4, 4), new ScoreMeterChange(0, 4, 1));
    }

    @Test
    public void twoWrittenBarsResolveConflictingStaffCopies() {
        assertEquals(
                new ScoreMeterChange(0, 4, 4),
                MeterChangeDetector.resolveConflict(choices(), bars(4, 2), 2));
    }

    @Test
    public void oneWrittenBarInsufficient() {
        assertNull(MeterChangeDetector.resolveConflict(choices(), bars(4, 1), 1));
    }

    @Test
    public void nextMeterBoundsEvidence() {
        assertNull(MeterChangeDetector.resolveConflict(choices(), bars(4, 3), 1));
    }

    @Test
    public void equalDurationMetersRemainAmbiguous() {
        assertNull(
                MeterChangeDetector.resolveConflict(
                        Set.of(new ScoreMeterChange(0, 3, 4), new ScoreMeterChange(0, 6, 8)),
                        bars(3, 3),
                        3));
    }

    @Test
    public void unsupportedAlternativesRemainAmbiguous() {
        assertNull(MeterChangeDetector.resolveConflict(choices(), bars(3, 3), 3));
    }

    @Test
    public void realWholeNoteMeterRetained() {
        assertEquals(
                new ScoreMeterChange(0, 4, 1),
                MeterChangeDetector.resolveConflict(choices(), bars(16, 2), 2));
    }
}
