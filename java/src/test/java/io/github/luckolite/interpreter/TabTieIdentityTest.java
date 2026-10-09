// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.Test;

/** Original scalar fixtures; sounding notes come from the actual tab decoder. */
public class TabTieIdentityTest {
    private static final int[] SIX = {64, 59, 55, 50, 45, 40};
    private static final int[] SEVEN = {64, 59, 55, 50, 45, 40, 35};

    @Test
    public void legacyDirectionValuesStillValidateInTheActualRecord() {
        var note = decoded(2, 6, 7, SIX, 0, 0, 0);
        for (int directions = 0; directions <= 15; directions++) {
            assertTrue(TabTieIdentity.valid(directions));
            assertFalse(TabTieIdentity.typed(directions));
            assertEquals(directions, note.withBoundaryTies(directions).boundaryTies());
        }
    }

    @Test
    public void typedIdentityAcceptsTheDocumentedInclusiveBounds() {
        for (int flags :
                new int[] {
                    TabTieIdentity.encode(1, 0, 6, 0, 0),
                    TabTieIdentity.encode(15, 6, 7, 36, 91),
                    TabTieIdentity.encode(8, 5, 6, 0, 127)
                }) {
            assertTrue(TabTieIdentity.valid(flags));
            assertTrue(TabTieIdentity.typed(flags));
            assertEquals(
                    flags, decoded(2, 6, 7, SIX, 0, 0, 0).withBoundaryTies(flags).boundaryTies());
        }
    }

