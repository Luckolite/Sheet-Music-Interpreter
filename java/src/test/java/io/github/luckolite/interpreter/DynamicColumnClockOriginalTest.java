// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original exact-column dynamics, written timing and refusal contracts. */
public final class DynamicColumnClockOriginalTest {
    static ScoreNoteEvent note(float pos, int pitch, int staff, int count, float duration) {
        return new ScoreNoteEvent(0, pos, pitch, staff, count, .4f, false, 0, 0, 0, duration, 1, 0);
    }

    static List<ScoreNoteEvent> notes() {
        return List.of(note(.17f, 0, 0, 1, 3), note(.86f, 2, 0, 1, 1));
    }

    static ScoreDynamicChange change(float pos) {
        return new ScoreDynamicChange(0, pos, 0, 1, 0, pos, 6, 0);
    }

    static ScoreNoteTiming.WrittenPlacement clock(double beat, double duration) {
        return new ScoreNoteTiming.WrittenPlacement(beat, duration, -1, -1, false, 0);
    }

    @Test
    public void firstColumnUsesBeatZeroRatherThanPageInset() {
        assertEquals(
                0, ScoreDynamicClock.onset(change(.17f), notes(), Map.of(), 4).orElseThrow(), 0);
    }

    @Test
    public void laterColumnUsesWrittenBeatThreeRatherThanWidth() {
        assertEquals(
                3, ScoreDynamicClock.onset(change(.86f), notes(), Map.of(), 4).orElseThrow(), 0);
    }

    @Test
    public void retainedEditedClockControlsTheExactColumn() {
        assertEquals(
                2,
                ScoreDynamicClock.onset(
                                change(.86f), notes(), Map.of(0, clock(0, 2), 1, clock(2, 2)), 4)
                        .orElseThrow(),
                0);
    }

    @Test
    public void nearbyUnsnappedPositionDoesNotAcquireAClock() {
        assertTrue(ScoreDynamicClock.onset(change(.8601f), notes(), Map.of(), 4).isEmpty());
    }

    @Test
    public void unrelatedStaffCannotSupplyTheClock() {
        var ns = List.of(note(.17f, 0, 1, 2, 4));
        assertTrue(
                ScoreDynamicClock.onset(
                                new ScoreDynamicChange(0, .17f, 0, 2, 0, .17f, 0, 0),
                                ns,
                                Map.of(),
                                4)
                        .isEmpty());
    }

    @Test
    public void mixedTopologyAtExactColumnAbstains() {
        var ns = List.of(note(.17f, 0, 0, 1, 4), note(.17f, 2, 0, 2, 4));
        assertTrue(ScoreDynamicClock.onset(change(.17f), ns, Map.of(), 4).isEmpty());
    }

    @Test
    public void conflictingWrittenOwnersAbstain() {
        var ns = List.of(note(.17f, 0, 0, 1, 4), note(.17f, 2, 0, 1, 4));
        assertTrue(
                ScoreDynamicClock.onset(change(.17f), ns, Map.of(0, clock(0, 4), 1, clock(1, 3)), 4)
                        .isEmpty());
    }

    @Test
    public void sharedTimingRequiresEveryExactOwnerToAgree() {
        var ns = List.of(note(.17f, 0, 0, 2, 4), note(.17f, 2, 1, 2, 4));
        var c = new ScoreDynamicChange(0, .17f, 0, 2, 0, .17f, 0, 0, true);
        assertEquals(0, ScoreDynamicClock.onset(c, ns, Map.of(), 4).orElseThrow(), 0);
        assertTrue(
                ScoreDynamicClock.onset(c, ns, Map.of(0, clock(0, 4), 1, clock(1, 3)), 4)
                        .isEmpty());
    }

    @Test
    public void leadingSilenceRemainsBeforeTheOwnedAttack() {
        var ns = List.of(note(.17f, 0, 0, 1, 3).withLeadingRest(1));
        assertEquals(1, ScoreDynamicClock.onset(change(.17f), ns, Map.of(), 4).orElseThrow(), 0);
    }

    @Test
    public void hairpinAndInvalidMeterRemainOutsideAbsoluteColumnClock() {
        assertTrue(
                ScoreDynamicClock.onset(
                                new ScoreDynamicChange(0, .17f, 0, 1, 0, .86f, 6, 1),
                                notes(),
                                Map.of(),
                                4)
                        .isEmpty());
        assertTrue(ScoreDynamicClock.onset(change(.17f), notes(), Map.of(), Float.NaN).isEmpty());
    }
}
