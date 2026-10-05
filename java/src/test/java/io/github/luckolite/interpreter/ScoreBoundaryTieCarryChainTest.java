// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original scalar source records; no optical, commercial-score or audio fixture. */
public final class ScoreBoundaryTieCarryChainTest {
    private static final int KEY = ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
    private static final List<ScoreKeyChange> C = List.of(new ScoreKeyChange(0, 0));

    private static ScoreNoteEvent note(int bar, int accidental, int flags) {
        return new ScoreNoteEvent(bar, .5f, 1, 0, 2, .5f, false, 0, 0, accidental, 4)
                .withClef(ScoreNoteEvent.CLEF_TREBLE)
                .withBoundaryTies(flags);
    }

    private static ScoreNoteEvent copy(
            ScoreNoteEvent n, boolean tied, int accidental, float following, float position) {
        return new ScoreNoteEvent(
                n.measureIndex(),
                position,
                n.staffStep(),
                n.staffIndex(),
                n.staffCount(),
                n.pageY(),
                tied,
                n.augmentationDots(),
                n.beamCount(),
                accidental,
                n.unbeamedDurationBeats(),
                n.tupletDivisor(),
                following,
                n.articulations(),
                n.clefBottomDiatonic(),
                n.crossStaffBeam(),
                n.leadingRestBeats(),
                n.compactOpening(),
                n.octaveShift(),
                n.boundaryTies(),
                n.tupletNormalNotes(),
                n.stemDirection(),
                n.kind());
    }

    private static List<ScoreNoteEvent> resolve(
            List<ScoreNoteEvent> input, List<ScoreKeyChange> keys, List<Integer> boundaries) {
        List<ScoreNoteEvent> before = List.copyOf(input);
        var resolved = ScoreBoundaryTies.resolve(input, keys, boundaries);
        assertEquals("Immutable input changed", before, input);
        return resolved;
    }

    private static void assertChain(List<ScoreNoteEvent> notes, int accidental) {
        for (int i = 0; i < notes.size(); i++) {
            assertEquals(
                    "Accidental at source index " + i,
                    accidental,
                    notes.get(i).writtenAccidental());
            assertEquals("Tie at source index " + i, i != 0, notes.get(i).tiedFromPrevious());
        }
    }

    private static void threePages(int accidental, int outgoing, int incoming) {
        var input =
                List.of(
                        note(0, accidental, outgoing),
                        note(1, KEY, outgoing | incoming),
                        note(2, KEY, incoming));
        var result = resolve(input, C, List.of(1, 2));
        assertChain(result, accidental);
        assertChain(ScoreTiePitchGuard.recheckWithKeyContext(result, C), accidental);
        assertSame(input.get(0), result.get(0));
    }

    @Test
    public void originalThreePageSharpLowerShouldersCarryThroughActualPitchGuard() {
        threePages(1, 8, 2);
    }

    @Test
    public void originalThreePageFlatUpperShouldersCarryThroughActualPitchGuard() {
        threePages(-1, 4, 1);
    }

    @Test
    public void finiteLongChainRetainsDoubleAndNaturalAccidentals() {
        for (int accidental : new int[] {-2, -1, 0, 1, 3}) {
            var notes = new ArrayList<ScoreNoteEvent>();
            var boundaries = new ArrayList<Integer>();
            for (int i = 0; i < 64; i++) {
                notes.add(note(i, i == 0 ? accidental : KEY, (i > 0 ? 2 : 0) | (i < 63 ? 8 : 0)));
                if (i > 0) boundaries.add(i);
            }
            var result = resolve(notes, C, boundaries);
            assertChain(result, accidental);
            assertChain(ScoreTiePitchGuard.recheckWithKeyContext(result, C), accidental);
        }
    }

