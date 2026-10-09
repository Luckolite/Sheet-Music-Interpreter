// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.luckolite.interpreter.ScoreExpressiveEvent.Scope;

/** Original independent silence, actual bar spans and explicit pickup witnesses. */
public class WholeMeasureRestConsumersTest {
    static ScoreRestEvent full(int staff, int count) {
        return ScoreRestEvent.fullMeasure(0, .5f, .25f + staff * .1f, .03f, staff, count);
    }

    static List<ScoreNoteEvent> moving(int count) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < count; i++)
            notes.add(
                    new ScoreNoteEvent(0, .1f + i * .22f, 0, 0, 1, .6f, false, 0, 0, 2, 1)
                            .withStemDirection(-1));
        return List.copyOf(notes);
    }

    static ScorePageInterpretation hold(
            ScoreRestEvent rest, List<ScoreNoteEvent> notes, float beats) {
        var event =
                ScoreRestFermataDetector.event(
                        rest, new NoteArticulationDetector.FermataMark(0, 220, 100, false), 400);
        var score =
                new ScorePageInterpretation(
                        List.of(new MeasureRegion(.1f, .9f, .2f, .9f)),
                        notes,
                        1,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(rest),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(event));
        return ScoreRestFermataDetector.resolve(score, beats);
    }

    static void assertHold(float beats, List<ScoreNoteEvent> notes) {
        var rest = full(0, 1);
        var score = hold(rest, notes, beats);
        var event = score.expressiveEvents().get(0);
        assertEquals(Scope.REST, event.scope());
        assertEquals(new ScoreAnchor(0, 0), event.start().orElseThrow());
        assertEquals(new ScoreAnchor(1, 0), event.end().orElseThrow());
        assertEquals(notes, score.notes());
        assertEquals(List.of(rest), score.rests());
        assertEquals(score, ScoreRestFermataDetector.resolve(score, beats));
    }

    @Test
    public void threeFourWholeRestFermataOwnsEntireBar() {
        assertHold(3, List.of());
    }

    @Test
    public void sixEightWholeRestFermataOwnsEntireBar() {
        assertHold(new ScoreMeterChange(0, 6, 8).quarterBeats(), List.of());
    }

    @Test
    public void pickupWholeRestFermataOwnsActualSpan() {
        assertHold(1, List.of());
    }

    @Test
    public void independentWholeRestFermataCanOverlapMovingVoice() {
        assertHold(3, moving(3));
    }

    @Test
    public void longWholeRestFermataOwnsEightBeats() {
        assertHold(8, List.of());
    }

    @Test
    public void literalFourFermataCannotBecomeThreeBeatBar() {
        assertEquals(
                Scope.UNRESOLVED,
                hold(new ScoreRestEvent(0, .5f, .25f, .03f, 0, 1, 4), List.of(), 3)
                        .expressiveEvents()
                        .get(0)
                        .scope());
    }

    @Test
    public void ordinarySilentGapIgnoresIndependentFullRest() {
        var literal = new ScoreRestEvent(0, .7f, .55f, .03f, 0, 1, 1);
        assertEquals(
                0,
                ScoreRestFermataDetector.provedOnset(
                        literal, List.of(literal, full(0, 1)), List.of(), 1),
                0);
    }

    static List<ScoreRestEvent> pickupRests() {
        return List.of(
                ScoreOpeningDurationTest.rests().get(0),
                ScoreOpeningDurationTest.rests().get(1),
                full(1, 2));
    }

    static double pickup(List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests) {
        return ScoreOpeningDuration.provedQuarterBeats(
                notes, rests, ScoreOpeningDurationTest.SHORT, 4, 1);
    }

    @Test
    public void silentStaffAdoptsIndependentlyProvedPickup() {
        assertEquals(1, pickup(ScoreOpeningDurationTest.notes(), pickupRests()), .0001);
        assertEquals(
                1,
                full(1, 2)
                        .resolvedDurationBeats(
                                pickup(ScoreOpeningDurationTest.notes(), pickupRests())),
                .0001);
    }

    @Test
    public void silentStaffCannotSupplyMissingExplicitGap() {
        assertTrue(
                Double.isNaN(
                        pickup(ScoreOpeningDurationTest.notes(), pickupRests().subList(1, 3))));
    }

    @Test
    public void allWholeRestOpeningCannotProveItsOwnSpan() {
        assertTrue(Double.isNaN(pickup(List.of(), List.of(full(0, 2), full(1, 2)))));
    }

    @Test
    public void duplicateWholeRestStaffCannotProvePickup() {
        var rests = new ArrayList<>(pickupRests());
        rests.add(full(1, 2));
        assertTrue(Double.isNaN(pickup(ScoreOpeningDurationTest.notes(), rests)));
    }

    @Test
    public void oneMovingEndingAndOneWholeRestStaffCannotProveShortEnding() {
        var notes = List.of(new ScoreNoteEvent(0, .1f, 0, 0, 2, .25f, false, 0, 0, 2, .5f));
        var rests = List.of(new ScoreRestEvent(0, .8f, .25f, .03f, 0, 2, .5), full(1, 2));
        assertTrue(
                Double.isNaN(ScoreOpeningDuration.provedClosingQuarterBeats(notes, rests, 1, 4)));
    }

    @Test
    public void twoExplicitEndingStaffsCanCarryThirdSilentStaff() {
        var notes =
                List.of(
                        new ScoreNoteEvent(0, .1f, 0, 0, 3, .25f, false, 0, 0, 2, .5f),
                        new ScoreNoteEvent(0, .1f, 0, 1, 3, .35f, false, 0, 0, 2, .5f));
        var rests =
                List.of(
                        new ScoreRestEvent(0, .8f, .25f, .03f, 0, 3, .5),
                        new ScoreRestEvent(0, .8f, .35f, .03f, 1, 3, .5),
                        full(2, 3));
        assertEquals(1, ScoreOpeningDuration.provedClosingQuarterBeats(notes, rests, 1, 4), .0001);
    }
}
