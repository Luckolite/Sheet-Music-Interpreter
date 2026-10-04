// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural clock; a printed beamed voice can reverse shaft direction per group. */
public class OffsetOpposingTupletClockTest {
    private static List<ScoreNoteEvent> original(boolean missingChordStem) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 12; i++) {
            int direction = i < 3 || i >= 9 ? 1 : -1;
            var n =
                    new ScoreNoteEvent(
                                    0,
                                    (i < 9
                                            ? .04f + .07f * i
                                            : new float[] {.74f, .81f, .93f}[i - 9]),
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
        notes.add(new ScoreNoteEvent(0, .70f, 8, 0, 2, .4f, false, 1, 1).withStemDirection(-1));
        notes.add(new ScoreNoteEvent(0, .97f, 8, 0, 2, .4f, false, 0, 2).withStemDirection(-1));
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

    @Test
    public void aMissingPrintedRatioCannotBeInferredFromTheCompleteHeadCount() {
        var notes = original(false);
        notes.set(7, notes.get(7).withTupletRatio(1, 1));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void anUnknownSoleShaftDoesNotProveAChangingBeamLane() {
        var notes = original(false);
        notes.set(7, notes.get(7).withStemDirection(0));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void opposingShaftsAtOneAttackCannotBeMergedIntoOneLane() {
        var notes = original(false);
        notes.set(14, notes.get(14).withStemDirection(-1));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void aShaftReversalInsideATripletGroupIsAmbiguous() {
        var notes = original(false);
        notes.set(7, notes.get(7).withStemDirection(1));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void aMelodyThatDoesNotFinishTheBarCannotProveTheClock() {
        var notes = original(false);
        notes.set(17, new ScoreNoteEvent(0, .97f, 8, 0, 2, .4f, false, 0, 1).withStemDirection(-1));
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 4));
    }

    @Test
    public void differentMeterCannotReuseTheFourBeatProof() {
        var notes = original(false);
        assertNull(ParallelTripletClock.find(notes.get(0), notes, 3));
    }
}