    @Test
    public void provedContinuationRetainsPitchAcrossAKeyChange() {
        var keys = List.of(new ScoreKeyChange(0, 0), new ScoreKeyChange(1, 1));
        var input = List.of(note(0, -1, 4), note(1, KEY, 5), note(2, KEY, 1));
        var result = resolve(input, keys, List.of(1, 2));
        assertChain(result, -1);
        assertChain(ScoreTiePitchGuard.recheckWithKeyContext(result, keys), -1);
    }

    @Test
    public void keyDerivedRootCanSupplyAContinuousChain() {
        var keys = List.of(new ScoreKeyChange(0, 1), new ScoreKeyChange(2, 0));
        var input = List.of(note(0, KEY, 8), note(1, KEY, 10), note(2, KEY, 2));
        var result = resolve(input, keys, List.of(1, 2));
        assertSame(input.get(0), result.get(0));
        for (int i = 1; i < 3; i++) {
            assertEquals(1, result.get(i).writtenAccidental());
            assertTrue(result.get(i).tiedFromPrevious());
        }
        assertEquals(result, ScoreTiePitchGuard.recheckWithKeyContext(result, keys));
    }

    @Test
    public void brokenFirstLinkDoesNotBorrowTheOlderSharp() {
        var input = List.of(note(0, 1, 8), note(1, KEY, 8), note(2, KEY, 2));
        var result = resolve(input, C, List.of(1, 2));
        assertSame(input.get(1), result.get(1));
        assertFalse(result.get(1).tiedFromPrevious());
        assertEquals(0, result.get(2).writtenAccidental());
        assertTrue(result.get(2).tiedFromPrevious());
    }

    @Test
    public void independentAttackAfterTheChainUsesItsOwnKeyContext() {
        var keys = List.of(new ScoreKeyChange(0, 0), new ScoreKeyChange(2, -7));
        var input = List.of(note(0, 1, 8), note(1, KEY, 10), note(2, KEY, 8), note(3, KEY, 2));
        var result = resolve(input, keys, List.of(1, 2, 3));
        assertSame(input.get(2), result.get(2));
        assertEquals(KEY, result.get(2).writtenAccidental());
        assertFalse(result.get(2).tiedFromPrevious());
        assertEquals(-1, result.get(3).writtenAccidental());
        assertTrue(result.get(3).tiedFromPrevious());
    }

    @Test
    public void explicitContradictionBreaksCarryAndDefinesTheNextLink() {
        var input = List.of(note(0, 1, 8), note(1, 0, 10), note(2, KEY, 2));
        var result = resolve(input, C, List.of(1, 2));
        assertSame(input.get(1), result.get(1));
        assertFalse(result.get(1).tiedFromPrevious());
        assertEquals(0, result.get(2).writtenAccidental());
        assertTrue(result.get(2).tiedFromPrevious());
    }

    @Test
    public void explicitFinalContradictionIsNeverOverwritten() {
        var input = List.of(note(0, 1, 8), note(1, KEY, 10), note(2, -1, 2));
        var result = resolve(input, C, List.of(1, 2));
        assertEquals(1, result.get(1).writtenAccidental());
        assertSame(input.get(2), result.get(2));
        assertFalse(result.get(2).tiedFromPrevious());
    }

    @Test
    public void noShoulderEvidenceDoesNotTurnASlurIntoATie() {
        var input = List.of(note(0, 1, 0), note(1, KEY, 0), note(2, KEY, 0));
        assertEquals(input, resolve(input, C, List.of(1, 2)));
    }

    @Test
    public void missingSecondActualBoundaryCannotContinueTheChain() {
        var input = List.of(note(0, 1, 8), note(1, KEY, 10), note(2, KEY, 2));
        var result = resolve(input, C, List.of(1));
        assertTrue(result.get(1).tiedFromPrevious());
        assertSame(input.get(2), result.get(2));
    }

    @Test
    public void oppositeSecondShoulderCannotContinueTheChain() {
        var input = List.of(note(0, 1, 8), note(1, KEY, 6), note(2, KEY, 2));
        var result = resolve(input, C, List.of(1, 2));
        assertTrue(result.get(1).tiedFromPrevious());
        assertSame(input.get(2), result.get(2));
    }

