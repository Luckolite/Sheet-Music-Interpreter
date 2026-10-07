// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original complete triplet phrases coexist with held, dotted and closing-quarter voices. */
public class CompleteCrossStaffTripletTest {
    private final List<ScoreNoteEvent> members = new ArrayList<>();
    private final List<ScoreNoteEvent> score = new ArrayList<>();
    private ScoreNoteEvent dottedTail, shortTail, quarter;

    private ScoreNoteEvent note(
            float x, int staff, int beams, int dots, float duration, int direction, boolean tied) {
        return new ScoreNoteEvent(
                0,
                x,
                staff == 0 ? 3 : 7,
                staff,
                2,
                .4f + staff * .12f,
                tied,
                dots,
                beams,
                0,
                duration,
                1,
                0,
                0,
                0,
                false,
                0,
                false,
                0,
                0,
                1,
                direction);
    }

    private void draw() {
        float[] x = {.03f, .10f, .17f, .24f, .31f, .38f, .45f, .52f, .59f, .70f, .78f, .86f};
        for (int i = 0; i < x.length; i++) {
            int staff = i < 8 ? 0 : 1;
            int direction = i < 3 || i >= 6 && i < 8 ? -1 : i < 9 ? 1 : 0;
            var n = note(x[i], staff, 1, 0, 0, direction, i == 9);
            if (i == 7 || i == 8) n = n.withCrossStaffBeam();
            members.add(n);
            score.add(n);
        }
        score.add(note(.03f, 1, 0, 0, 4, 0, false));
        score.add(note(.03f, 1, 0, 1, 2, 1, false));
        dottedTail = note(.70f, 1, 1, 1, 0, 0, false);
        shortTail = note(.95f, 1, 2, 0, 0, 0, false);
        quarter = note(.70f, 1, 0, 0, 1, 1, false);
        score.add(dottedTail);
        score.add(shortTail);
        score.add(quarter);
    }

    @Test
    public void continuedTripletsKeepTheirWrittenAttackClockAcrossStaves() {
        draw();
        for (int i = 0; i < members.size(); i++)
            assertEquals(
                    "triplet " + i,
                    i / 3.0,
                    ScoreNoteTiming.beatInMeasure(members.get(i), score, 4),
                    .0001);
    }

    @Test
    public void aCoincidentDottedPulseCannotLengthenATriplet() {
        draw();
        for (var n : members)
            assertEquals(1.0 / 3, ScoreNoteTiming.resolvedWrittenDurationBeats(n, score, 4), .0001);
    }

    @Test
    public void dottedPulseKeepsItsOwnOnsetAndDuration() {
        draw();
        assertEquals(3, ScoreNoteTiming.beatInMeasure(dottedTail, score, 4), .0001);
        assertEquals(
                .75, ScoreNoteTiming.resolvedWrittenDurationBeats(dottedTail, score, 4), .0001);
    }

    @Test
    public void closingShortPulseFollowsItsPrintedDottedPulse() {
        draw();
        assertEquals(3.75, ScoreNoteTiming.beatInMeasure(shortTail, score, 4), .0001);
        assertEquals(.25, ScoreNoteTiming.resolvedWrittenDurationBeats(shortTail, score, 4), .0001);
    }

    @Test
    public void closingQuarterVoiceStaysAtItsProvedColumn() {
        draw();
        assertEquals(3, ScoreNoteTiming.beatInMeasure(quarter, score, 4), .0001);
        assertEquals(1, ScoreNoteTiming.resolvedWrittenDurationBeats(quarter, score, 4), .0001);
    }

    @Test
    public void missingCrossStaffProofKeepsTheExistingFallback() {
        draw();
        score.remove(8);
        assertNull(ParallelTripletClock.find(members.get(0), score, 4));
    }

    @Test
    public void incompleteTripletPhraseCannotSupplyANewClock() {
        draw();
        score.remove(11);
        assertNull(ParallelTripletClock.find(members.get(0), score, 4));
    }

    @Test
    public void conflictingTupletRatioCannotSupplyANewClock() {
        draw();
        score.set(4, score.get(4).withTupletRatio(5, 4));
        assertNull(ParallelTripletClock.find(members.get(0), score, 4));
    }

    @Test
    public void stemSwitchInsideOnePrintedGroupRemainsUnresolved() {
        draw();
        score.set(1, score.get(1).withStemDirection(1));
        assertNull(ParallelTripletClock.find(members.get(0), score, 4));
    }

    @Test
    public void unknownOpeningGroupsCannotBeInventedFromSpacing() {
        draw();
        for (int i = 0; i < 6; i++) score.set(i, score.get(i).withStemDirection(0));
        assertNull(ParallelTripletClock.find(members.get(0), score, 4));
    }

    @Test
    public void unknownTerminalShaftsNeedAnIndependentClosingQuarter() {
        draw();
        score.remove(quarter);
        assertNull(ParallelTripletClock.find(members.get(0), score, 4));
    }

    @Test
    public void oppositeUnknownHeadsAtOneColumnAreNotAProvedChord() {
        draw();
        score.add(note(.70f, 1, 1, 0, 0, 1, false));
        assertNull(ParallelTripletClock.find(members.get(0), score, 4));
    }

    @Test
    public void anExtraShortPulseBeforeItsAnchorCannotBeMovedToTheBarEnd() {
        draw();
        score.add(note(.35f, 1, 2, 0, 0, 0, false));
        assertNull(ParallelTripletClock.find(members.get(0), score, 4));
    }

    @Test
    public void incompatibleClosingQuarterCannotSupplyUnknownStemOwnership() {
        draw();
        score.remove(quarter);
        score.add(note(.70f, 1, 0, 0, 1.5f, 1, false));
        assertNull(ParallelTripletClock.find(members.get(0), score, 4));
    }

    @Test
    public void writtenTiedContinuationKeepsItsTripletSlot() {
        draw();
        assertTrue(members.get(9).tiedFromPrevious());
        assertEquals(3, ScoreNoteTiming.beatInMeasure(members.get(9), score, 4), .0001);
        assertEquals(
                1.0 / 3,
                ScoreNoteTiming.resolvedWrittenDurationBeats(members.get(9), score, 4),
                .0001);
    }

    @Test
    public void clockDoesNotChangeEventsOrInputOrder() {
        draw();
        var original = List.copyOf(score);
        for (var n : score) ScoreNoteTiming.beatInMeasure(n, score, 4);
        assertEquals(original, score);
    }

    @Test
    public void sixBeatOrdinaryEighthsAreNotReinterpretedAsTriplets() {
        draw();
        assertNull(ParallelTripletClock.find(members.get(0), score, 6));
    }
}
