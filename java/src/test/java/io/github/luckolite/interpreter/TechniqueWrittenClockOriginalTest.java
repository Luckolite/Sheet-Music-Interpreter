// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original source-column clocks; no private score or Android/app dependencies. */
public final class TechniqueWrittenClockOriginalTest {
    static ScoreNoteEvent note(float p, int pitch, int staff, int count, float duration) {
        return new ScoreNoteEvent(0, p, pitch, staff, count, .4f, false, 0, 0, 0, duration, 1, 0);
    }

    static List<ScoreNoteEvent> notes() {
        return List.of(note(.12f, 0, 0, 1, 2), note(.82f, 2, 0, 1, 2));
    }

    static ScoreTechniqueChange change(float p) {
        return new ScoreTechniqueChange(0, p, 0, 1, 0);
    }

    static void onset(float p, double expected) {
        assertEquals(
                expected,
                ScoreTechniqueClock.onset(change(p), notes(), Map.of(), 4).orElseThrow(),
                0);
    }

    @Test
    public void exactColumnUsesWrittenOnset() {
        onset(.82f, 2);
    }

    @Test
    public void unsnappedLabelBeforeFirstColumnUsesItsFirstAttack() {
        onset(.03f, 0);
    }

    @Test
    public void betweenColumnsUsesExistingActivationLaw() {
        onset(.6f, 2);
    }

    @Test
    public void toleranceChoosesAColumnWithoutTurningToleranceIntoBeats() {
        onset(.8205f, 2);
    }

    @Test
    public void otherStaffCannotSupplyTheInstructionClock() {
        var ns = List.of(note(.02f, 0, 1, 2, 4), note(.12f, 0, 0, 2, 2), note(.82f, 2, 0, 2, 2));
        var c = new ScoreTechniqueChange(0, .03f, 0, 2, 0);
        assertEquals(0, ScoreTechniqueClock.onset(c, ns, Map.of(), 4).orElseThrow(), 0);
    }

    @Test
    public void retainedWrittenClockTakesPrecedence() {
        var p =
                Map.of(
                        0, new ScoreNoteTiming.WrittenPlacement(0, 1, -1, -1, false, 0),
                        1, new ScoreNoteTiming.WrittenPlacement(1, 3, -1, -1, false, 0));
        assertEquals(1, ScoreTechniqueClock.onset(change(.6f), notes(), p, 4).orElseThrow(), 0);
    }

    @Test
    public void conflictingOwnersAtOneColumnDoNotAcquireAnOnset() {
        var ns = List.of(note(.12f, 0, 0, 1, 4), note(.12f, 2, 0, 1, 4));
        var p =
                Map.of(
                        0, new ScoreNoteTiming.WrittenPlacement(0, 4, -1, -1, false, 0),
                        1, new ScoreNoteTiming.WrittenPlacement(1, 3, -1, -1, false, 0));
        assertTrue(ScoreTechniqueClock.onset(change(.1f), ns, p, 4).isEmpty());
    }

    @Test
    public void mixedTopologyAtTheFirstColumnAbstains() {
        var ns = List.of(note(.12f, 0, 0, 1, 4), note(.12f, 2, 0, 2, 4));
        assertTrue(ScoreTechniqueClock.onset(change(.1f), ns, Map.of(), 4).isEmpty());
    }

    @Test
    public void noAffectedColumnAndAttackLocalBowsRemainOutsideThisClock() {
        assertTrue(ScoreTechniqueClock.onset(change(.99f), notes(), Map.of(), 4).isEmpty());
        var bow = new ScoreTechniqueChange(0, .12f, 0, 1, ScoreTechniqueChange.UP_BOW);
        assertTrue(ScoreTechniqueClock.onset(bow, notes(), Map.of(), 4).isEmpty());
    }

    @Test
    public void printedLeadingSilenceRemainsBeforeTheFirstAffectedAttack() {
        var ns = List.of(note(.12f, 0, 0, 1, 1).withLeadingRest(1), note(.82f, 2, 0, 1, 2));
        assertEquals(1, ScoreTechniqueClock.onset(change(.03f), ns, Map.of(), 4).orElseThrow(), 0);
    }
}