    @Test
    public void positiveFollowingRestInterruptsTheSecondLink() {
        var middle = copy(note(1, KEY, 10), false, KEY, .5f, .5f);
        var input = List.of(note(0, 1, 8), middle, note(2, KEY, 2));
        var result = resolve(input, C, List.of(1, 2));
        assertTrue(result.get(1).tiedFromPrevious());
        assertSame(input.get(2), result.get(2));
    }

    @Test
    public void positiveLeadingRestInterruptsTheSecondLink() {
        var input = List.of(note(0, 1, 8), note(1, KEY, 10), note(2, KEY, 2).withLeadingRest(.5f));
        var result = resolve(input, C, List.of(1, 2));
        assertTrue(result.get(1).tiedFromPrevious());
        assertSame(input.get(2), result.get(2));
    }

    @Test
    public void interveningPitchedOrUnpitchedAttackBlocksCarry() {
        for (var kind : ScoreNoteEvent.Kind.values()) {
            var other = copy(note(1, KEY, 0), false, KEY, 0, .85f).withKind(kind);
            var input = List.of(note(0, 1, 8), note(1, KEY, 10), other, note(2, KEY, 2));
            var result = resolve(input, C, List.of(1, 2));
            assertTrue(result.get(1).tiedFromPrevious());
            assertSame(other, result.get(2));
            assertSame(input.get(3), result.get(3));
        }
    }

    @Test
    public void changedRegisterCannotContinueTheChain() {
        var input = List.of(note(0, 1, 8), note(1, KEY, 10), note(2, KEY, 2).withOctaveShift(1));
        var result = resolve(input, C, List.of(1, 2));
        assertTrue(result.get(1).tiedFromPrevious());
        assertSame(input.get(2), result.get(2));
    }

    @Test
    public void missingKeyRemainsUnknownUnlessTheRootIsExplicit() {
        var explicit = List.of(note(0, 1, 8), note(1, KEY, 10), note(2, KEY, 2));
        assertChain(resolve(explicit, List.of(), List.of(1, 2)), 1);
        var unknown = List.of(note(0, KEY, 8), note(1, KEY, 10), note(2, KEY, 2));
        assertEquals(unknown, resolve(unknown, List.of(), List.of(1, 2)));
    }

    @Test
    public void carriedCopyPreservesAllOtherTwentyThreeComponents() throws Exception {
        var middle =
                new ScoreNoteEvent(
                        1,
                        .5f,
                        1,
                        0,
                        2,
                        .44f,
                        false,
                        1,
                        2,
                        KEY,
                        4,
                        3,
                        0,
                        0x100,
                        ScoreNoteEvent.CLEF_TREBLE,
                        true,
                        0,
                        true,
                        1,
                        10,
                        2,
                        -1,
                        ScoreNoteEvent.Kind.PITCHED);
        var last =
                new ScoreNoteEvent(
                        2,
                        .5f,
                        1,
                        0,
                        2,
                        .3f,
                        false,
                        2,
                        1,
                        KEY,
                        2,
                        5,
                        0,
                        0x20,
                        ScoreNoteEvent.CLEF_TREBLE,
                        false,
                        0,
                        false,
                        1,
                        2,
                        4,
                        1,
                        ScoreNoteEvent.Kind.PITCHED);
        var input = List.of(note(0, -1, 8).withOctaveShift(1), middle, last);
        var result = resolve(input, C, List.of(1, 2));
        assertChain(result, -1);
        assertEquals(23, ScoreNoteEvent.class.getRecordComponents().length);
        for (int i = 1; i < 3; i++)
            for (RecordComponent field : ScoreNoteEvent.class.getRecordComponents())
                if (!field.getName().equals("writtenAccidental")
                        && !field.getName().equals("tiedFromPrevious"))
                    assertEquals(
                            field.getName(),
                            field.getAccessor().invoke(input.get(i)),
                            field.getAccessor().invoke(result.get(i)));
    }
}
