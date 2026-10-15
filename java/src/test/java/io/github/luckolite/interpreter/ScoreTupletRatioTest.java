// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ScoreTupletRatioTest {
    private static ScoreNoteEvent sixteenth(float x) {
        return new ScoreNoteEvent(
                0, x, 0, 0, 1, .4f, false, 0, 2, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, 0, 1);
    }

    @Test
    public void explicitFiveInThreePreservesOneFinalRegularSixteenth() {
        var phrase =
                List.of(
                        sixteenth(.10f).withTupletRatio(5, 3),
                        sixteenth(.24f).withTupletRatio(5, 3),
                        sixteenth(.38f).withTupletRatio(5, 3),
                        sixteenth(.52f).withTupletRatio(5, 3),
                        sixteenth(.66f).withTupletRatio(5, 3),
                        sixteenth(.86f));
        double total = 0;
        for (int i = 0; i < phrase.size(); i++) {
            double duration = ScoreNoteTiming.writtenDurationBeats(phrase.get(i));
            assertEquals(i < 5 ? .15 : .25, duration, 1e-8);
            assertEquals(
                    i < 5 ? i * .15 : .75,
                    ScoreNoteTiming.beatInMeasure(phrase.get(i), phrase, 1),
                    1e-8);
            total += duration;
        }
        assertEquals(1, total, 1e-8);
    }

    @Test
    public void legacyConstructorsKeepConventionalRatios() {
        for (int[] ratio : new int[][] {{3, 2}, {5, 4}, {6, 4}, {7, 4}}) {
            var note =
                    new ScoreNoteEvent(
                            0,
                            .2f,
                            0,
                            0,
                            1,
                            .4f,
                            false,
                            0,
                            2,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            0,
                            ratio[0]);
            assertEquals(ratio[1], note.tupletNormalNotes());
            assertEquals(
                    .25 * ratio[1] / ratio[0], ScoreNoteTiming.writtenDurationBeats(note), 1e-8);
        }
    }

    @Test
    public void independentNoteEditsRetainExplicitRatio() {
        var note = sixteenth(.2f).withTupletRatio(5, 3);
        for (var changed :
                List.of(
                        note.withBoundaryTies(5),
                        note.withOctaveShift(1),
                        note.withCompactOpening(),
                        note.withLeadingRest(.25f),
                        note.withCrossStaffBeam(),
                        note.withClef(ScoreNoteEvent.CLEF_BASS),
                        note.withArticulations(1))) {
            assertEquals(5, changed.tupletDivisor());
            assertEquals(3, changed.tupletNormalNotes());
            assertEquals(.6, changed.durationScale(), 1e-8);
        }
    }

    @Test
    public void invalidRatiosAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> sixteenth(.2f).withTupletRatio(5, 0));
        assertThrows(IllegalArgumentException.class, () -> sixteenth(.2f).withTupletRatio(1, 3));
        assertThrows(IllegalArgumentException.class, () -> sixteenth(.2f).withTupletRatio(8, 3));
    }
}
