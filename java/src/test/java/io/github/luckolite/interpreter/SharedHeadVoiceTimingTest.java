// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original simultaneous unison voices with eight attacks and four held quarter notes. */
public class SharedHeadVoiceTimingTest {
    private List<ScoreNoteEvent> passage() {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 8; i++) {
            float x = .08f + i * .115f;
            int pitch = i % 2 == 0 ? -4 : 3;
            notes.add(
                    new ScoreNoteEvent(0, x, pitch, 1, 2, .6f, false, 0, 1, 2, 0)
                            .withStemDirection(1));
            if (i % 2 == 0)
                notes.add(
                        new ScoreNoteEvent(0, x, pitch, 1, 2, .6f, false, 0, 0, 2, 1)
                                .withStemDirection(-1));
        }
        return notes;
    }

    @Test
    public void bothVoicesKeepTheirWrittenDurations() {
        var notes = passage();
        for (var n : notes)
            assertEquals(
                    n.beamCount() == 0 ? 1 : .5,
                    ScoreNoteTiming.resolvedWrittenDurationBeats(n, notes, 4),
                    .00001);
    }

    @Test
    public void eighthVoiceKeepsEightHalfBeatAttacks() {
        var notes = passage();
        int i = 0;
        for (var n : notes)
            if (n.beamCount() == 1)
                assertEquals(i++ * .5, ScoreNoteTiming.beatInMeasure(n, notes, 4), .00001);
    }

    @Test
    public void quarterVoiceKeepsFourWholeBeatAttacks() {
        var notes = passage();
        int i = 0;
        for (var n : notes)
            if (n.beamCount() == 0) {
                assertTrue(ScoreNoteTiming.hasIndependentDuration(n, notes));
                assertEquals(i++, ScoreNoteTiming.beatInMeasure(n, notes, 4), .00001);
            }
    }

    @Test
    public void projectionRetainsBothRecordsAtEachSharedOnset() {
        var notes = passage();
        var projected = ScoreRhythmProjection.resolve(notes, notes, 4, List.of());
        assertEquals(12, projected.size());
        for (int i = 0; i < notes.size(); i++)
            assertEquals(
                    notes.get(i).beamCount() == 0 ? 1 : .5,
                    projected.get(i).durationBeats(),
                    .00001);
    }
}
