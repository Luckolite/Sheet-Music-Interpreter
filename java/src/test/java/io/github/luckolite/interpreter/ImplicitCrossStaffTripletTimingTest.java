// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original twelve-attack exercise, with one note on the adjacent staff. */
public class ImplicitCrossStaffTripletTimingTest {
    private List<ScoreNoteEvent> phrase(boolean bridge, int count) {
        List<ScoreNoteEvent> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int staff = i == count - 3 ? 1 : 0;
            var n =
                    new ScoreNoteEvent(
                            0,
                            .06f + .88f * i / (count - 1),
                            i % 7,
                            staff,
                            2,
                            .4f + staff * .2f,
                            false,
                            0,
                            1,
                            2,
                            0);
            if (bridge && (i == count - 3 || i == count - 2)) n = n.withCrossStaffBeam();
            result.add(n);
        }
        return result;
    }

    @Test
    public void provedSharedTripletsHaveOneThirdBeatAttacks() {
        var n = phrase(true, 12);
        for (int i = 0; i < n.size(); i++) {
            assertEquals(i / 3.0, ScoreNoteTiming.beatInMeasure(n.get(i), n, 4), .000001);
            assertEquals(
                    1 / 3.0, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(i), n, 4), .000001);
        }
    }

    @Test
    public void independentSustainedBassKeepsItsWrittenLength() {
        var n = phrase(true, 12);
        var held = new ScoreNoteEvent(0, .06f, -5, 1, 2, .6f, false, 0, 0, 2, 4);
        n.add(held);
        assertEquals(4, ScoreNoteTiming.resolvedWrittenDurationBeats(held, n, 4), 0);
        assertEquals(0, ScoreNoteTiming.beatInMeasure(held, n, 4), 0);
        for (int i = 0; i < 12; i++)
            assertEquals(i / 3.0, ScoreNoteTiming.beatInMeasure(n.get(i), n, 4), .000001);
    }

    @Test
    public void spacingWithoutBeamProofDoesNotJoinTheStaves() {
        var n = phrase(false, 12);
        assertEquals(.5, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(9), n, 4), 0);
    }

    @Test
    public void completeOrdinaryEighthsRemainOrdinary() {
        var n = phrase(true, 8);
        for (int i = 0; i < 8; i++) {
            assertEquals(i * .5, ScoreNoteTiming.beatInMeasure(n.get(i), n, 4), .000001);
            assertEquals(.5, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(i), n, 4), 0);
        }
    }

    @Test
    public void inputOrderDoesNotChangeTheSharedClock() {
        var n = phrase(true, 12);
        var target = n.get(9);
        Collections.reverse(n);
        assertEquals(3, ScoreNoteTiming.beatInMeasure(target, n, 4), .000001);
    }

    @Test
    public void incompleteRunDoesNotAcquireAnImplicitRatio() {
        var n = phrase(true, 11);
        assertEquals(.5, ScoreNoteTiming.resolvedWrittenDurationBeats(n.get(8), n, 4), 0);
    }
}
