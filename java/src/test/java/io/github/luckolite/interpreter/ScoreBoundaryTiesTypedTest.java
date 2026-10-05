// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original scalar actual-resolver controls; no source score, model or geometry inference. */
public class ScoreBoundaryTiesTypedTest {
    private static final String UNSUPPORTED =
            "Unpitched source tie flags do not prove shared instrument ownership";

    private static ScoreNoteEvent note(int bar, float position, int boundary) {
        return new ScoreNoteEvent(
                bar,
                position,
                1,
                0,
                1,
                .45f,
                false,
                1,
                2,
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                .75f,
                3,
                0,
                0x100,
                ScoreNoteEvent.CLEF_TREBLE,
                true,
                0,
                true,
                1,
                boundary,
                2,
                -1);
    }

    private static ScoreNoteEvent copy(
            ScoreNoteEvent n,
            boolean tied,
            int accidental,
            int clef,
            float leading,
            float following,
            int shift,
            int staff,
            int count) {
        return new ScoreNoteEvent(
                n.measureIndex(),
                n.positionInMeasure(),
                n.staffStep(),
                staff,
                count,
                n.pageY(),
                tied,
                n.augmentationDots(),
                n.beamCount(),
                accidental,
                n.unbeamedDurationBeats(),
                n.tupletDivisor(),
                following,
                n.articulations(),
                clef,
                n.crossStaffBeam(),
                leading,
                n.compactOpening(),
                shift,
                n.boundaryTies(),
                n.tupletNormalNotes(),
                n.stemDirection(),
                n.kind());
    }

    private static List<ScoreNoteEvent> resolve(List<ScoreNoteEvent> notes) {
        return ScoreBoundaryTies.resolve(notes, List.of(new ScoreKeyChange(0, 1)), List.of(1));
    }

