// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual private OMR passes, with original scalar head geometry and note evidence. */
public class TypedOmrStateTest {
    private Class<?> nested(String name) throws Exception {
        return Class.forName(OmrScoreInterpreter.class.getName() + "$" + name);
    }

    private Object head(int x, int y) throws Exception {
        var c =
                nested("Component")
                        .getDeclaredConstructor(
                                int.class,
                                int.class,
                                int.class,
                                int.class,
                                int.class,
                                float.class,
                                float.class);
        c.setAccessible(true);
        return c.newInstance(120, x - 10, x + 10, y - 7, y + 7, (float) x, (float) y);
    }

    private Object detected(ScoreNoteEvent n, int x) throws Exception {
        var c =
                nested("DetectedNote")
                        .getDeclaredConstructor(
                                ScoreNoteEvent.class, nested("Component"), float.class);
        c.setAccessible(true);
        return c.newInstance(n, head(x, 148), 16f);
    }

    private ScoreNoteEvent event(Object d) throws Exception {
        var m = nested("DetectedNote").getDeclaredMethod("event");
        m.setAccessible(true);
        return (ScoreNoteEvent) m.invoke(d);
    }

    @SuppressWarnings("unchecked")
    private List<Object> apply(String name, List<Object> source) throws Exception {
        var m = OmrScoreInterpreter.class.getDeclaredMethod(name, List.class);
        m.setAccessible(true);
        return (List<Object>) m.invoke(null, source);
    }

    private ScoreNoteEvent note(float x, int accidental) {
        return new ScoreNoteEvent(0, x, 2, 0, 1, .6f, false, 0, 0, accidental, 1).withClef(30);
    }

    @Test
    public void unpitchedHeadDoesNotBorrowOrPoisonAccidentalState() throws Exception {
        var sharp = note(.1f, ScoreNoteEvent.ACCIDENTAL_SHARP);
        var u =
                note(.3f, ScoreNoteEvent.ACCIDENTAL_FROM_KEY)
                        .withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var later = note(.5f, ScoreNoteEvent.ACCIDENTAL_FROM_KEY);
        var out =
                apply(
                        "applyAccidentalState",
                        List.of(detected(sharp, 80), detected(u, 160), detected(later, 240)));
        assertEquals(u, event(out.get(1)));
        assertEquals(ScoreNoteEvent.ACCIDENTAL_SHARP, event(out.get(2)).writtenAccidental());
        out =
                apply(
                        "applyAccidentalState",
                        List.of(detected(u.withArticulations(5), 80), detected(later, 240)));
        assertEquals(ScoreNoteEvent.ACCIDENTAL_FROM_KEY, event(out.get(1)).writtenAccidental());
    }

    @Test
    public void splitDedupRetainsMixedKindsAtSameWrittenAttack() throws Exception {
        var p = note(.3f, ScoreNoteEvent.ACCIDENTAL_FROM_KEY);
        var u = p.withKind(ScoreNoteEvent.Kind.UNPITCHED);
        assertEquals(
                2,
                apply("removeSplitDuplicates", List.of(detected(p, 160), detected(u, 160))).size());
        assertEquals(
                1,
                apply("removeSplitDuplicates", List.of(detected(p, 160), detected(p, 162))).size());
    }

    @Test
    public void unpitchedOnsetBlocksOlderOrdinaryPitchTie() throws Exception {
        var p = note(.1f, ScoreNoteEvent.ACCIDENTAL_FROM_KEY);
        var u =
                note(.3f, ScoreNoteEvent.ACCIDENTAL_FROM_KEY)
                        .withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var end = note(.5f, ScoreNoteEvent.ACCIDENTAL_FROM_KEY);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "previousSamePitch", List.class, int.class, int.class);
        m.setAccessible(true);
        assertEquals(
                -1,
                m.invoke(
                        null,
                        List.of(detected(p, 90), detected(u, 145), detected(end, 200)),
                        2,
                        420));
        assertEquals(0, m.invoke(null, List.of(detected(p, 90), detected(end, 200)), 1, 420));
        assertEquals(-1, m.invoke(null, List.of(detected(p, 90), detected(u, 145)), 1, 420));
    }
}
