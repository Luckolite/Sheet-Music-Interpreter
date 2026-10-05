// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Entirely synthetic standalone boundary-clock controls. */
public class ScoreBoundaryClockTest {
    static final List<MeasureRegion> SHORT =
            List.of(
                    new MeasureRegion(.1f, .2f, .2f, .4f),
                    new MeasureRegion(.21f, .46f, .2f, .4f),
                    new MeasureRegion(.47f, .75f, .2f, .4f));

    static ScoreNoteEvent note(int measure, int staff, float x, int beam, float duration) {
        return new ScoreNoteEvent(
                        measure,
                        x,
                        staff == 0 ? 3 : 0,
                        staff,
                        2,
                        .25f + staff * .1f,
                        false,
                        0,
                        beam,
                        2,
                        duration)
                .withClef(staff == 0 ? 30 : 18);
    }

    static ScoreRestEvent rest(int measure, int staff, float x, double duration) {
        return new ScoreRestEvent(measure, x, .25f + staff * .1f, .02f, staff, 2, duration);
    }

    static ScorePageInterpretation source(
            boolean pickup, boolean closingPartial, boolean meterChange) {
        var notes = new ArrayList<ScoreNoteEvent>();
        var rests = new ArrayList<ScoreRestEvent>();
        if (pickup)
            for (int staff = 0; staff < 2; staff++) {
                notes.add(note(0, staff, .8f, 1, 0).withLeadingRest(.5f));
                rests.add(rest(0, staff, .3f, .5));
            }
        else {
            notes.add(note(0, 0, .8f, 0, 1).withLeadingRest(1));
            rests.add(rest(0, 0, .3f, 1));
            notes.add(note(0, 1, .8f, 0, 4));
        }
        for (int staff = 0; staff < 2; staff++) {
            notes.add(note(1, staff, .3f, 0, meterChange ? 3 : 4));
            notes.add(
                    note(
                            2,
                            staff,
                            .2f,
                            closingPartial ? 1 : 0,
                            closingPartial ? 0 : meterChange ? 2 : 4));
            if (closingPartial) rests.add(rest(2, staff, .8f, .5));
        }
        return new ScorePageInterpretation(
                SHORT,
                notes,
                1,
                List.of(),
                List.of(),
                meterChange
                        ? List.of(new ScoreMeterChange(1, 3, 4), new ScoreMeterChange(2, 2, 4))
                        : List.of(),
                rests);
    }

    @Test
    public void completeFirstBarFreezesNominalSpanBeforeSparseMelodySelection() {
        var source = source(false, false, false);
        var clock = ScoreBoundaryClock.resolve(source, 4);
        assertEquals(new ScoreBoundaryClock(4, 4), clock);
        assertTrue(
                Double.isNaN(
                        ScoreOpeningDuration.provedQuarterBeats(
                                source.notes(), source.rests(), SHORT, 4, 1)));
        assertEquals(4, clock.meter(source, 4).startBeat(1), 0);
    }

    @Test
    public void actualPickupAndTerminalRestProofKeepTheirOriginalSpans() {
        var source = source(true, true, false);
        var clock = ScoreBoundaryClock.resolve(source, 4);
        assertEquals(new ScoreBoundaryClock(1, 1), clock);
        assertEquals(6, clock.meter(source, 4).startBeat(3), 0);
    }

    @Test
    public void interiorMeterChangesRemainPartOfTheCapturedGrid() {
        var source = source(true, true, true);
        var meter = ScoreBoundaryClock.resolve(source, 4).meter(source, 4);
        assertEquals(1, meter.startBeat(1), 0);
        assertEquals(4, meter.startBeat(2), 0);
        assertEquals(5, meter.startBeat(3), 0);
        assertEquals(
                List.of(new ScoreMeterChange(1, 3, 4), new ScoreMeterChange(2, 2, 4)),
                source.meterChanges());
    }

    @Test
    public void legacyAndInvalidExplicitClocksAreDistinctAndRejectedSafely() {
        var source = source(true, true, true);
        assertFalse(ScoreBoundaryClock.legacy().retained());
        assertEquals(9, ScoreBoundaryClock.legacy().meter(source, 4).startBeat(3), 0);
        assertThrows(IllegalArgumentException.class, () -> new ScoreBoundaryClock(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ScoreBoundaryClock(Double.NaN, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScoreBoundaryClock(1, 3).meter(source, 4));
        var single = new ScorePageInterpretation(List.of(SHORT.get(0)), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScoreBoundaryClock(1, 2).meter(single, 4));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScoreBoundaryClock(1, 1)
                                .meter(new ScorePageInterpretation(List.of(), List.of()), 4));
    }
}
