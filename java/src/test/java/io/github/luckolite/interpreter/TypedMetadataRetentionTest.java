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

/** Reconstruction contracts only; no raster recognition or new tie decisions are asserted. */
public class TypedMetadataRetentionTest {
    static ScoreNoteEvent fixture(ScoreNoteEvent.Kind kind) {
        return new ScoreNoteEvent(
                        0,
                        .25f,
                        3,
                        0,
                        2,
                        .42f,
                        false,
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
                        -1)
                .withKind(kind);
    }

    static void assertFields(
            ScoreNoteEvent source, ScoreNoteEvent copy, Map<String, Object> changes)
            throws Exception {
        for (RecordComponent field : ScoreNoteEvent.class.getRecordComponents())
            assertEquals(
                    field.getName(),
                    changes.getOrDefault(field.getName(), field.getAccessor().invoke(source)),
                    field.getAccessor().invoke(copy));
    }

    @Test
    public void actualGraceHelperPreservesKindAndAllUnchangedFields() throws Exception {
        Method method =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "asEngravedGrace", ScoreNoteEvent.class, int.class);
        method.setAccessible(true);
        for (var kind : ScoreNoteEvent.Kind.values()) {
            var n = fixture(kind);
            var copy = (ScoreNoteEvent) method.invoke(null, n, 3);
            assertFields(
                    n,
                    copy,
                    Map.of(
                            "beamCount",
                            3,
                            "unbeamedDurationBeats",
                            0f,
                            "articulations",
                            n.articulations() | NoteOrnament.GRACE));
        }
    }

    @Test
    public void actualAccidentalStateCarriesOnlyAccidentalAndKeepsMetadata() throws Exception {
        Class<?> detected = null, component = null;
        for (Class<?> nested : OmrScoreInterpreter.class.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("DetectedNote")) detected = nested;
            if (nested.getSimpleName().equals("Component")) component = nested;
        }
        Constructor<?> ctor =
                detected.getDeclaredConstructor(ScoreNoteEvent.class, component, float.class);
        ctor.setAccessible(true);
        var target = fixture(ScoreNoteEvent.Kind.PITCHED);
        var prior =
                new ScoreNoteEvent(0, 0, target.staffStep(), 0, 2, .42f, false, 0, 0, 1, 1)
                        .withClef(target.clefBottomDiatonic());
        Method method =
                OmrScoreInterpreter.class.getDeclaredMethod("applyAccidentalState", List.class);
        method.setAccessible(true);
        var result =
                (List<?>)
                        method.invoke(
                                null,
                                List.of(
                                        ctor.newInstance(prior, null, 10f),
                                        ctor.newInstance(target, null, 10f)));
        Method event = detected.getDeclaredMethod("event");
        event.setAccessible(true);
        assertFields(
                target,
                (ScoreNoteEvent) event.invoke(result.get(1)),
                Map.of("writtenAccidental", 1));
        assertEquals(2, result.size());
    }
}
