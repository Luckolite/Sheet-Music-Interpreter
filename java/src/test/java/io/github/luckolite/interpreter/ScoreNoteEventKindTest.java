// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreNoteEventKindTest {
    private static final List<String> LEGACY_FIELDS =
            List.of(
                    "measureIndex",
                    "positionInMeasure",
                    "staffStep",
                    "staffIndex",
                    "staffCount",
                    "pageY",
                    "tiedFromPrevious",
                    "augmentationDots",
                    "beamCount",
                    "writtenAccidental",
                    "unbeamedDurationBeats",
                    "tupletDivisor",
                    "followingRestBeats",
                    "articulations",
                    "clefBottomDiatonic",
                    "crossStaffBeam",
                    "leadingRestBeats",
                    "compactOpening",
                    "octaveShift",
                    "boundaryTies",
                    "tupletNormalNotes",
                    "stemDirection");
    private static final Object[] LEGACY_VALUES = {
        3,
        .61f,
        8,
        1,
        2,
        .42f,
        true,
        2,
        2,
        ScoreNoteEvent.ACCIDENTAL_SHARP,
        1f,
        5,
        1.25f,
        NoteArticulation.ACCENT | NoteArticulation.TENUTO,
        ScoreNoteEvent.CLEF_TENOR,
        false,
        .75f,
        false,
        -1,
        9,
        3,
        -1
    };

    @Test
    public void keepsAll22ExistingComponentsAndTheirCompleteConstructor() throws Exception {
        var fields =
                Arrays.stream(ScoreNoteEvent.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList();
        assertEquals(23, fields.size());
        assertEquals(LEGACY_FIELDS, fields.subList(0, 22));
        assertEquals("kind", fields.get(22));
        ScoreNoteEvent legacy = (ScoreNoteEvent) constructor(22).newInstance(LEGACY_VALUES);
        assertEquals(ScoreNoteEvent.Kind.PITCHED, legacy.kind());
        assertEquals(30, legacy.diatonicPitchIdentity());
        Map<String, Object> values = values(legacy);
        for (int i = 0; i < 22; i++)
            assertEquals(LEGACY_FIELDS.get(i), LEGACY_VALUES[i], values.get(LEGACY_FIELDS.get(i)));
    }

    @Test
    public void everyLegacyConstructorDefaultsToPitched() throws Exception {
        Set<Integer> expected =
                Set.of(5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
        Set<Integer> observed =
                Arrays.stream(ScoreNoteEvent.class.getConstructors())
                        .filter(c -> c.getParameterCount() != 23)
                        .map(Constructor::getParameterCount)
                        .collect(Collectors.toSet());
        assertEquals("No existing constructor may disappear", expected, observed);
        for (int count : expected) {
            ScoreNoteEvent legacy =
                    (ScoreNoteEvent)
                            constructor(count).newInstance(Arrays.copyOf(LEGACY_VALUES, count));
            assertEquals(
                    "Constructor with " + count + " parameters",
                    ScoreNoteEvent.Kind.PITCHED,
                    legacy.kind());
        }
    }

    @Test
    public void everyExistingCopyPreservesKindAndEveryUnchangedField() throws Exception {
        var source = unpitched();
        Map<String, Object> before = values(source);
        assertCopy(source, source.withStemDirection(1), Map.of("stemDirection", 1));
        assertCopy(
                source,
                source.withTupletRatio(7, 4),
                Map.of("tupletDivisor", 7, "tupletNormalNotes", 4));
        assertCopy(source, source.withBoundaryTies(6), Map.of("boundaryTies", 6));
        assertCopy(source, source.withOctaveShift(2), Map.of("octaveShift", 2));
        assertCopy(source, source.withCompactOpening(), Map.of("compactOpening", true));
        assertCopy(source, source.withLeadingRest(2f), Map.of("leadingRestBeats", 2f));
        assertCopy(
                source,
                source.withCrossStaffBeam(),
                Map.of("crossStaffBeam", true, "unbeamedDurationBeats", 0f));
        assertCopy(
                source,
                source.withClef(ScoreNoteEvent.CLEF_BASS),
                Map.of("clefBottomDiatonic", 18));
        assertCopy(
                source,
                source.withArticulations(NoteArticulation.TENUTO),
                Map.of("articulations", NoteArticulation.TENUTO));
        assertCopy(
                source,
                source.withKind(ScoreNoteEvent.Kind.PITCHED),
                Map.of("kind", ScoreNoteEvent.Kind.PITCHED));
        assertEquals("Copies cannot mutate their source", before, values(source));
        Set<String> copies =
                Arrays.stream(ScoreNoteEvent.class.getDeclaredMethods())
                        .filter(
                                m ->
                                        m.getName().startsWith("with")
                                                && m.getReturnType() == ScoreNoteEvent.class)
                        .map(m -> m.getName())
                        .collect(Collectors.toSet());
        assertEquals(
                "Each copy method needs a retention control",
                Set.of(
                        "withStemDirection",
                        "withTupletRatio",
                        "withBoundaryTies",
                        "withOctaveShift",
                        "withCompactOpening",
                        "withLeadingRest",
                        "withCrossStaffBeam",
                        "withClef",
                        "withArticulations",
                        "withKind"),
                copies);
    }

    @Test
    public void everyExistingCopyAlsoKeepsPitchedClassification() throws Exception {
        var source = unpitched().withKind(ScoreNoteEvent.Kind.PITCHED);
        assertCopy(source, source.withStemDirection(1), Map.of("stemDirection", 1));
        assertCopy(
                source,
                source.withTupletRatio(7, 4),
                Map.of("tupletDivisor", 7, "tupletNormalNotes", 4));
        assertCopy(source, source.withBoundaryTies(6), Map.of("boundaryTies", 6));
        assertCopy(source, source.withOctaveShift(2), Map.of("octaveShift", 2));
        assertCopy(source, source.withCompactOpening(), Map.of("compactOpening", true));
        assertCopy(source, source.withLeadingRest(2f), Map.of("leadingRestBeats", 2f));
        assertCopy(
                source,
                source.withCrossStaffBeam(),
                Map.of("crossStaffBeam", true, "unbeamedDurationBeats", 0f));
        assertCopy(
                source,
                source.withClef(ScoreNoteEvent.CLEF_BASS),
                Map.of("clefBottomDiatonic", 18));
        assertCopy(
                source,
                source.withArticulations(NoteArticulation.TENUTO),
                Map.of("articulations", NoteArticulation.TENUTO));
    }

    @Test
    public void unpitchedGeometryCannotBorrowPitchFromAnyClefOrOctave() {
        var source = unpitched();
        for (int clef :
                List.of(
                        ScoreNoteEvent.CLEF_UNKNOWN,
                        ScoreNoteEvent.CLEF_BASS,
                        ScoreNoteEvent.CLEF_TENOR,
                        ScoreNoteEvent.CLEF_ALTO,
                        ScoreNoteEvent.CLEF_TREBLE,
                        ScoreNoteEvent.CLEF_TREBLE_OTTAVA)) {
            for (int octave : List.of(-2, -1, 0, 1, 2)) {
                var copy = source.withClef(clef).withOctaveShift(octave);
                assertThrows(IllegalStateException.class, copy::diatonicPitchIdentity);
            }
        }
        assertThrows(IllegalStateException.class, source::diatonicPitchIdentity);
        assertEquals(
                "Classification requires an explicit typed change",
                ScoreNoteEvent.Kind.UNPITCHED,
                source.kind());
    }

    @Test
    public void unpitchedRetainsWrittenRhythmAndOpticalGeometry() {
        var note = unpitched();
        assertEquals(3, note.measureIndex());
        assertEquals(.61f, note.positionInMeasure(), 0f);
        assertEquals(8, note.staffStep());
        assertEquals(.42f, note.pageY(), 0f);
        assertEquals(2, note.augmentationDots());
        assertEquals(2, note.beamCount());
        assertEquals(1f, note.unbeamedDurationBeats(), 0f);
        assertEquals(3d / 5, note.durationScale(), 0d);
        assertEquals(9, note.boundaryTies());
        assertEquals(-1, note.stemDirection());
    }

    @Test
    public void classificationParticipatesInRecordIdentityAndNeverDefaultsNull() {
        var unpitched = unpitched();
        var pitched = unpitched.withKind(ScoreNoteEvent.Kind.PITCHED);
        assertNotEquals(unpitched, pitched);
        assertEquals(2, Set.of(unpitched, pitched).size());
        assertThrows(IllegalArgumentException.class, () -> unpitched.withKind(null));
    }

    private static ScoreNoteEvent unpitched() {
        return new ScoreNoteEvent(
                3,
                .61f,
                8,
                1,
                2,
                .42f,
                true,
                2,
                2,
                ScoreNoteEvent.ACCIDENTAL_SHARP,
                1f,
                5,
                1.25f,
                NoteArticulation.ACCENT | NoteArticulation.TENUTO,
                ScoreNoteEvent.CLEF_TENOR,
                false,
                .75f,
                false,
                -1,
                9,
                3,
                -1,
                ScoreNoteEvent.Kind.UNPITCHED);
    }

    private static Constructor<?> constructor(int count) {
        return Arrays.stream(ScoreNoteEvent.class.getConstructors())
                .filter(c -> c.getParameterCount() == count)
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> values(ScoreNoteEvent note) throws Exception {
        var values = new LinkedHashMap<String, Object>();
        for (RecordComponent component : ScoreNoteEvent.class.getRecordComponents())
            values.put(component.getName(), component.getAccessor().invoke(note));
        return values;
    }

    private static void assertCopy(
            ScoreNoteEvent source, ScoreNoteEvent copy, Map<String, Object> changes)
            throws Exception {
        Map<String, Object> expected = values(source);
        expected.putAll(changes);
        assertEquals(expected, values(copy));
    }
}
