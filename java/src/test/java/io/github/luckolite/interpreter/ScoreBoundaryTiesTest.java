// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ScoreBoundaryTiesTest {
    private ScoreNoteEvent note(int bar, int pitch, int evidence, int accidental) {
        return new ScoreNoteEvent(
                        bar,
                        bar == 0 ? .8f : .1f,
                        pitch,
                        0,
                        2,
                        bar == 0 ? .8f : .2f,
                        false,
                        0,
                        0,
                        accidental,
                        1)
                .withClef(30)
                .withBoundaryTies(evidence);
    }

    private List<ScoreNoteEvent> resolve(ScoreNoteEvent a, ScoreNoteEvent b) {
        return ScoreBoundaryTies.resolve(
                List.of(a, b), List.of(new ScoreKeyChange(0, 0)), List.of(1));
    }

    @Test
    public void matchingShouldersResolveOnlyAfterPageAssembly() {
        var a = note(0, 2, 8, 2);
        var b = note(1, 2, 2, 2);
        assertFalse(b.tiedFromPrevious());
        assertTrue(resolve(a, b).get(1).tiedFromPrevious());
    }

    @Test
    public void eitherMissingShoulderPreventsTie() {
        assertFalse(resolve(note(0, 2, 0, 2), note(1, 2, 2, 2)).get(1).tiedFromPrevious());
        assertFalse(resolve(note(0, 2, 8, 2), note(1, 2, 0, 2)).get(1).tiedFromPrevious());
    }

    @Test
    public void oppositeSidesCannotPair() {
        assertFalse(resolve(note(0, 2, 4, 2), note(1, 2, 2, 2)).get(1).tiedFromPrevious());
    }

    @Test
    public void differentPitchesCannotPair() {
        assertFalse(resolve(note(0, 2, 8, 2), note(1, 3, 2, 2)).get(1).tiedFromPrevious());
    }

    @Test
    public void differentRegistersCannotPair() {
        assertFalse(
                resolve(note(0, 2, 8, 2), note(1, 2, 2, 2).withOctaveShift(1))
                        .get(1)
                        .tiedFromPrevious());
    }

    @Test
    public void printedAccidentalContradictionPreventsTie() {
        assertFalse(resolve(note(0, 2, 8, 1), note(1, 2, 2, 0)).get(1).tiedFromPrevious());
    }

    @Test
    public void unmarkedContinuationRetainsPriorAccidental() {
        var tied = resolve(note(0, 2, 8, -1), note(1, 2, 2, 2)).get(1);
        assertTrue(tied.tiedFromPrevious());
        assertEquals(-1, tied.writtenAccidental());
    }

    @Test
    public void leadingRestPreventsTie() {
        assertFalse(
                resolve(note(0, 2, 8, 2), note(1, 2, 2, 2).withLeadingRest(.5f))
                        .get(1)
                        .tiedFromPrevious());
    }

    @Test
    public void nonadjacentMeasuresCannotPair() {
        assertFalse(resolve(note(0, 2, 8, 2), note(2, 2, 2, 2)).get(1).tiedFromPrevious());
    }

    @Test
    public void noAssemblyBoundaryMeansNoTie() {
        var result =
                ScoreBoundaryTies.resolve(
                        List.of(note(0, 2, 8, 2), note(1, 2, 2, 2)),
                        List.of(new ScoreKeyChange(0, 0)),
                        List.of());
        assertFalse(result.get(1).tiedFromPrevious());
    }

    @Test
    public void interveningAttackPreventsTie() {
        var a = note(0, 2, 8, 2);
        var other = new ScoreNoteEvent(0, .95f, 3, 0, 2);
        var b = note(1, 2, 2, 2);
        var result =
                ScoreBoundaryTies.resolve(
                        List.of(a, other, b), List.of(new ScoreKeyChange(0, 0)), List.of(1));
        assertFalse(result.get(2).tiedFromPrevious());
    }

    @Test
    public void evidenceSurvivesEventTransformations() {
        var n =
                note(0, 2, 10, 2)
                        .withOctaveShift(1)
                        .withClef(18)
                        .withCompactOpening()
                        .withLeadingRest(.25f)
                        .withArticulations(1)
                        .withCrossStaffBeam();
        assertEquals(10, n.boundaryTies());
    }

    @Test
    public void invalidEvidenceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> note(0, 2, 16, 2));
    }

    @Test
    public void bothKeyOrdersRetainCompleteNotesAndEarlyReturnBranches() {
        for (int count = 0; count <= 7; count++) {
            for (int sign : new int[] {1, -1}) {
                int fifths = count * sign;
                for (int letter = 0; letter < 7; letter++) {
                    int step = Math.floorMod(letter - 2, 7); // Treble bottom E is letter 2.
                    var earlier = note(0, step, 8, ScoreNoteEvent.ACCIDENTAL_FROM_KEY);
                    var current = note(1, step, 2, ScoreNoteEvent.ACCIDENTAL_FROM_KEY);
                    var notes = List.of(earlier, current);
                    int firstAltered =
                            sign > 0
                                    ? switch (letter) {
                                        case 0 -> 2;
                                        case 1 -> 4;
                                        case 2 -> 6;
                                        case 3 -> 1;
                                        case 4 -> 3;
                                        case 5 -> 5;
                                        default -> 7;
                                    }
                                    : switch (letter) {
                                        case 0 -> 6;
                                        case 1 -> 4;
                                        case 2 -> 2;
                                        case 3 -> 7;
                                        case 4 -> 5;
                                        case 5 -> 3;
                                        default -> 1;
                                    };
                    int accidental =
                            count >= firstAltered ? sign : ScoreNoteEvent.ACCIDENTAL_NATURAL;
                    var expected =
                            new ScoreNoteEvent(1, .1f, step, 0, 2, .2f, true, 0, 0, accidental, 1)
                                    .withClef(30)
                                    .withBoundaryTies(2);
                    var result =
                            ScoreBoundaryTies.resolve(
                                    notes, List.of(new ScoreKeyChange(0, fifths)), List.of(1));
                    assertEquals(List.of(earlier, expected), result);
                    assertSame(earlier, result.get(0));
                    assertSame(current, notes.get(1));
                    assertFalse(current.tiedFromPrevious());
                    assertEquals(ScoreNoteEvent.ACCIDENTAL_FROM_KEY, current.writtenAccidental());
                }
            }
        }
        var earlier = note(0, 2, 8, ScoreNoteEvent.ACCIDENTAL_NATURAL);
        var current = note(1, 2, 2, ScoreNoteEvent.ACCIDENTAL_FROM_KEY);
        assertTrue(
                ScoreBoundaryTies.resolve(List.of(earlier, current), null, List.of(1))
                        .get(1)
                        .tiedFromPrevious()); // Explicit accidental must not traverse keys.
        var inferred = note(0, 2, 8, ScoreNoteEvent.ACCIDENTAL_FROM_KEY);
        assertSame(
                current,
                ScoreBoundaryTies.resolve(List.of(inferred, current), List.of(), List.of(1))
                        .get(1));
        assertThrows(
                NullPointerException.class,
                () -> ScoreBoundaryTies.resolve(List.of(inferred, current), null, List.of(1)));
    }
}
