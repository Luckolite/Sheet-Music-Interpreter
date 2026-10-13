// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedSlurContextTest {
    private ScoreNoteEvent note(float position) {
        return new ScoreNoteEvent(0, position, 2, 0, 1, .4f, false);
    }

    private ScoreNoteTiming.WrittenPlacement ordinary(double onset) {
        return new ScoreNoteTiming.WrittenPlacement(onset, 1, -1, -1, false, 0);
    }

    private List<PrintedSlurOwners.Head> heads(
            List<ScoreNoteEvent> notes,
            Map<Integer, ScoreNoteTiming.WrittenPlacement> written,
            Map<Integer, Integer> voices,
            Map<Integer, Integer> pitches) {
        return PrintedSlurContext.heads(
                notes,
                List.of(new MeasureRegion(.2f, .8f, .2f, .6f)),
                written,
                new ScoreMeterMap(4, List.of()),
                voices,
                pitches,
                1000,
                2000);
    }

    @Test
    public void finalizedSlotsUseTheirOwnMeasureGeometryAndWrittenClock() {
        var result =
                heads(
                        List.of(note(.25f), note(.75f)),
                        Map.of(1, ordinary(2), 0, ordinary(0)),
                        Map.of(0, 3, 1, 3),
                        Map.of(0, 60, 1, 64));
        assertEquals(0, result.get(0).owner());
        assertEquals(350, result.get(0).x(), .001);
        assertEquals(800, result.get(0).y(), .001);
        assertEquals(20160, result.get(1).attack());
        assertEquals(3, result.get(1).voice());
        assertEquals(64, result.get(1).soundingPitch());
    }

    @Test
    public void missingMusicalEvidenceStaysUnknown() {
        var result = heads(List.of(note(.25f)), Map.of(0, ordinary(0)), Map.of(), Map.of());
        assertEquals(0, result.get(0).voice());
        assertEquals(-1, result.get(0).soundingPitch());
    }

    @Test
    public void absentWrittenPlacementCannotAcquireAZeroAttack() {
        assertTrue(heads(List.of(note(.25f)), Map.of(), Map.of(0, 1), Map.of(0, 60)).isEmpty());
    }

    @Test
    public void graceLaneComesFromItsWrittenPrincipal() {
        var notes = List.of(note(.2f).withArticulations(NoteOrnament.GRACE), note(.3f));
        var written =
                Map.of(
                        0,
                        new ScoreNoteTiming.WrittenPlacement(0, .5, 1, 0, false, 0),
                        1,
                        ordinary(0));
        var result = heads(notes, written, Map.of(0, 7, 1, 2), Map.of(0, 60, 1, 62));
        assertEquals(2, result.get(0).voice());
        assertTrue(result.get(0).graceOrder() < result.get(1).graceOrder());
        result = heads(notes, written, Map.of(0, 7), Map.of(0, 60, 1, 62));
        assertEquals(0, result.get(0).voice());
    }

    @Test
    public void unpitchedOwnerCannotBecomeATiePitch() {
        var n = note(.25f).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        assertEquals(
                -1,
                heads(List.of(n), Map.of(0, ordinary(0)), Map.of(0, 1), Map.of(0, 60))
                        .get(0)
                        .soundingPitch());
    }

    @Test(expected = IllegalArgumentException.class)
    public void staleOwnerMapCannotBeReusedForAnotherNoteList() {
        heads(List.of(note(.25f)), Map.of(0, ordinary(0)), Map.of(1, 1), Map.of(0, 60));
    }
}
