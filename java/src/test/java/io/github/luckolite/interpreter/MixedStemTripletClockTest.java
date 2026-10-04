// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural clock; a printed beamed voice can reverse shaft direction per group. */
public class MixedStemTripletClockTest {
    private static List<ScoreNoteEvent> original(boolean missingChordStem) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 12; i++) {
            int direction = i < 3 || i >= 9 ? 1 : -1;
            var n =
                    new ScoreNoteEvent(
                                    0,
                                    .04f + .07f * i,
                                    i % 5,
                                    0,
                                    2,
                                    .4f,
                                    false,
                                    0,
                                    1,
                                    ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                    0)
                            .withTupletRatio(3, 2)
                            .withStemDirection(direction);
            notes.add(n);
        }
        for (int i = 0; i < 3; i++)
            notes.add(
                    new ScoreNoteEvent(0, .04f + .07f * i, 8, 0, 2, .4f, i > 0, 0, 1)
                            .withTupletRatio(3, 2)
                            .withStemDirection(missingChordStem && i == 2 ? 0 : 1));
        notes.add(
                new ScoreNoteEvent(
                                0,
                                .25f,
                                8,
                                0,
                                2,
                                .4f,
                                true,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                2)
                        .withStemDirection(1));
        notes.add(new ScoreNoteEvent(0, .67f, 8, 0, 2, .4f, false, 1, 1).withStemDirection(-1));
        notes.add(new ScoreNoteEvent(0, .835f, 8, 0, 2, .4f, false, 0, 2).withStemDirection(-1));
        return notes;
    }

    @Test
    public void completePrintedTupletsRetainAClockAcrossStemChanges() {
        var notes = original(false);
        for (int i = 0; i < 12; i++)
            assertEquals(
                    "moving attack " + i,
                    i / 3.0,
                    ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4),
                    1e-6);
        assertEquals(1, ScoreNoteTiming.beatInMeasure(notes.get(15), notes, 4), 1e-6);
        assertEquals(3, ScoreNoteTiming.beatInMeasure(notes.get(16), notes, 4), 1e-6);
        assertEquals(3.75, ScoreNoteTiming.beatInMeasure(notes.get(17), notes, 4), 1e-6);
    }

    @Test
    public void sharedChordAttackDoesNotGainAQuarterClockFromOneUnknownShaft() {
        var notes = original(true);
        assertEquals(2 / 3.0, ScoreNoteTiming.beatInMeasure(notes.get(14), notes, 4), 1e-6);
        for (int i = 0; i < 12; i++)
            assertEquals(
                    "moving attack " + i,
                    i / 3.0,
                    ScoreNoteTiming.beatInMeasure(notes.get(i), notes, 4),
                    1e-6);
    }
}