    @Test
    public void encodeRejectsInvalidDirectionsCountsStringsFretsAndOpenPitches() {
        int[][] invalid = {
            {0, 0, 6, 0, 0},
            {16, 0, 6, 0, 0},
            {-1, 0, 6, 0, 0},
            {1, -1, 6, 0, 0},
            {1, 6, 6, 0, 0},
            {1, 7, 7, 0, 0},
            {1, 0, 5, 0, 0},
            {1, 0, 8, 0, 0},
            {1, 0, 6, -1, 0},
            {1, 0, 6, 37, 0},
            {1, 0, 6, 0, -1},
            {1, 0, 6, 0, 128},
            {1, 0, 6, 36, 92}
        };
        for (int[] value : invalid)
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TabTieIdentity.encode(value[0], value[1], value[2], value[3], value[4]));
    }

    @Test
    public void encodeRejectsOpenPitchAdditionOverflow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TabTieIdentity.encode(1, 0, 6, 1, Integer.MAX_VALUE));
        assertThrows(
                IllegalArgumentException.class,
                () -> TabTieIdentity.encode(1, 0, 6, 36, Integer.MAX_VALUE - 1));
    }

    @Test
    public void rawMetadataRequiresTagDirectionAndValidTypedFields() {
        int tag = TabTieIdentity.TAG;
        int[] invalid = {
            1 | 1 << 5,
            1 | 1 << 9,
            1 | 1 << 15,
            tag,
            tag | 2 << 5 | 7 << 9 | 55 << 15,
            tag | 1 | 6 << 5,
            tag | 1 | 7 << 5 | 1 << 8,
            tag | 1 | 37 << 9,
            tag | 1 | 63 << 9,
            tag | 1 | 36 << 9 | 100 << 15,
            1 << 22,
            -1,
            Integer.MIN_VALUE
        };
        var note = decoded(2, 6, 7, SIX, 0, 0, 0);
        for (int flags : invalid) {
            assertFalse("Malformed flags " + flags, TabTieIdentity.valid(flags));
            assertFalse(TabTieIdentity.compatible(flags, 1));
            assertFalse(TabTieIdentity.compatible(4, flags));
            assertThrows(IllegalArgumentException.class, () -> note.withBoundaryTies(flags));
        }
    }

    @Test
    public void matchingTypedIdentityResolvesAndPreservesEveryOtherField() throws Exception {
        var before = typed(2, 6, 7, SIX, 0, 0, 4);
        var after = typed(2, 6, 7, SIX, 0, 1, 1);
        var inputs = List.of(before, after);
        var result = resolve(inputs);
        assertTrue(result.get(1).tiedFromPrevious());
        assertSame(before, result.get(0));
        assertFalse(after.tiedFromPrevious());
        assertEquals(2, result.size());
        for (RecordComponent field : ScoreNoteEvent.class.getRecordComponents())
            if (!field.getName().equals("tiedFromPrevious"))
                assertEquals(
                        field.getName(),
                        field.getAccessor().invoke(after),
                        field.getAccessor().invoke(result.get(1)));
        assertEquals(result, resolve(result));
    }

    @Test
    public void sameSoundingPitchOnAnotherStringRemainsANewAttack() {
        int[] changed = SIX.clone();
        changed[3] = 55;
        assertIndependent(typed(2, 6, 7, SIX, 0, 0, 4), typed(3, 6, 7, changed, 0, 1, 1));
    }

    @Test
    public void changedFretCompensatedByTuningRemainsANewAttack() {
        int[] changed = SIX.clone();
        changed[2] = 57;
        assertIndependent(typed(2, 6, 7, SIX, 0, 0, 4), typed(2, 6, 5, changed, 0, 1, 1));
    }

    @Test
    public void changedFretCompensatedByCapoRemainsANewAttack() {
        assertIndependent(typed(2, 6, 7, SIX, 0, 0, 4), typed(2, 6, 5, SIX, 2, 1, 1));
    }

    @Test
    public void SixAndSevenStringSourcesDoNotShareIdentity() {
        assertIndependent(typed(2, 6, 7, SIX, 0, 0, 4), typed(2, 7, 7, SEVEN, 0, 1, 1));
    }

    @Test
    public void legacyOutgoingCannotProveTypedIncomingOwnership() {
        assertIndependent(decoded(2, 6, 7, SIX, 0, 0, 4), typed(2, 6, 7, SIX, 0, 1, 1));
    }

    @Test
    public void typedOutgoingCannotProveLegacyIncomingOwnership() {
        assertIndependent(typed(2, 6, 7, SIX, 0, 0, 4), decoded(2, 6, 7, SIX, 0, 1, 1));
    }

    @Test
    public void matchingLegacyDirectionsStillResolveWithoutTypedMetadata() {
        var result =
                resolve(List.of(decoded(2, 6, 7, SIX, 0, 0, 4), decoded(2, 6, 7, SIX, 0, 1, 1)));
        assertTrue(result.get(1).tiedFromPrevious());
        assertEquals(1, result.get(1).boundaryTies());
    }

    @Test
    public void compatibleIdentityDoesNotWaiveDirectionOrPageBoundary() {
        var before = typed(2, 6, 7, SIX, 0, 0, 4);
        var opposite = typed(2, 6, 7, SIX, 0, 1, 2);
        assertTrue(TabTieIdentity.compatible(before.boundaryTies(), opposite.boundaryTies()));
        assertFalse(resolve(List.of(before, opposite)).get(1).tiedFromPrevious());
        var after = typed(2, 6, 7, SIX, 0, 1, 1);
        assertFalse(
                ScoreBoundaryTies.resolve(
                                List.of(before, after),
                                List.of(new ScoreKeyChange(0, 0)),
                                List.of())
                        .get(1)
                        .tiedFromPrevious());
    }

    @Test
    public void matchingMetadataDoesNotWaiveDecodedSoundingPitch() {
        var before = typed(2, 6, 7, SIX, 0, 0, 4);
        var changedPitch = decoded(2, 6, 8, SIX, 0, 1, TabTieIdentity.encode(1, 2, 6, 7, 55));
        assertNotEquals(midi(before), midi(changedPitch));
        assertFalse(resolve(List.of(before, changedPitch)).get(1).tiedFromPrevious());
    }

    @Test
    public void typedIncomingAndOutgoingUnpitchedEvidenceAreRejected() {
        var before = typed(2, 6, 7, SIX, 0, 0, 4);
        var after = typed(2, 6, 7, SIX, 0, 1, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> resolve(List.of(before.withKind(ScoreNoteEvent.Kind.UNPITCHED), after)));
        assertThrows(
                IllegalArgumentException.class,
                () -> resolve(List.of(before, after.withKind(ScoreNoteEvent.Kind.UNPITCHED))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ScoreBoundaryTies.resolve(
                                List.of(after.withKind(ScoreNoteEvent.Kind.UNPITCHED)),
                                List.of(),
                                List.of()));
    }

    private static ScoreNoteEvent typed(
            int string, int count, int fret, int[] tuning, int capo, int measure, int directions) {
        return decoded(
                string,
                count,
                fret,
                tuning,
                capo,
                measure,
                TabTieIdentity.encode(directions, string, count, fret, tuning[string] + capo));
    }

    private static ScoreNoteEvent decoded(
            int string, int count, int fret, int[] tuning, int capo, int measure, int flags) {
        var staff =
                new TablatureDecoder.Staff(
                        80,
                        20,
                        -1,
                        List.of(
                                new TablatureDecoder.Fret(
                                        100, 80 + string * 20, string, fret, 4, 0, 0, 0, false, 1)),
                        List.of(20f, 180f),
                        count,
                        List.of());
        var score =
                TablatureDecoder.apply(
                        new ScorePageInterpretation(List.of(), List.of()),
                        List.of(staff),
                        240,
                        280,
                        tuning,
                        capo);
        assertEquals(1, score.notes().size());
        var n = score.notes().get(0);
        // Relocate the decoded event into adjacent assembled-page measures;
        // pitch, rhythm and performance fields still come from the decoder.
        return new ScoreNoteEvent(
                measure,
                measure == 0 ? .8f : .1f,
                n.staffStep(),
                n.staffIndex(),
                n.staffCount(),
                n.pageY(),
                n.tiedFromPrevious(),
                n.augmentationDots(),
                n.beamCount(),
                n.writtenAccidental(),
                n.unbeamedDurationBeats(),
                n.tupletDivisor(),
                n.followingRestBeats(),
                n.articulations(),
                n.clefBottomDiatonic(),
                n.crossStaffBeam(),
                n.leadingRestBeats(),
                n.compactOpening(),
                n.octaveShift(),
                flags,
                n.tupletNormalNotes(),
                n.stemDirection(),
                n.kind());
    }

    private static List<ScoreNoteEvent> resolve(List<ScoreNoteEvent> notes) {
        return ScoreBoundaryTies.resolve(notes, List.of(new ScoreKeyChange(0, 0)), List.of(1));
    }

    private static void assertIndependent(ScoreNoteEvent before, ScoreNoteEvent after) {
        assertEquals(
                "The negative fixture must have the same sounding MIDI pitch",
                midi(before),
                midi(after));
        assertEquals(before.diatonicPitchIdentity(), after.diatonicPitchIdentity());
        assertEquals(before.writtenAccidental(), after.writtenAccidental());
        assertFalse(TabTieIdentity.compatible(before.boundaryTies(), after.boundaryTies()));
        var result = resolve(List.of(before, after));
        assertFalse(result.get(1).tiedFromPrevious());
        assertSame(before, result.get(0));
        assertSame(after, result.get(1));
    }

    private static int midi(ScoreNoteEvent note) {
        int pitch = note.diatonicPitchIdentity();
        return 12 * (Math.floorDiv(pitch, 7) + 1 + note.octaveShift())
                + new int[] {0, 2, 4, 5, 7, 9, 11}[Math.floorMod(pitch, 7)]
                + ScoreNoteEvent.accidentalSemitones(note.writtenAccidental());
    }
}
