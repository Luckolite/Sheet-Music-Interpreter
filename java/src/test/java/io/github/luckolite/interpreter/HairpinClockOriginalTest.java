// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class HairpinClockOriginalTest {
    static ScoreNoteEvent n(float x, int lane, int count, float duration) {
        return new ScoreNoteEvent(0, x, 2, lane, count, .4f, false, 0, 0, 0, duration, 1, 0);
    }

    static ScoreDynamicChange d(float start, float end) {
        return new ScoreDynamicChange(0, start, 0, 1, 0, end, 0, 1);
    }

    static ScoreNoteTiming.WrittenPlacement p(double start, double duration) {
        return new ScoreNoteTiming.WrittenPlacement(start, duration, -1, -1, false, 0);
    }

    @Test
    public void insetStartUsesWrittenZero() {
        assertEquals(
                0,
                ScoreHairpinClock.start(
                                d(.2f, .8f), List.of(n(.2f, 0, 1, 3), n(.8f, 0, 1, 1)), Map.of(), 4)
                        .orElseThrow(),
                0);
    }

    @Test
    public void stopUsesWrittenColumnRatherThanSpacing() {
        assertEquals(
                3,
                ScoreHairpinClock.end(
                                d(.2f, .8f), List.of(n(.2f, 0, 1, 3), n(.8f, 0, 1, 1)), Map.of(), 4)
                        .orElseThrow(),
                0);
    }

    @Test
    public void editedRetainedClockIsAuthoritative() {
        assertEquals(
                2,
                ScoreHairpinClock.end(
                                d(.2f, .8f),
                                List.of(n(.2f, 0, 1, 3), n(.8f, 0, 1, 1)),
                                Map.of(0, p(0, 2), 1, p(2, 2)),
                                4)
                        .orElseThrow(),
                0);
    }

    @Test
    public void unownedStopKeepsUnknownClock() {
        assertTrue(
                ScoreHairpinClock.end(
                                d(.2f, .8001f),
                                List.of(n(.2f, 0, 1, 3), n(.8f, 0, 1, 1)),
                                Map.of(),
                                4)
                        .isEmpty());
    }

    @Test
    public void centeredWholeBarStartsBeforeItsHeadAtZero() {
        assertEquals(
                0,
                ScoreHairpinClock.start(d(.1f, .75f), List.of(n(.4f, 0, 1, 4)), Map.of(), 4)
                        .orElseThrow(),
                0);
    }

    @Test
    public void partialHeldNoteDoesNotInventAnEarlierClock() {
        assertTrue(
                ScoreHairpinClock.start(d(.1f, .75f), List.of(n(.4f, 0, 1, 3)), Map.of(), 4)
                        .isEmpty());
    }

    @Test
    public void leadingRestCannotBeDiscardedByWholeHoldFallback() {
        assertTrue(
                ScoreHairpinClock.start(
                                d(.1f, .75f),
                                List.of(n(.4f, 0, 1, 3).withLeadingRest(1)),
                                Map.of(),
                                4)
                        .isEmpty());
    }

    @Test
    public void emptyStaffCannotProveWholeHold() {
        assertTrue(ScoreHairpinClock.start(d(.1f, .75f), List.of(), Map.of(), 4).isEmpty());
    }

    @Test
    public void foreignStaffCannotSupplyWholeHold() {
        assertTrue(
                ScoreHairpinClock.start(d(.1f, .75f), List.of(n(.4f, 1, 2, 4)), Map.of(), 4)
                        .isEmpty());
    }

    @Test
    public void conflictingSharedColumnAbstains() {
        var ns = List.of(n(.2f, 0, 2, 4), n(.2f, 1, 2, 4));
        var c = new ScoreDynamicChange(0, .2f, 0, 2, 0, .8f, 0, 1, true);
        assertTrue(ScoreHairpinClock.start(c, ns, Map.of(0, p(0, 4), 1, p(1, 3)), 4).isEmpty());
    }

    @Test
    public void fullBarBoundaryUsesCurrentMeter() {
        assertEquals(
                3,
                ScoreHairpinClock.end(d(.2f, 1), List.of(n(.2f, 0, 1, 3)), Map.of(), 3)
                        .orElseThrow(),
                0);
    }

    @Test
    public void followingBarLeftBoundaryIsZero() {
        var c = new ScoreDynamicChange(0, .2f, 0, 1, 1, 0, 0, -1);
        assertEquals(
                0,
                ScoreHairpinClock.end(c, List.of(n(.2f, 0, 1, 4)), Map.of(), 4).orElseThrow(),
                0);
    }

    @Test
    public void absoluteAndInvalidMeterStayOutsideHairpinClock() {
        assertTrue(
                ScoreHairpinClock.start(
                                new ScoreDynamicChange(0, .2f, 0, 1, 0, .2f, 0, 0),
                                List.of(n(.2f, 0, 1, 4)),
                                Map.of(),
                                4)
                        .isEmpty());
        assertTrue(ScoreHairpinClock.end(d(.2f, 1), List.of(), Map.of(), Float.NaN).isEmpty());
    }
}
