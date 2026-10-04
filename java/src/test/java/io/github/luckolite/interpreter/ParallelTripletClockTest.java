// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParallelTripletClockTest {
    private List<ScoreNoteEvent> exercise(boolean bridges) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 12; i++) {
            int staff = i < 4 || i == 6 || i == 9 ? 1 : 0;
            var n =
                    new ScoreNoteEvent(
                                    0,
                                    .04f + .08f * i,
                                    i % 5,
                                    staff,
                                    2,
                                    .4f + staff * .2f,
                                    false,
                                    0,
                                    1,
                                    2,
                                    0,
                                    1,
                                    i == 5 ? 1 : 0,
                                    0,
                                    staff == 1 ? 18 : 30)
                            .withStemDirection(staff == 1 ? 1 : -1);
            if (bridges && (i == 3 || i == 4 || i == 6 || i == 7)) n = n.withCrossStaffBeam();
            notes.add(n);
        }
        notes.add(new ScoreNoteEvent(0, .76f, 5, 0, 2, .4f, false, 1, 1).withStemDirection(1));
        notes.add(new ScoreNoteEvent(0, .97f, 5, 0, 2, .4f, false, 0, 2).withStemDirection(1));
        return notes;
    }

    @Test
    public void bridgedAccompanimentAndOrdinaryMelodyKeepSeparateClocks() {
        var n = exercise(true);
        for (int i = 0; i < 12; i++) {
            assertEquals(i / 3.0, ScoreNoteTiming.beatInMeasure(n.get(i), n, 4), .000001);
            assertEquals(
                    1 / 3.0, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(i), n, 4), .000001);
        }
        assertEquals(3, ScoreNoteTiming.beatInMeasure(n.get(12), n, 4), .000001);
        assertEquals(.75, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(12), n, 4), 0);
        assertEquals(3.75, ScoreNoteTiming.beatInMeasure(n.get(13), n, 4), .000001);
        assertEquals(.25, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(13), n, 4), 0);
        assertEquals(1, n.get(5).followingRestBeats(), 0);
    }

    @Test
    public void physicalBridgesAndKnownShaftsAreRequired() {
        var unproved = exercise(false);
        assertNull(ParallelTripletClock.find(unproved.get(0), unproved, 4));
        var unknown = exercise(true);
        unknown.set(3, unknown.get(3).withStemDirection(0));
        assertNull(ParallelTripletClock.find(unknown.get(0), unknown, 4));
    }

    @Test
    public void incompleteLaneAndNonbinaryTupletsKeepExistingSemantics() {
        var incomplete = exercise(true);
        incomplete.remove(11);
        assertNull(ParallelTripletClock.find(incomplete.get(0), incomplete, 4));
        var quintuplet = exercise(true);
        quintuplet.set(10, quintuplet.get(10).withTupletRatio(5, 3));
        assertNull(ParallelTripletClock.find(quintuplet.get(0), quintuplet, 4));
        assertEquals(.3, ScoreNoteTiming.writtenDurationBeats(quintuplet.get(10)), .000001);
    }

    @Test
    public void anExistingExplicitTripletIsNotScaledTwice() {
        var n = exercise(true);
        n.set(10, n.get(10).withTupletRatio(3, 2));
        assertEquals(
                1 / 3.0, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(10), n, 4), .000001);
        assertEquals(10 / 3.0, ScoreNoteTiming.beatInMeasure(n.get(10), n, 4), .000001);
    }

    @Test
    public void independentBassQuartersAndHalfRemainWritten() {
        var n = exercise(true);
        var quarter =
                new ScoreNoteEvent(0, .28f, -2, 1, 2, .6f, false, 0, 0, 2, 1).withStemDirection(-1);
        var half =
                new ScoreNoteEvent(0, .52f, -4, 1, 2, .6f, false, 0, 0, 2, 2).withStemDirection(-1);
        n.add(quarter);
        n.add(half);
        assertEquals(1, ScoreNoteTiming.beatInMeasure(quarter, n, 4), .000001);
        assertEquals(1, ScoreNoteTiming.resolvedWrittenDurationBeats(quarter, n, 4), 0);
        assertEquals(2, ScoreNoteTiming.beatInMeasure(half, n, 4), .000001);
        assertEquals(2, ScoreNoteTiming.resolvedWrittenDurationBeats(half, n, 4), 0);
    }

    @Test
    public void reversedInputRetainsTheVoiceClocks() {
        var n = exercise(true);
        var target = n.get(11);
        var melody = n.get(13);
        Collections.reverse(n);
        assertEquals(11 / 3.0, ScoreNoteTiming.beatInMeasure(target, n, 4), .000001);
        assertEquals(3.75, ScoreNoteTiming.beatInMeasure(melody, n, 4), .000001);
    }

    @Test
    public void sameDirectionQuarterPulseMakesTheCandidateAmbiguous() {
        var n = exercise(true);
        n.add(new ScoreNoteEvent(0, .2f, -3, 1, 2, .6f, false, 0, 0, 2, 1).withStemDirection(1));
        assertNull(ParallelTripletClock.find(n.get(0), n, 4));
    }
}
