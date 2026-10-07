// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original sixteenth-note triplets fill a two-quarter-beat measure with one rest. */
public class ContinuedSemiquaverRestTest {
    private ScoreNoteEvent event(int slot, int beams, float duration, float lead, float follow) {
        return new ScoreNoteEvent(
                0,
                .04f + slot * .08f,
                5,
                1,
                2,
                .6f,
                false,
                0,
                beams,
                0,
                duration,
                1,
                follow,
                0,
                0,
                false,
                lead,
                false,
                0,
                0,
                1,
                beams == 0 ? 0 : 1);
    }

    private void verify(boolean leading) {
        var score = new ArrayList<ScoreNoteEvent>();
        var moving = new ArrayList<ScoreNoteEvent>();
        var slots = new ArrayList<Integer>();
        for (int slot = 0; slot < 12; slot++) {
            if (slot == (leading ? 0 : 6)) continue;
            var n =
                    event(
                            slot,
                            2,
                            0,
                            leading && slot == 1 ? .25f : 0,
                            !leading && slot == 5 ? .25f : 0);
            score.add(n);
            moving.add(n);
            slots.add(slot);
        }
        score.add(event(0, 0, 2, 0, 0));
        for (int i = 0; i < moving.size(); i++) {
            assertEquals(
                    slots.get(i) / 6.,
                    ScoreNoteTiming.beatInMeasure(moving.get(i), score, 2),
                    .0001);
            assertEquals(
                    1 / 6.,
                    ScoreNoteTiming.resolvedWrittenDurationBeats(moving.get(i), score, 2),
                    .0001);
        }
        assertEquals(
                2,
                ScoreNoteTiming.resolvedWrittenDurationBeats(score.get(score.size() - 1), score, 2),
                .0001);
    }

    @Test
    public void internalSixteenthRestKeepsTheTripletGrid() {
        verify(false);
    }

    @Test
    public void leadingSixteenthRestKeepsTheTripletGrid() {
        verify(true);
    }
}
