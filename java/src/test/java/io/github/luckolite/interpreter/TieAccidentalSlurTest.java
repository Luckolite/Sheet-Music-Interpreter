// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original same-staff-step slur and courtesy-accidental controls. */
public class TieAccidentalSlurTest {
    private ScoreNoteEvent note(int step, float x, boolean tied, int accidental) {
        return new ScoreNoteEvent(7, x, step, 0, 1, .34f, tied, 0, 2, accidental, 0, 1, 0)
                .withClef(ScoreNoteEvent.CLEF_TREBLE);
    }

    @SuppressWarnings("unchecked")
    private List<ScoreNoteEvent> guard(List<ScoreNoteEvent> notes, List<ScoreKeyChange> keys)
            throws Exception {
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "withoutPitchChangedTies", List.class, List.class, boolean[].class);
        method.setAccessible(true);
        return (List<ScoreNoteEvent>) method.invoke(null, notes, keys, new boolean[] {false, true});
    }

    @Test
    public void explicitSharpChangingSoundingPitchMakesSlurNotTie() throws Exception {
        // D major: G is natural. A printed sharp on the second G changes pitch.
        var notes =
                List.of(
                        note(2, .50f, false, ScoreNoteEvent.ACCIDENTAL_FROM_KEY),
                        note(2, .59f, true, ScoreNoteEvent.ACCIDENTAL_SHARP));
        assertFalse(guard(notes, List.of(new ScoreKeyChange(0, 2))).get(1).tiedFromPrevious());
    }

    @Test
    public void redundantSharpInKeySignatureCanStillTie() throws Exception {
        // D major: F is already sharp; an explicit F sharp may be cautionary.
        var notes =
                List.of(
                        note(1, .50f, false, ScoreNoteEvent.ACCIDENTAL_FROM_KEY),
                        note(1, .59f, true, ScoreNoteEvent.ACCIDENTAL_SHARP));
        assertTrue(guard(notes, List.of(new ScoreKeyChange(0, 2))).get(1).tiedFromPrevious());
    }

    @Test
    public void absentKeyEvidenceKeepsExistingTie() throws Exception {
        var notes =
                List.of(
                        note(2, .50f, false, ScoreNoteEvent.ACCIDENTAL_FROM_KEY),
                        note(2, .59f, true, ScoreNoteEvent.ACCIDENTAL_SHARP));
        assertTrue(guard(notes, List.of()).get(1).tiedFromPrevious());
    }

    @Test
    public void carriedAccidentalDoesNotPretendToBePrinted() throws Exception {
        var notes =
                List.of(
                        note(2, .50f, false, ScoreNoteEvent.ACCIDENTAL_FROM_KEY),
                        note(2, .59f, true, ScoreNoteEvent.ACCIDENTAL_SHARP));
        var method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "withoutPitchChangedTies", List.class, List.class, boolean[].class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var output =
                (List<ScoreNoteEvent>)
                        method.invoke(
                                null,
                                notes,
                                List.of(new ScoreKeyChange(0, 2)),
                                new boolean[] {false, false});
        assertTrue(output.get(1).tiedFromPrevious());
    }

    @Test
    public void sharpAndFlatKeyOrdersKeepCourtesyTiesAndRejectChangedPitchSlurs() throws Exception {
        // Treble bottom is E: steps1/5/-3/0 are F/C/B/E respectively.
        // F then C enter sharp signatures; B then E enter flat signatures.
        int[][] cases = {
            {1, 1, ScoreNoteEvent.ACCIDENTAL_SHARP, 1},
            {5, 2, ScoreNoteEvent.ACCIDENTAL_SHARP, 1},
            {-3, -1, ScoreNoteEvent.ACCIDENTAL_FLAT, 1},
            {0, -2, ScoreNoteEvent.ACCIDENTAL_FLAT, 1},
            {5, 1, ScoreNoteEvent.ACCIDENTAL_SHARP, 0},
            {0, -1, ScoreNoteEvent.ACCIDENTAL_FLAT, 0}
        };
        for (int[] example : cases) {
            var first = note(example[0], .50f, false, ScoreNoteEvent.ACCIDENTAL_FROM_KEY);
            var second = note(example[0], .59f, true, example[2]);
            var notes = List.of(first, second);
            var keys = List.of(new ScoreKeyChange(0, example[1]));
            var output = guard(notes, keys);
            assertEquals(
                    "written step " + example[0] + ", fifths " + example[1],
                    example[3] == 1,
                    output.get(1).tiedFromPrevious());
            assertEquals(2, output.size());
            assertSame(first, notes.get(0));
            assertSame(second, notes.get(1));
            assertTrue(second.tiedFromPrevious());
            assertEquals(example[2], second.writtenAccidental());
            assertEquals(1, keys.size());
            assertEquals(example[1], keys.get(0).fifths());
        }
    }
}
