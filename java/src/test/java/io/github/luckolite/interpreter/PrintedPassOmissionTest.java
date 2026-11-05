// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Optional;
import org.junit.Test;

/** Original two-staff navigation fixture. */
public class PrintedPassOmissionTest {
    @Test
    public void onlyMarkedStaffLosesAttacksOnSecondVisit() {
        var meter = new ScoreMeterMap(4, List.of());
        var plan = ScoreNavigationPlan.create(3, List.of(
                new ScorePlaybackDirection(0, ScorePlaybackDirection.Kind.REPEAT_START),
                new ScorePlaybackDirection(2, ScorePlaybackDirection.Kind.REPEAT_END)), meter);
        assertEquals(List.of(0, 1, 0, 1, 2), plan.sourceMeasures());

        var notes = List.of(note(1, .1f, 0), note(1, .3f, 0), note(1, .6f, 0),
                note(1, .3f, 1), note(2, .1f, 0));
        var source = List.of(sound(0, 4), sound(1, 5), sound(2, 6), sound(3, 5), sound(4, 8));
        var projected = ScoreNavigationNoteProjection.project(
                source, plan, meter, ScoreNavigationNoteProjection.EntryPolicy.REATTACK);
        String sourceId = "printed-second-pass-omission:v1";
        var mark = new ScoreExpressiveEvent(sourceId + ":0:1:0",
                ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                Optional.of(new ScoreAnchor(1, 0)), Optional.of(new ScoreAnchor(2, 0)),
                ScoreExpressiveEvent.Scope.UNRESOLVED, 0, 2,
                Optional.of(Integer.toString(Float.floatToIntBits(.3f))),
                ScoreExpressiveEvent.Strength.UNSPECIFIED, "",
                List.of(new ScoreExpressiveEvent.Evidence(sourceId, 0, .4f, 0, 2, "Second pass")));

        var result = PrintedPassOmission.project(projected, List.of(mark), notes, plan, meter);
        assertEquals(20, result.performedBeats(), 0);
        var expected = projected.notes().stream().filter(n ->
                !(n.sourceIndex() == 1 && n.startBeat() == 13)
                        && !(n.sourceIndex() == 2 && n.startBeat() == 14)).toList();
        assertEquals(2, projected.notes().size() - expected.size());
        assertEquals(expected, result.notes());
        assertSame(projected, PrintedPassOmission.project(projected, List.of(), notes, plan, meter));
    }

    private static ScoreNoteEvent note(int bar, float position, int staff) {
        return new ScoreNoteEvent(bar, position, 0, staff, 2, .5f, false, 0, 1, 2, 0, 1);
    }

    private static ScoreNavigationNoteProjection.SourceNote sound(int owner, double beat) {
        return new ScoreNavigationNoteProjection.SourceNote("note-" + owner, owner, beat, beat + .5);
    }
}
