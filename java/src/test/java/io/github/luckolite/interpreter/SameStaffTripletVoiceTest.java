// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SameStaffTripletVoiceTest {
    private List<ScoreNoteEvent> voices(int staffCount, int staff) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 12; i++)
            notes.add(
                    new ScoreNoteEvent(
                                    0,
                                    .045f + i * .081f,
                                    i % 4 - 3,
                                    staff,
                                    staffCount,
                                    .4f,
                                    false,
                                    0,
                                    1)
                            .withStemDirection(-1));
        notes.add(
                new ScoreNoteEvent(0, .045f, 6, staff, staffCount, .35f, false, 1, 0, 2, 2)
                        .withStemDirection(1));
        notes.add(
                new ScoreNoteEvent(
                                0, .045f + 9 * .081f, 4, staff, staffCount, .36f, false, 0, 0, 2, 1)
                        .withStemDirection(1));
        return notes;
    }

    @Test
    public void printedOpposingVoicesKeepTwelveTripletsAndDottedHalfQuarter() {
        var n = voices(2, 0);
        for (int i = 0; i < 12; i++) {
            assertEquals(i / 3.0, ScoreNoteTiming.beatInMeasure(n.get(i), n, 4), .000001);
            assertEquals(
                    1 / 3.0, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(i), n, 4), .000001);
        }
        assertEquals(0, ScoreNoteTiming.beatInMeasure(n.get(12), n, 4), 0);
        assertEquals(3, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(12), n, 4), 0);
        assertEquals(3, ScoreNoteTiming.beatInMeasure(n.get(13), n, 4), .000001);
        assertEquals(1, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(13), n, 4), 0);
    }

    @Test
    public void bassQuarterChordsAndTiesRetainTheirIndependentValues() {
        var n = voices(2, 0);
        for (int i = 0; i < 4; i++)
            for (int pitch : new int[] {-3, 4})
                n.add(
                        new ScoreNoteEvent(
                                        0,
                                        .045f + i * 3 * .081f,
                                        pitch,
                                        1,
                                        2,
                                        .65f,
                                        i == 0,
                                        0,
                                        0,
                                        2,
                                        1)
                                .withStemDirection(1));
        for (int i = 14; i < n.size(); i++) {
            assertEquals((i - 14) / 2, ScoreNoteTiming.beatInMeasure(n.get(i), n, 4), .000001);
            assertEquals(1, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(i), n, 4), 0);
        }
        assertTrue(n.get(14).tiedFromPrevious());
    }

    @Test
    public void theVoiceCanBelongToAnEnsembleStaffAndInputOrderDoesNotMatter() {
        var n = voices(4, 2);
        var last = n.get(11);
        Collections.reverse(n);
        assertNotNull(ParallelTripletClock.find(last, n, 4));
        assertEquals(11 / 3.0, ScoreNoteTiming.beatInMeasure(last, n, 4), .000001);
    }

    @Test
    public void unknownShaftAndMixedSameDirectionQuarterAreNotOwnedByTheLane() {
        var n = voices(2, 0);
        n.set(2, n.get(2).withStemDirection(0));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
        n = voices(2, 0);
        n.add(new ScoreNoteEvent(0, .2f, -4, 0, 2, .4f, false, 0, 0, 2, 1).withStemDirection(-1));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
    }

    @Test
    public void anIncompleteOrContradictoryMelodyDoesNotProveTheMeter() {
        var n = voices(2, 0);
        n.remove(13);
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
        n = voices(2, 0);
        n.set(
                13,
                new ScoreNoteEvent(0, .045f + 6 * .081f, 4, 0, 2, .36f, false, 0, 0, 2, 1)
                        .withStemDirection(1));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
    }

    @Test
    public void anInterruptedLaneAndFiveToThreeTupletKeepWrittenSemantics() {
        var n = voices(2, 0);
        n.set(3, n.get(3).withLeadingRest(.5f));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
        n = voices(2, 0);
        n.set(3, n.get(3).withTupletRatio(5, 3));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
        assertEquals(.3, ScoreNoteTiming.writtenDurationBeats(n.get(3)), .000001);
    }

    @Test
    public void anAlreadyMarkedTripletIsNotScaledTwice() {
        var n = voices(2, 0);
        n.set(4, n.get(4).withTupletRatio(3, 2));
        assertEquals(
                1 / 3.0, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(4), n, 4), .000001);
    }

    @Test
    public void anIndependentEnsemblePartCannotBorrowThePianoClock() {
        var n = voices(4, 0);
        var violin =
                new ScoreNoteEvent(0, .045f + 4 * .081f, 3, 3, 4, .8f, false, 0, 1)
                        .withStemDirection(1)
                        .withTupletRatio(3, 2);
        n.add(violin);
        assertNotNull(ParallelTripletClock.find(n.get(0), n, 4));
        assertNull(ParallelTripletClock.find(violin, n, 4));
        assertEquals(1 / 3.0, ScoreNoteTiming.resolvedWrittenDurationBeats(violin, n, 4), .000001);
    }

    @Test
    public void ordinaryEightEighthNotesKeepTheStraightClock() {
        var n = voices(2, 0);
        n.subList(8, 12).clear();
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
        assertEquals(.5, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(1), n, 4), 0);
    }
}