    private static void unsupported(List<ScoreNoteEvent> notes, List<Integer> boundaries) {
        String before = notes.toString();
        try {
            ScoreBoundaryTies.resolve(notes, List.of(new ScoreKeyChange(0, 1)), boundaries);
            fail("Raw unpitched tie evidence was accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals(UNSUPPORTED, expected.getMessage());
        } finally {
            assertEquals(before, notes.toString());
        }
    }

    @Test
    public void currentUnpitchedIncomingRejectsBeforePitchQuery() {
        unsupported(
                List.of(note(0, .8f, 4), note(1, .1f, 1).withKind(ScoreNoteEvent.Kind.UNPITCHED)),
                List.of(1));
    }

    @Test
    public void earlierUnpitchedOutgoingRejectsBeforePitchQuery() {
        unsupported(
                List.of(note(0, .8f, 4).withKind(ScoreNoteEvent.Kind.UNPITCHED), note(1, .1f, 1)),
                List.of(1));
    }

    @Test
    public void rawUnpitchedTieWithoutBoundaryRejectsConsistently() {
        var value = note(0, .4f, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        unsupported(
                List.of(
                        copy(
                                value,
                                true,
                                value.writtenAccidental(),
                                value.clefBottomDiatonic(),
                                0,
                                0,
                                1,
                                0,
                                1)),
                List.of());
    }

    @Test
    public void unpitchedShoulderOutsideRequestedBoundaryStillRejects() {
        unsupported(List.of(note(3, .4f, 1).withKind(ScoreNoteEvent.Kind.UNPITCHED)), List.of());
    }

    @Test
    public void unpitchedShoulderWithUnknownClefStillRejects() {
        unsupported(
                List.of(
                        note(1, .1f, 1)
                                .withKind(ScoreNoteEvent.Kind.UNPITCHED)
                                .withClef(ScoreNoteEvent.CLEF_UNKNOWN)),
                List.of(1));
    }

    @Test
    public void unpitchedShoulderAtRestStillRejectsWithoutErasingEvidence() {
        var value = note(1, .1f, 1).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        unsupported(
                List.of(
                        copy(
                                value,
                                false,
                                value.writtenAccidental(),
                                value.clefBottomDiatonic(),
                                .5f,
                                0,
                                1,
                                0,
                                1)),
                List.of(1));
    }

    @Test
    public void validUnpitchedEqualDisplayRecordsRemainIndependentAndUnchanged() {
        var a = note(0, .2f, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var b = note(0, .2f, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        assertEquals(a, b);
        assertNotSame(a, b);
        var result = resolve(List.of(a, b));
        assertEquals(2, result.size());
        assertSame(a, result.get(0));
        assertSame(b, result.get(1));
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, result.get(0).kind());
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, result.get(1).kind());
    }

    @Test
    public void unpitchedEqualDisplayIsNotAPitchedShoulderCandidate() {
        var earlier = note(0, .8f, 4);
        var displayEqual = note(0, .8f, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var current = note(1, .1f, 1);
        var result = resolve(List.of(earlier, displayEqual, current));
        assertTrue(result.get(2).tiedFromPrevious());
        assertSame(displayEqual, result.get(1));
        assertEquals(ScoreNoteEvent.Kind.UNPITCHED, result.get(1).kind());
    }

    @Test
    public void unpitchedInterveningOccurrenceStillInterruptsPitchedShoulders() {
        var earlier = note(0, .8f, 4);
        var intervening = note(0, .95f, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var result = resolve(List.of(earlier, intervening, note(1, .1f, 1)));
        assertFalse(result.get(2).tiedFromPrevious());
        assertSame(intervening, result.get(1));
    }

    @Test
    public void unpitchedCurrentSideInterrupterStillInterruptsPitchedShoulders() {
        var intervening = note(1, .05f, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var result = resolve(List.of(note(0, .8f, 4), intervening, note(1, .1f, 1)));
        assertFalse(result.get(2).tiedFromPrevious());
        assertSame(intervening, result.get(1));
    }

    @Test
    public void differentStaffUnpitchedOccurrenceDoesNotInterrupt() {
        var value = note(0, .95f, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var otherStaff =
                copy(
                        value,
                        false,
                        value.writtenAccidental(),
                        value.clefBottomDiatonic(),
                        0,
                        0,
                        1,
                        1,
                        2);
        var result = resolve(List.of(note(0, .8f, 4), otherStaff, note(1, .1f, 1)));
        assertTrue(result.get(2).tiedFromPrevious());
        assertSame(otherStaff, result.get(1));
    }

    @Test
    public void unpitchedWithoutShoulderDoesNotSupportPitchedIncoming() {
        var earlier = note(0, .8f, 0).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var result = resolve(List.of(earlier, note(1, .1f, 1)));
        assertFalse(result.get(1).tiedFromPrevious());
        assertSame(earlier, result.get(0));
    }

    @Test
    public void pitchedReconstructionKeepsAllOtherLegacyFieldsAndKind() throws Exception {
        var current = note(1, .1f, 1);
        var result = resolve(List.of(note(0, .8f, 4), current)).get(1);
        assertTrue(result.tiedFromPrevious());
        assertEquals(1, result.writtenAccidental());
        assertEquals(23, ScoreNoteEvent.class.getRecordComponents().length);
        for (RecordComponent field : ScoreNoteEvent.class.getRecordComponents())
            if (!field.getName().equals("tiedFromPrevious")
                    && !field.getName().equals("writtenAccidental"))
                assertEquals(
                        field.getName(),
                        field.getAccessor().invoke(current),
                        field.getAccessor().invoke(result));
    }

    @Test
    public void pitchedShoulderAndExistingLegacyGuardsStayUnchanged() {
        assertTrue(resolve(List.of(note(0, .8f, 8), note(1, .1f, 2))).get(1).tiedFromPrevious());
        assertFalse(resolve(List.of(note(0, .8f, 4), note(1, .1f, 2))).get(1).tiedFromPrevious());
        assertFalse(
                ScoreBoundaryTies.resolve(
                                List.of(note(0, .8f, 4), note(1, .1f, 1)),
                                List.of(new ScoreKeyChange(0, 1)),
                                List.of())
                        .get(1)
                        .tiedFromPrevious());
        var current = note(1, .1f, 1);
        assertFalse(
                resolve(
                                List.of(
                                        note(0, .8f, 4),
                                        copy(
                                                current,
                                                false,
                                                current.writtenAccidental(),
                                                current.clefBottomDiatonic(),
                                                .5f,
                                                0,
                                                1,
                                                0,
                                                1)))
                        .get(1)
                        .tiedFromPrevious());
        var earlier = note(0, .8f, 4);
        assertFalse(
                resolve(
                                List.of(
                                        copy(
                                                earlier,
                                                false,
                                                earlier.writtenAccidental(),
                                                earlier.clefBottomDiatonic(),
                                                0,
                                                .5f,
                                                1,
                                                0,
                                                1),
                                        current))
                        .get(1)
                        .tiedFromPrevious());
        assertFalse(
                resolve(List.of(earlier.withClef(ScoreNoteEvent.CLEF_UNKNOWN), current))
                        .get(1)
                        .tiedFromPrevious());
        assertFalse(
                resolve(List.of(earlier, current.withClef(ScoreNoteEvent.CLEF_UNKNOWN)))
                        .get(1)
                        .tiedFromPrevious());
        assertFalse(
                resolve(
                                List.of(
                                        earlier,
                                        copy(
                                                current,
                                                false,
                                                current.writtenAccidental(),
                                                current.clefBottomDiatonic(),
                                                0,
                                                0,
                                                0,
                                                0,
                                                1)))
                        .get(1)
                        .tiedFromPrevious());
        assertFalse(
                resolve(
                                List.of(
                                        earlier,
                                        copy(
                                                current,
                                                false,
                                                ScoreNoteEvent.ACCIDENTAL_NATURAL,
                                                current.clefBottomDiatonic(),
                                                0,
                                                0,
                                                1,
                                                0,
                                                1)))
                        .get(1)
                        .tiedFromPrevious());
        assertFalse(
                ScoreBoundaryTies.resolve(List.of(earlier, current), List.of(), List.of(1))
                        .get(1)
                        .tiedFromPrevious());
    }

    public static void main(String[] args) throws Exception {
        // Actual record snapshots bind all 22 former fields plus Kind to resolver output bytes.
        var fixtures =
                List.of(
                        resolve(List.of(note(0, .8f, 4), note(1, .1f, 1))),
                        resolve(List.of(note(0, .8f, 8), note(1, .1f, 2))),
                        resolve(List.of(note(0, .8f, 4), note(1, .1f, 2))),
                        ScoreBoundaryTies.resolve(
                                List.of(note(0, .8f, 4), note(1, .1f, 1)), List.of(), List.of(1)));
        Files.write(Path.of(args[0]), fixtures.toString().getBytes(StandardCharsets.UTF_8));
    }
}
