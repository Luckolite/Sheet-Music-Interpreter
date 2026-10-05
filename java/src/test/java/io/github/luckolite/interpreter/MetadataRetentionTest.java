// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic events passed through the actual accidental and grace operations. */
public class MetadataRetentionTest {
    private static ScoreNoteEvent target(int measure, boolean tied) {
        return new ScoreNoteEvent(
                measure,
                .25f,
                3,
                0,
                2,
                .42f,
                tied,
                1,
                2,
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                .5f,
                5,
                .25f,
                NoteArticulation.TENUTO,
                ScoreNoteEvent.CLEF_BASS,
                true,
                .125f,
                true,
                2,
                5,
                4,
                -1);
    }

    private static ScoreNoteEvent sharp() {
        return new ScoreNoteEvent(0, 0, 3, 0, 2, .42f, false, 0, 0, 1, 1)
                .withClef(ScoreNoteEvent.CLEF_BASS);
    }

    private static void unchangedExcept(
            ScoreNoteEvent before, ScoreNoteEvent after, Map<String, Object> changes)
            throws Exception {
        assertEquals(23, ScoreNoteEvent.class.getRecordComponents().length);
        assertEquals("kind", ScoreNoteEvent.class.getRecordComponents()[22].getName());
        assertEquals(before.kind(), after.kind());
        for (RecordComponent field : ScoreNoteEvent.class.getRecordComponents())
            assertEquals(
                    field.getName(),
                    changes.getOrDefault(field.getName(), field.getAccessor().invoke(before)),
                    field.getAccessor().invoke(after));
    }

    private static List<ScoreNoteEvent> accidentalState(ScoreNoteEvent... events) throws Exception {
        Class<?> detected = null, component = null;
        for (Class<?> nested : OmrScoreInterpreter.class.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("DetectedNote")) detected = nested;
            if (nested.getSimpleName().equals("Component")) component = nested;
        }
        Constructor<?> ctor =
                detected.getDeclaredConstructor(ScoreNoteEvent.class, component, float.class);
        ctor.setAccessible(true);
        var input = new java.util.ArrayList<Object>();
        for (var event : events) input.add(ctor.newInstance(event, null, 10f));
        Method apply =
                OmrScoreInterpreter.class.getDeclaredMethod("applyAccidentalState", List.class);
        apply.setAccessible(true);
        var output = (List<?>) apply.invoke(null, input);
        Method event = detected.getDeclaredMethod("event");
        event.setAccessible(true);
        var result = new java.util.ArrayList<ScoreNoteEvent>();
        for (Object note : output) result.add((ScoreNoteEvent) event.invoke(note));
        return result;
    }

    @Test
    public void sameBarAccidentalCarryPreservesRhythmAndEngravingEvidence() throws Exception {
        var printed = sharp();
        var inferred = target(0, false);
        var result = accidentalState(printed, inferred);
        assertEquals(2, result.size());
        assertSame(printed, result.get(0));
        unchangedExcept(inferred, result.get(1), Map.of("writtenAccidental", 1));
    }

    @Test
    public void onlyTiedBoundaryCarriesAccidentalWithoutLosingMetadata() throws Exception {
        var tied = target(1, true);
        var untied = target(1, false);
        unchangedExcept(
                tied, accidentalState(sharp(), tied).get(1), Map.of("writtenAccidental", 1));
        var unchanged = accidentalState(sharp(), untied).get(1);
        assertSame(untied, unchanged);
        unchangedExcept(untied, unchanged, Map.of());
    }

    @Test
    public void engravedGraceChangesOnlyWrittenDurationBeamAndGraceArticulation() throws Exception {
        Method apply =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "asEngravedGrace", ScoreNoteEvent.class, int.class);
        apply.setAccessible(true);
        var before = target(0, false);
        var after = (ScoreNoteEvent) apply.invoke(null, before, 3);
        unchangedExcept(
                before,
                after,
                Map.of(
                        "beamCount",
                        3,
                        "unbeamedDurationBeats",
                        0f,
                        "articulations",
                        before.articulations() | NoteOrnament.GRACE));
    }
}
